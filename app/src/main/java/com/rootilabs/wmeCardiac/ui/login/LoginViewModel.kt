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
    TEST("test", "https://mct-api.rooticare.com/"),
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
            uiState = uiState.copy(isLoading = true, error = null, statusMessage = "STATUS_TOKEN")
            Log.d(TAG, "=== Login Start === institutionId=$institutionId, patientId=$patientId")

            // Step 1: Get Token
            val tokenResult = repository.getToken()
            Log.d(TAG, "Step 1 getToken: success=${tokenResult.isSuccess}")
            if (tokenResult.isFailure) {
                Log.e(TAG, "Token failed: ${tokenResult.exceptionOrNull()?.message}")
                uiState = uiState.copy(isLoading = false, error = "TOKEN_FAILED")
                return
            }

            // Step 2: Auth Patient
            uiState = uiState.copy(statusMessage = "STATUS_AUTH")
            val authResult = repository.authPatient(institutionId, patientId)
            Log.d(TAG, "Step 2 authPatient: success=${authResult.isSuccess}")
            if (authResult.isFailure) {
                val cause = authResult.exceptionOrNull()?.message ?: ""
                Log.e(TAG, "authPatient failed: $cause")
                uiState = uiState.copy(isLoading = false, error = cause)
                return
            }

            // Step 3: Get Current Measurement
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
                ServiceLocator.tokenManager.deviceId = it
                Log.d(TAG, "Server deviceId saved: $it")
            }

            if (!isMeasuring) {
                Log.w(TAG, "Not measuring: state=${measurementInfo.state}")
                repository.unsubscribePatient()
                uiState = uiState.copy(isLoading = false, error = "NOT_MEASURING")
                return
            }

            // Validation 2: isVirtualTagMode?
            if (!measurementInfo.isVirtualTagMode()) {
                Log.w(TAG, "Unsupported mode: mode=${measurementInfo.mode}")
                repository.unsubscribePatient()
                uiState = uiState.copy(isLoading = false, error = "UNSUPPORTED_MODE")
                return
            }

            // Step 4: Check measureRecordId - same session or new session?
            val oldMeasureId = ServiceLocator.tokenManager.measureRecordId
            val newMeasureId = measurementInfo.measureRecordId
            Log.d(TAG, "Step 4 measureRecordId check: old=$oldMeasureId, new=$newMeasureId")

            if (oldMeasureId != null && newMeasureId != null && oldMeasureId != newMeasureId) {
                // Different measureRecordId → different measurement session (possibly same patient name but different record)
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
            institutionId = parts[0]
            patientId = parts[1]
        } else {
            // Single value → fill ID Number (patientId) only
            patientId = barcode.trim()
        }
        uiState = uiState.copy(showScanner = false)
    }

    fun onScannerDismissed() {
        uiState = uiState.copy(showScanner = false)
    }
}
