package com.rootilabs.wmeCardiac.ui.login

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootilabs.wmeCardiac.di.ServiceLocator
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val loginSuccess: Boolean = false,
    val statusMessage: String = "",
    val error: String? = null,
    val showScanner: Boolean = false
)

enum class ServerRegion(val label: String, val url: String) {
    AP("Asia-Pacific 1", "http://192.168.103.17:8080/"),
    AP2("Asia-Pacific 2", "https://mct2-api.rooticare.com/"),
    EU("Europe", "https://mcteu-api.rooticare.com/")
}

class LoginViewModel : ViewModel() {
    companion object {
        private const val TAG = "LoginViewModel"
    }

    private val repository get() = ServiceLocator.repository

    var uiState by mutableStateOf(LoginUiState())
        private set

    var institutionId by mutableStateOf("")
    var patientId by mutableStateOf("")
    var selectedServer by mutableStateOf(ServerRegion.AP)

    fun login() {
        if (institutionId.isBlank() || patientId.isBlank()) {
            uiState = uiState.copy(error = "FIELDS_REQUIRED")
            return
        }
        viewModelScope.launch {
            // Switch server before login
            ServiceLocator.reinitWithBaseUrl(selectedServer.url)
            performLogin()
        }
    }

    private suspend fun performLogin() {
        try {
            // Normalize inputs to lowercase to avoid case-sensitivity issues with the server
            institutionId = institutionId.trim().lowercase()
            patientId = patientId.trim().lowercase()

            uiState = uiState.copy(isLoading = true, error = null, statusMessage = "STATUS_TOKEN")
            Log.d(TAG, "=== Login Start === institutionId=$institutionId, patientId=$patientId")

            // CRITICAL: Cancel any pending background logout workers that might have been
            // enqueued by a previous failed attempt. This prevents "fake logout" after success.
            try {
                androidx.work.WorkManager.getInstance(ServiceLocator.appContext)
                    .cancelAllWorkByTag("LogoutWorker") // or cancel unique if we used it
                // Since we used OneTimeWorkRequestBuilder without a tag, let's use the Class name if possible,
                // but simpler is to just cancel by tag if we had one.
                // For now, let's cancel all to be safe, or just rely on the new revokeOldSession logic.
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel work: ${e.message}")
            }

            // Step 1: Get Token
            val tokenResult = repository.getToken()
            Log.d(TAG, "Step 1 getToken: success=${tokenResult.isSuccess}")
            if (tokenResult.isFailure) {
                Log.e(TAG, "Token failed: ${tokenResult.exceptionOrNull()?.message}")
                uiState = uiState.copy(isLoading = false, error = "GET_TOKEN_FAILED")
                return
            }

            // Step 2: Auth Patient
            uiState = uiState.copy(statusMessage = "STATUS_AUTH")
            var authResult = repository.authPatient(institutionId, patientId)
            Log.d(TAG, "Step 2 authPatient: success=${authResult.isSuccess}")

            // 409 = some device/session is already subscribed
            // Strict Policy: Only allow auto-revoke if we are SURE it's the same session on this device.
            if (authResult.isFailure && authResult.exceptionOrNull()?.message == "ALREADY_SUBSCRIBED") {
                val tokenManager = ServiceLocator.tokenManager
                val localMeasureId = tokenManager.measureRecordId
                val lastLoggedOutId = tokenManager.lastLoggedOutMeasureId
                
                val measureCheck = repository.getCurrentMeasurement(institutionId, patientId)
                val measureInfo = measureCheck.getOrNull()
                val serverMeasureId = measureInfo?.measureRecordId
                
                Log.w(TAG, "409 check: localMeasureId=$localMeasureId, lastLoggedOutId=$lastLoggedOutId, serverMeasureId=$serverMeasureId")

                // Strict check: Only same session ID (current or recently logged out) allows auto-repair. 
                // This prevents multi-phone bypass while allowing repair if a previous same-phone logout failed to sync to server.
                val isStaleSessionOnSameDevice = (localMeasureId != null && localMeasureId == serverMeasureId) ||
                                                (lastLoggedOutId != null && lastLoggedOutId == serverMeasureId)

                if (isStaleSessionOnSameDevice) {
                    Log.w(TAG, "Stale or recently logged-out session on same device -> repairing")
                    repository.revokeOldSession(institutionId, patientId)
                    authResult = repository.authPatient(institutionId, patientId)
                    Log.d(TAG, "Step 2 retry authPatient: success=${authResult.isSuccess}")
                } else {
                    Log.w(TAG, "Conflicting session or new install -> blocking login per strict 409 policy")
                    uiState = uiState.copy(isLoading = false, error = "ALREADY_SUBSCRIBED")
                    return
                }
            }

            if (authResult.isFailure) {
                val cause = authResult.exceptionOrNull()?.message ?: ""
                Log.e(TAG, "authPatient failed: $cause")
                uiState = uiState.copy(isLoading = false, error = cause)
                return
            }

            // Step 3: Get Current Measurement
            // IMPORTANT: capture oldMeasureId BEFORE the API call, because
            // getCurrentMeasurement() will overwrite tokenManager.measureRecordId with the server value.
            val oldMeasureId = ServiceLocator.tokenManager.measureRecordId

            uiState = uiState.copy(statusMessage = "STATUS_MEASUREMENT")
            val measureResult = repository.getCurrentMeasurement(institutionId, patientId)
            Log.d(TAG, "Step 3 getCurrentMeasurement: success=${measureResult.isSuccess}")

            if (measureResult.isFailure) {
                Log.e(TAG, "Measurement failed: ${measureResult.exceptionOrNull()?.message}")
                uiState = uiState.copy(isLoading = false, error = "MEASUREMENT_FAILED")
                return
            }

            val measurementInfo = measureResult.getOrNull()
            if (measurementInfo == null) {
                Log.w(TAG, "No measurement info found (null)")
                uiState = uiState.copy(isLoading = false, error = "NOT_MEASURING")
                return
            }

            // Validation 1: isMeasuring?
            val isMeasuring = measurementInfo.isMeasuring()
            val now = System.currentTimeMillis()
            Log.d(TAG, "Measurement Check: state=${measurementInfo.state}, expectedEndTime=${measurementInfo.expectedEndTime}, now=$now")

            ServiceLocator.tokenManager.isMeasuring = isMeasuring
            measurementInfo.deviceId?.let {
                ServiceLocator.tokenManager.serverDeviceId = it
                Log.d(TAG, "Server hardware deviceId (Rx) saved: $it")
            }

            if (!isMeasuring) {
                Log.w(TAG, "Login: Server says NOT_MEASURING (state=${measurementInfo.state}), but allowing login to proceed.")
            }

            if (!measurementInfo.isVirtualTagMode()) {
                Log.w(TAG, "Login: Server says mode is NOT VirtualTag (mode=${measurementInfo.mode}), but allowing login to proceed.")
            }

            // Step 4: Check measureRecordId - same session or new session?
            val newMeasureId = measurementInfo.measureRecordId
            
            // Explicitly sync the ID only AFTER we are past the 409 check and proceeding with login
            ServiceLocator.tokenManager.measureRecordId = newMeasureId
            
            Log.d(TAG, "Step 4 measureRecordId check: old=$oldMeasureId, new=$newMeasureId")

            if (oldMeasureId != null && newMeasureId != null && oldMeasureId != newMeasureId) {
                // Different measureRecordId → different measurement session
                Log.w(TAG, "measureRecordId changed ($oldMeasureId → $newMeasureId), clearing old local tags")
                repository.clearLocalEventTags()
            } else if (oldMeasureId == newMeasureId) {
                Log.d(TAG, "Same measureRecordId, keeping local data")
            }

            // Step 5: Check Total History Count (API #4) & Fetch History (API #5)
            uiState = uiState.copy(statusMessage = "STATUS_SYNCING")
            val measureId = ServiceLocator.tokenManager.measureRecordId
            Log.d(TAG, "Step 5 measureId=$measureId")
            if (measureId != null) {
                val countResult = repository.getTotalHistoryCount(institutionId, patientId, measureId)
                val totalRow = countResult.getOrNull() ?: 0
                Log.d(TAG, "Step 5a totalHistoryCount: totalRow=$totalRow")

                if (totalRow > 0) {
                    uiState = uiState.copy(statusMessage = "STATUS_DOWNLOADING:$totalRow")
                    val fetchResult = repository.fetchAllEventTagHistory(institutionId, patientId, measureId)
                    Log.d(TAG, "Step 5b fetchHistory: saved=${fetchResult.getOrNull()} records")
                }
            }

            // Successful Login
            ServiceLocator.tokenManager.lastLoggedOutMeasureId = null
            Log.d(TAG, "=== Login Success === measureRecordId=${ServiceLocator.tokenManager.measureRecordId}")
            uiState = uiState.copy(isLoading = false, loginSuccess = true)
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL CRASH during login flow", e)
            uiState = uiState.copy(isLoading = false, error = "FATAL_ERROR")
        }
    }

    fun onScanClicked() {
        uiState = uiState.copy(showScanner = true)
    }

    fun onBarcodeScanned(barcode: String) {
        // Support multiple delimiters: comma, semicolon, pipe, space
        val delimiter = Regex("[,;| ]")
        val parts = barcode.split(delimiter).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            // Format: institutionId + delimiter + patientId
            institutionId = parts[0].lowercase()
            patientId = parts[1].lowercase()
        } else {
            // Single value → fill ID Number (patientId) only
            // institutionId is expected to be filled manually or pre-set
            patientId = barcode.trim().lowercase()
        }
        uiState = uiState.copy(showScanner = false)
    }

    fun onScannerDismissed() {
        uiState = uiState.copy(showScanner = false)
    }
}
