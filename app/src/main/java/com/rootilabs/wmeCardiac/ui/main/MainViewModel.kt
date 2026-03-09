package com.rootilabs.wmeCardiac.ui.main

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootilabs.wmeCardiac.data.local.EventTagDbEntity
import com.rootilabs.wmeCardiac.di.ServiceLocator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.rootilabs.wmeCardiac.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

// Icon Source Wrapper
sealed class IconSource {
    data class Vector(val imageVector: ImageVector) : IconSource()
    data class Resource(val resId: Int) : IconSource()
}

// 症狀類型
data class SymptomItem(
    val id: Int,
    val labelResId: Int, // Changed from String to Int for resource ID
    val icon: IconSource
)

val symptoms = listOf(
    SymptomItem(1, R.string.symptom_chest_pain, IconSource.Resource(R.drawable.icon_chestpain)),
    SymptomItem(2, R.string.symptom_dizziness, IconSource.Resource(R.drawable.icon_dizzy)),
    SymptomItem(7, R.string.symptom_palpitations, IconSource.Resource(R.drawable.icon_palpitation)),  // iOS 使用 ID 7
    SymptomItem(4, R.string.symptom_fatigue, IconSource.Resource(R.drawable.icon_tired)),
    SymptomItem(5, R.string.symptom_rapid_heartbeat, IconSource.Resource(R.drawable.icon_tachycardia)),
    SymptomItem(9, R.string.symptom_shortness_of_breath, IconSource.Resource(R.drawable.icon_gasp))   // iOS 使用 ID 9
)

// 運動強度
data class ExerciseItem(
    val id: Int,
    val labelResId: Int, // Changed from String to Int for resource ID
    val descResId: Int,  // Changed from String to Int for resource ID
    val icon: IconSource
)

val exercises = listOf(
    ExerciseItem(3, R.string.intensity_high, R.string.intensity_high_desc, IconSource.Resource(R.drawable.icon_hi)),
    ExerciseItem(2, R.string.intensity_medium, R.string.intensity_medium_desc, IconSource.Resource(R.drawable.icon_mid)),
    ExerciseItem(1, R.string.intensity_low, R.string.intensity_low_desc, IconSource.Resource(R.drawable.icon_low)),
    ExerciseItem(0, R.string.intensity_resting, R.string.intensity_resting_desc, IconSource.Resource(R.drawable.icon_idle))
)

enum class TagFlowStep {
    IDLE, SYMPTOM_SELECTION, EXERCISE_SELECTION, CONFIRMATION
}

data class MainUiState(
    val isLoading: Boolean = false,
    val isMeasuring: Boolean = false,
    val eventTags: List<EventTagDbEntity> = emptyList(),
    val lastTagTime: String? = null,
    val logoutSuccess: Boolean = false,
    val error: String? = null,
    val tagFlowStep: TagFlowStep = TagFlowStep.IDLE,
    val tagTime: Long = 0L,
    val tagTimeFormatted: String = "",
    val selectedSymptoms: List<Int> = emptyList(),
    val otherSymptom: String = "",
    val selectedExercise: Int = -1,
    val hasUnsyncedTags: Boolean = false,
    val showSyncErrorBadge: Boolean = false,
    val loginTimeDisplay: String = "",
    val isStatusVerified: Boolean = false
)

