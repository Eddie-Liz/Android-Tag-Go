package com.rootilabs.wmeCardiac.ui.login

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootilabs.wmeCardiac.di.ServiceLocator
import com.rootilabs.wmeCardiac.data.model.MeasurementInfo
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val loginSuccess: Boolean = false,
    val statusMessage: String = "",
    val error: String? = null,
    val showScanner: Boolean = false,
    val showDeviceSelection: Boolean = false,
    val measurements: List<MeasurementInfo> = emptyList(),
    val showDuplicateWarning: Boolean = false,
    val selectedDeviceId: String? = null,
    val selectedMeasureRecordId: String? = null,
    val selectedDeviceIsLoggedIn: Boolean = false,
    val showDeviceSheet: Boolean = false,
    val showServerSheet: Boolean = false,
    val transientErrorMessage: String? = null
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
            // If we already have a selected device (and it's not logged in), 
            // perform the second phase of login.
            val selectedRecordId = uiState.selectedMeasureRecordId
            if (selectedRecordId != null) {
                if (uiState.selectedDeviceIsLoggedIn) {
                    uiState = uiState.copy(error = "ALREADY_LOGGED_IN")
                    return@launch
                }
                startPhase2(selectedRecordId)
                return@launch
            }

            // Normal flow: Switch server before login and save it
            ServiceLocator.reinitWithBaseUrl(selectedServer.url)
            ServiceLocator.tokenManager.serverUrl = selectedServer.url
            performLogin()
        }
    }

    private suspend fun performLogin() {
        try {
            institutionId = institutionId.trim()
            patientId = patientId.trim()

            uiState = uiState.copy(isLoading = true, error = null)
            Log.d(TAG, "=== Login Phase 1 === institutionId=$institutionId, patientId=$patientId")

            // Step 1: Get Token
            val tokenResult = repository.getToken()
            if (tokenResult.isFailure) {
                uiState = uiState.copy(isLoading = false, error = "GET_TOKEN_FAILED")
                return
            }

            // Step 2: Get Recording Measurements
            uiState = uiState.copy(statusMessage = "STATUS_AUTH")
            val measurementsResult = repository.getRecordingMeasurements(institutionId, patientId)
            
            if (measurementsResult.isFailure) {
                uiState = uiState.copy(isLoading = false, error = measurementsResult.exceptionOrNull()?.message ?: "GET_MEASUREMENTS_FAILED")
                return
            }

            val measurements = measurementsResult.getOrNull() ?: emptyList()
            if (measurements.isEmpty()) {
                Log.w(TAG, "No recording measurements found")
                uiState = uiState.copy(isLoading = false, error = "NO_DEVICE_RECORDING")
                return
            }

            Log.d(TAG, "Found ${measurements.size} measurements")
            
            if (measurements.size == 1) {
                val singleInfo = measurements[0]
                val recordId = singleInfo.measureRecordId
                if (recordId != null) {
                    uiState = uiState.copy(
                        selectedDeviceId = singleInfo.deviceId,
                        selectedMeasureRecordId = recordId,
                        selectedDeviceIsLoggedIn = singleInfo.isPatientSubscribed == true,
                        measurements = measurements,
                        isLoading = false,
                        error = if (singleInfo.isPatientSubscribed == true) "ALREADY_LOGGED_IN" else null
                    )
                    
                    if (singleInfo.isPatientSubscribed != true) {
                        startPhase2(recordId)
                    }
                    return
                }
            }

            // Phase 1 Success: Show selection UI
            uiState = uiState.copy(
                isLoading = false,
                measurements = measurements,
                showDuplicateWarning = measurements.size >= 2,
                showDeviceSheet = true // Show the iOS-style picker
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error in login phase 1", e)
            uiState = uiState.copy(isLoading = false, error = "FATAL_ERROR")
        }
    }

    fun onMeasurementSelected(info: MeasurementInfo) {
        val measureId = info.measureRecordId ?: return
        
        uiState = uiState.copy(
            selectedDeviceId = info.deviceId,
            selectedMeasureRecordId = measureId,
            selectedDeviceIsLoggedIn = info.isPatientSubscribed == true,
            showDeviceSheet = false,
            showDeviceSelection = false,
            error = null
        )

        if (info.isPatientSubscribed == true) {
            uiState = uiState.copy(error = "ALREADY_LOGGED_IN")
        }
        // Removed startPhase2(info) call - user must click Login button again
    }

    private fun startPhase2(measureId: String) {
        uiState = uiState.copy(isLoading = true, statusMessage = "STATUS_AUTH")
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== Login Phase 2 === measureId=$measureId")
                
                // Step 3: Auth Patient
                var authResult = repository.authPatient(institutionId, patientId, measureId)
                
                // 409 Repair Logic
                if (authResult.isFailure && authResult.exceptionOrNull()?.message == "ALREADY_SUBSCRIBED") {
                    val tokenManager = ServiceLocator.tokenManager
                    val lastLoggedOutId = tokenManager.lastLoggedOutMeasureId
                    
                    // Repair only if same session that was recently logged out
                    if (tokenManager.offlineLogoutPending && lastLoggedOutId == measureId) {
                        Log.w(TAG, "Repairing stale session $measureId")
                        repository.revokeOldSession(institutionId, patientId, measureId)
                        authResult = repository.authPatient(institutionId, patientId, measureId)
                    } else {
                        uiState = uiState.copy(isLoading = false, error = "ALREADY_SUBSCRIBED")
                        return@launch
                    }
                }

                if (authResult.isFailure) {
                    uiState = uiState.copy(isLoading = false, error = authResult.exceptionOrNull()?.message ?: "AUTH_FAILED")
                    return@launch
                }

                // Step 4: Get Current Measurement Info
                uiState = uiState.copy(statusMessage = "STATUS_MEASUREMENT")
                val measureResult = repository.getCurrentMeasurement(institutionId, patientId, measureId)
                val measurementInfo = measureResult.getOrNull()
                
                if (measurementInfo == null || !measurementInfo.isMeasuring()) {
                    Log.w(TAG, "Device not measuring or not found. Cleaning up subscription.")
                    repository.unsubscribePatient(institutionId, patientId, measureId)
                    uiState = uiState.copy(isLoading = false, error = "NOT_MEASURING")
                    return@launch
                }

                if (!measurementInfo.isVirtualTagMode()) {
                    Log.w(TAG, "Unsupported mode: ${measurementInfo.mode}. Cleaning up subscription.")
                    repository.unsubscribePatient(institutionId, patientId, measureId)
                    uiState = uiState.copy(isLoading = false, error = "UNSUPPORTED_MODE")
                    return@launch
                }

                // Update TokenManager
                val tokenManager = ServiceLocator.tokenManager
                tokenManager.isMeasuring = true
                if (measurementInfo.deviceId != null) {
                    tokenManager.serverDeviceId = measurementInfo.deviceId
                }

                // Lock measureRecordId
                repository.clearLocalEventTags()
                if (tokenManager.measureRecordId == null) {
                    tokenManager.measureRecordId = measureId
                }

                // Step 5: Sync History
                uiState = uiState.copy(statusMessage = "STATUS_SYNCING")
                val countResult = repository.getTotalHistoryCount(institutionId, patientId, measureId)
                val totalRow = countResult.getOrNull() ?: 0
                
                if (totalRow > 0) {
                    uiState = uiState.copy(statusMessage = "STATUS_DOWNLOADING:$totalRow")
                    repository.fetchAllEventTagHistory(institutionId, patientId, measureId)
                }

                // Finish
                tokenManager.institutionId = institutionId
                tokenManager.patientId = patientId
                tokenManager.lastLoggedOutMeasureId = null
                
                uiState = uiState.copy(isLoading = false, loginSuccess = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error in login phase 2", e)
                uiState = uiState.copy(isLoading = false, error = "FATAL_ERROR")
            }
        }
    }

    fun onDismissDeviceSelection() {
        uiState = uiState.copy(showDeviceSelection = false, showDuplicateWarning = false, showDeviceSheet = false)
    }

    fun onDismissDuplicateWarning() {
        uiState = uiState.copy(showDuplicateWarning = false)
    }
    
    fun onDismissDeviceSheet() {
        uiState = uiState.copy(showDeviceSheet = false)
    }

    fun onShowDeviceSheet() {
        if (uiState.measurements.isNotEmpty()) {
            uiState = uiState.copy(showDeviceSheet = true)
        }
    }

    fun onShowServerSheet() {
        uiState = uiState.copy(showServerSheet = true)
    }

    fun onDismissServerSheet() {
        uiState = uiState.copy(showServerSheet = false)
    }

    fun onServerSelected(region: ServerRegion) {
        selectedServer = region
        uiState = uiState.copy(showServerSheet = false)
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
            // institutionId is expected to be filled manually or pre-set
            patientId = barcode.trim()
        }
        uiState = uiState.copy(showScanner = false)
    }

    fun onScannerDismissed() {
        uiState = uiState.copy(showScanner = false)
    }
}