class MainViewModel : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val repository get() = ServiceLocator.repository
    private val tokenManager = ServiceLocator.tokenManager

    var uiState by mutableStateOf(MainUiState(isMeasuring = tokenManager.isMeasuring))
        private set

    private var isSyncing = false

    init {
        // Cancel any pending background logout tasks to prevent "ghost logout"
        // after app swipe-away and reopen.
        androidx.work.WorkManager.getInstance(ServiceLocator.appContext)
            .cancelAllWorkByTag("LogoutWorker")

        Log.d(TAG, "MainViewModel Init: isLoggedIn=${tokenManager.isLoggedIn}, isMeasuring=${uiState.isMeasuring}, measureRecordId=${tokenManager.measureRecordId}, deviceId=${tokenManager.deviceId}")
        loadEventTags()
        checkRecordingStatus()
    }

    fun loadEventTags() {
        viewModelScope.launch {
            try {
                val tags = repository.getLocalEventTags()
                val unsyncedCount = tags.count { it.isEdit }
                val lastTime = tags.firstOrNull()?.tagLocalTime

                val loginTime = tokenManager.loginTime
                val isMeasuring = tokenManager.isMeasuring
                val loginTimeStr = if (loginTime > 0) {
                    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(loginTime))
                } else {
                    "未知"
                }

                uiState = uiState.copy(
                    eventTags = tags,
                    hasUnsyncedTags = unsyncedCount > 0,
                    showSyncErrorBadge = uiState.showSyncErrorBadge && unsyncedCount > 0,
                    lastTagTime = lastTime,
                    loginTimeDisplay = loginTimeStr
                )

                if (unsyncedCount > 0) {
                    triggerAutoSync()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error loading event tags", e)
                uiState = uiState.copy(error = "載入資料庫錯誤: ${e.message}")
            }
        }
    }

    private fun triggerAutoSync() {
        if (isSyncing) return
        
        viewModelScope.launch {
            try {
                val unsynced = uiState.eventTags.filter { it.isEdit }
                if (unsynced.isEmpty()) return@launch

                isSyncing = true
                Log.d(TAG, "Auto-sync: triggering upload for ${unsynced.size} tags")
                val result = repository.uploadVirtualEventTags(unsynced)
                if (result.isSuccess) {
                    uiState = uiState.copy(showSyncErrorBadge = false)
                    loadEventTags()
                } else {
                    val error = result.exceptionOrNull()
                    if (error != null && isNetworkError(error)) {
                        uiState = uiState.copy(showSyncErrorBadge = true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-sync failed: ${e.message}")
                if (isNetworkError(e)) {
                    uiState = uiState.copy(showSyncErrorBadge = true)
                }
            } finally {
                isSyncing = false
            }
        }
    }

    private fun isNetworkError(t: Throwable): Boolean {
        return t is java.net.UnknownHostException || 
               t is java.net.ConnectException || 
               t is java.net.SocketTimeoutException ||
               t.message?.contains("Unable to resolve host", ignoreCase = true) == true
    }

    private var isCheckingStatus = false

    fun checkRecordingStatus() {
        Log.d(TAG, "checkRecordingStatus: starting refresh...")
        if (isCheckingStatus) return
        
        viewModelScope.launch {
            try {
                isCheckingStatus = true
                val institutionId = tokenManager.institutionId ?: ""
                val patientId = tokenManager.patientId ?: ""
                
                if (institutionId.isNotEmpty() && patientId.isNotEmpty()) {
                    // Read local measureRecordId for comparison with server response.
                    // measureRecordId is immutable after login — periodic checks only log discrepancies.
                    val localMeasureId = tokenManager.measureRecordId

                    val result = repository.getCurrentMeasurement(institutionId, patientId)
                    if (result.isSuccess) {
                        val info = result.getOrNull()

                        // info == null means 404: recording was deleted
                        if (info == null) {
                            Log.w(TAG, "checkRecordingStatus: session deleted (404), forcing isMeasuring=false")
                            if (uiState.isMeasuring) {
                                uiState = uiState.copy(isMeasuring = false)
                                tokenManager.isMeasuring = false
                            }
                        } else {
                            val serverStatus = info.isMeasuring()
                            val serverMeasureId = info.measureRecordId
                            Log.d(TAG, "checkRecordingStatus: serverStatus=$serverStatus, serverMeasureId=$serverMeasureId, localMeasureId=$localMeasureId")

                            // 根據新的設計：
                            // measureRecordId 一旦在登入時寫入，就永久鎖死在本地。
                            // 背景定期檢查無論發生什麼事（Server ID 不同或是 Server 無 session），
                            // 全都僅記錄 Log，絕對不覆寫 localMeasureId。
                            if (serverMeasureId != null && serverMeasureId != localMeasureId) {
                                Log.w(TAG, "checkRecordingStatus: Server session ID different ($serverMeasureId). Keeping local locked ID: $localMeasureId")
                            } else if (serverMeasureId == null && localMeasureId != null) {
                                Log.w(TAG, "checkRecordingStatus: No active session on server. Keeping local locked ID: $localMeasureId")
                            }

                            // 是否要反灰標籤按鈕 (isMeasuring) 呢？
                            // 若伺服器回傳的是「我們自己的 localMeasureId」的狀態，就照著更新。
                            // 若伺服器端已經被其他裝置（或新錄製）蓋掉 (serverMeasureId != localMeasureId)，
                            // 我們就不任意把自己的按鈕關掉，保護自己能安穩一直打 Tag 直到明確登出/刪除。
                            if (serverMeasureId == localMeasureId) {
                                if (serverStatus) {
                                    if (!uiState.isMeasuring) {
                                        Log.d(TAG, "Enabling tag button (serverStatus=true)")
                                        uiState = uiState.copy(isMeasuring = true)
                                        tokenManager.isMeasuring = true
                                    }
                                } else {
                                    if (uiState.isMeasuring) {
                                        Log.d(TAG, "Disabling tag button (serverStatus=false)")
                                        uiState = uiState.copy(isMeasuring = false)
                                        tokenManager.isMeasuring = false
                                    }
                                }
                            } else {
                                Log.d(TAG, "Ignoring server status change ($serverStatus). Server active session is not our locked session.")
                            }
                            
                            
                            // State is verified after successful API return
                            uiState = uiState.copy(isStatusVerified = true)
                        }
                    } else {
                        // Exception from server (e.g. 400, 401, network disconnection)
                        // Treat these as temporary to allow offline tagging or keep current active state
                        Log.w(TAG, "checkRecordingStatus failed: ${result.exceptionOrNull()?.message}, keeping current local state")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkRecordingStatus failed: ${e.message}")
            } finally {
                isCheckingStatus = false
            }
        }
    }

    // ---- Tag Flow ----
    fun onTagPressed() {
        if (!uiState.isMeasuring) {
            Log.w(TAG, "onTagPressed: ignored because isMeasuring is false")
            return
        }
        val now = System.currentTimeMillis() // Pure UTC
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Log.d(TAG, "onTagPressed: measureRecordId=${tokenManager.measureRecordId}")
        uiState = uiState.copy(
            tagFlowStep = TagFlowStep.SYMPTOM_SELECTION,
            tagTime = now,
            tagTimeFormatted = formatter.format(Date(now)), // Local Taiwan Time
            selectedSymptoms = emptyList(),
            otherSymptom = "",
            selectedExercise = -1
        )
    }

    fun toggleSymptom(symptomId: Int) {
        val current = uiState.selectedSymptoms.toMutableList()
        if (current.contains(symptomId)) current.remove(symptomId)
        else current.add(symptomId)
        uiState = uiState.copy(selectedSymptoms = current)
    }

    fun setOtherSymptom(text: String) {
        uiState = uiState.copy(otherSymptom = text)
    }

    fun goToExerciseSelection() {
        uiState = uiState.copy(tagFlowStep = TagFlowStep.EXERCISE_SELECTION)
    }

    fun selectExercise(exerciseId: Int) {
        uiState = uiState.copy(selectedExercise = exerciseId)
    }

    fun goToConfirmation() {
        uiState = uiState.copy(tagFlowStep = TagFlowStep.CONFIRMATION)
    }

    fun confirmTag() {
        viewModelScope.launch {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val symptoms = uiState.selectedSymptoms.toMutableList()
            // iOS 使用 ID 27 代表「其他症狀」，Android 需要一致才能讓 iOS 正確顯示 others 文字
            if (uiState.otherSymptom.isNotBlank() && !symptoms.contains(27)) {
                symptoms.add(27)
            }
            
            val entity = EventTagDbEntity(
                id = "TAG-${System.currentTimeMillis()}",
                tagTime = uiState.tagTime,
                tagLocalTime = formatter.format(Date(uiState.tagTime)),
                measureMode = 0,
                measureRecordId = tokenManager.measureRecordId ?: "",
                eventType = symptoms,
                others = uiState.otherSymptom.takeIf { it.isNotBlank() },
                exerciseIntensity = uiState.selectedExercise,
                isRead = false,
                isEdit = true
            )
            
            repository.saveEventTag(entity)

            uiState = uiState.copy(
                tagFlowStep = TagFlowStep.IDLE,
                lastTagTime = entity.tagLocalTime
            )

            // Upload immediately first, THEN reload (to avoid triggerAutoSync racing with this upload)
            Log.d(TAG, "Attempting auto-upload for new tag: ${entity.id}")
            val uploadResult = repository.uploadVirtualEventTags(listOf(entity))
            if (uploadResult.isSuccess) {
                Log.i(TAG, "Auto-upload success")
            } else {
                Log.e(TAG, "Auto-upload failed: ${uploadResult.exceptionOrNull()?.message}")
            }

            loadEventTags()
        }
    }

    var isVoiceInputActive: Boolean = false
        private set

    fun setVoiceInputActive(active: Boolean) {
        isVoiceInputActive = active
    }

    fun cancelTagFlow() {
        // 語音辨識期間不要中斷 Tag Flow
        if (isVoiceInputActive) return
        uiState = uiState.copy(tagFlowStep = TagFlowStep.IDLE)
    }

    fun goBackToSymptoms() {
        uiState = uiState.copy(tagFlowStep = TagFlowStep.SYMPTOM_SELECTION)
    }

    fun goBackToExercise() {
        uiState = uiState.copy(tagFlowStep = TagFlowStep.EXERCISE_SELECTION)
    }

    fun logout() {
        viewModelScope.launch {
            try {
                uiState = uiState.copy(isLoading = true)
                Log.d(TAG, "=== Logout Start ===")

                // Safety Net: Save active measureId before clearing, 
                // in case the server unsubscribe fails and we need to "repair" later.
                tokenManager.lastLoggedOutMeasureId = tokenManager.measureRecordId
                Log.d(TAG, "Saved lastLoggedOutMeasureId: ${tokenManager.lastLoggedOutMeasureId}")

                // Refresh token before unsubscribe
                try {
                    val tokenResult = ServiceLocator.repository.getToken()
                    Log.d(TAG, "Token refresh: success=${tokenResult.isSuccess}")
                } catch (e: Exception) {
                    Log.e(TAG, "Token refresh failed: ${e.message}")
                }

                val result = repository.unsubscribePatient()
                if (result.isSuccess) {
                    Log.d(TAG, "=== Logout Complete (local data cleared) ===")
                    uiState = uiState.copy(isLoading = false, logoutSuccess = true)
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "原因不明"
                    Log.e(TAG, "=== Logout Failed: $errorMsg ===")
                    uiState = uiState.copy(isLoading = false, error = "登出失敗：$errorMsg")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Logout error", e)
                uiState = uiState.copy(isLoading = false, error = "登出發生錯誤: ${e.message}")
            }
        }
    }
}
