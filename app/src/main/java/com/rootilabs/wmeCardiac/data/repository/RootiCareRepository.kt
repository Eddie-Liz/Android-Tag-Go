package com.rootilabs.wmeCardiac.data.repository

import android.util.Log
import androidx.work.*
import androidx.work.workDataOf
import androidx.work.OneTimeWorkRequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rootilabs.wmeCardiac.Constants
import com.rootilabs.wmeCardiac.data.api.AuthApi
import com.rootilabs.wmeCardiac.data.api.RootiCareApi
import com.rootilabs.wmeCardiac.data.auth.TokenManager
import com.rootilabs.wmeCardiac.data.local.AppDatabase
import com.rootilabs.wmeCardiac.data.local.EventTagDbEntity
import com.rootilabs.wmeCardiac.data.model.*
import retrofit2.Response
import com.squareup.moshi.Moshi
import java.text.SimpleDateFormat
import java.util.*
import com.rootilabs.wmeCardiac.di.ServiceLocator
import com.rootilabs.wmeCardiac.data.worker.LogoutWorker
import com.rootilabs.wmeCardiac.data.worker.TagUploadWorker

class RootiCareRepository(
    private val authApi: AuthApi,
    private val rootiCareApi: RootiCareApi,
    private val tokenManager: TokenManager,
    private val database: AppDatabase,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "RootiCareRepo"
    }

    // ---- API #1: Token ----
    suspend fun getToken(): Result<String> {
        return try {
            val response = authApi.getToken(
                basicAuth = Constants.BASIC_AUTH,
                body = mapOf("grant_type" to "client_credentials")
            )
            if (response.isSuccessful) {
                val token = response.body()?.accessToken
                if (token != null) {
                    withContext(Dispatchers.IO) { tokenManager.accessToken = token }
                    Result.success(token)
                } else {
                    Result.failure(Exception("Token is null"))
                }
            } else {
                Result.failure(Exception("Token request failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getToken error", e)
            Result.failure(e)
        }
    }

    // ---- API #2: Get Recording Measurements ----
    suspend fun getRecordingMeasurements(institutionId: String, patientId: String): Result<List<MeasurementInfo>> {
        return try {
            val response = rootiCareApi.getRecordingMeasurements(institutionId, patientId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "getRecordingMeasurements failed: HTTP ${response.code()} - $errorBody")
                Result.failure(Exception(parseError(errorBody)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRecordingMeasurements exception", e)
            Result.failure(e)
        }
    }

    // ---- API #3: Auth Patient (GET, subscribes the device) ----
    suspend fun authPatient(institutionId: String, patientId: String, measureId: String): Result<AuthPatientResponse> {
        return try {
            Log.d(TAG, "authPatient: institutionId=$institutionId, patientId=$patientId, measureId=$measureId")
            
            val response = rootiCareApi.authPatient(institutionId, patientId, measureId)
            Log.d(TAG, "authPatient response code: ${response.code()}")
            
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "authPatient response: vendorName=${body?.vendorName}, subscribedBefore=${body?.subscribedBefore}")

                if (body?.vendorName != null) {
                    withContext(Dispatchers.IO) {
                        tokenManager.persistLoginSession(
                            institutionId = institutionId,
                            patientId = patientId,
                            vendorName = body.vendorName,
                            loginTime = System.currentTimeMillis()
                        )
                    }
                    Log.d(TAG, "authPatient success: vendorName=${body.vendorName}")
                    Result.success(body)
                } else {
                    val msg = "登入失敗：無效的回應內容"
                    Log.e(TAG, msg)
                    Result.failure(Exception(msg))
                }
            } else if (response.code() == 409) {
                // Another device is actively logged in
                Log.w(TAG, "authPatient 409: another device is logged in.")
                Result.failure(Exception("ALREADY_SUBSCRIBED"))
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "authPatient failed: HTTP ${response.code()} - $errorBody")
                val errorMsg = parseError(errorBody)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "authPatient exception", e)
            Result.failure(e)
        }
    }

    // ---- API #4: Current Measurement ----
    suspend fun getCurrentMeasurement(
        institutionId: String,
        patientId: String,
        measureId: String
    ): Result<MeasurementInfo?> {
        return try {
            val response = rootiCareApi.getCurrentMeasurementInfo(institutionId, patientId, measureId)
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "getCurrentMeasurement success: $body")
                Result.success(body)
            } else if (response.code() == 404) {
                Log.d(TAG, "getCurrentMeasurement 404: No active measurement found")
                Result.success(null)
            } else {
                val errorMsg = response.errorBody()?.string() ?: ""
                Log.e(TAG, "getCurrentMeasurement failed: ${response.code()} - $errorMsg")
                if (response.code() == 400 && errorMsg.contains("invalid_patient", ignoreCase = true)) {
                    Result.failure(Exception("INVALID_PATIENT"))
                } else {
                    Result.failure(Exception("Get measurement failed: ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentMeasurement error", e)
            Result.failure(e)
        }
    }

    // ---- API #4: Total History Count ----
    suspend fun getTotalHistoryCount(
        institutionId: String,
        patientId: String,
        measureId: String
    ): Result<Int> {
        return try {
            val response = rootiCareApi.getTotalHistoryCount(institutionId, patientId, measureId)
            if (response.isSuccessful) {
                Result.success(response.body()?.totalRow ?: 0)
            } else {
                Result.failure(Exception("Get history count failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTotalHistoryCount error", e)
            Result.failure(e)
        }
    }

    // ---- API #5: Fetch All Event Tag History (paginated) ----
    suspend fun fetchAllEventTagHistory(
        institutionId: String,
        patientId: String,
        measureId: String
    ): Result<Int> {
        return try {
            var currentPage = 1
            var totalSaved = 0

            // 用 tagTime 對應本地記錄（本地 ID 是 TAG-xxx，伺服器 ID 是 UUID，不同）
            val existingTagsByTime = database.eventTagDao().getAll().associateBy { it.tagTime }

            while (true) {
                val response = rootiCareApi.getEventTagHistory(
                    institutionId = institutionId,
                    patientId = patientId,
                    measureId = measureId,
                    pageSize = Constants.PAGE_SIZE,
                    pageNumber = currentPage
                )
                val body = response.body() ?: break

                val dbEntities = body.rows.mapNotNull { tag ->
                    try {
                        val entity = tag.toDbEntity(measureId, MeasurementInfo.MODE_VIRTUAL_TAG)
                        // 伺服器遺失 others 時，從本地同 tagTime 的記錄補回
                        val localTag = existingTagsByTime[entity.tagTime]
                        if (entity.others == null && localTag?.others != null) {
                            entity.copy(others = localTag.others)
                        } else {
                            entity
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "parseVirtualEventTag error: ${e.message}")
                        null
                    }
                }
                database.eventTagDao().insertAll(dbEntities)

                // 刪除已被伺服器版本取代的本地暫存記錄（TAG- 開頭的 ID），避免重複
                dbEntities.forEach { serverEntity ->
                    val localTag = existingTagsByTime[serverEntity.tagTime]
                    if (localTag != null && localTag.id.startsWith("TAG-") && localTag.id != serverEntity.id) {
                        database.eventTagDao().deleteById(localTag.id)
                        Log.d(TAG, "Removed local temp tag ${localTag.id}, replaced by server ${serverEntity.id}")
                    }
                }

                totalSaved += dbEntities.size

                if (currentPage >= body.totalPage) break
                currentPage++
            }

            Result.success(totalSaved)
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllEventTagHistory error", e)
            Result.failure(e)
        }
    }


    // ---- API #7: Unsubscribe (Logout) ----
    suspend fun unsubscribePatient(
        explicitInstitutionId: String? = null,
        explicitPatientId: String? = null,
        explicitMeasureId: String? = null
    ): Result<Unit> {
        val institutionId = explicitInstitutionId ?: tokenManager.institutionId ?: ""
        val patientId = explicitPatientId ?: tokenManager.patientId ?: ""
        val measureId = explicitMeasureId ?: tokenManager.measureRecordId ?: ""

        if (institutionId.isEmpty() || patientId.isEmpty() || measureId.isEmpty()) {
            Log.w(TAG, "Unsubscribe: missing institutionId, patientId or measureId, clearing anyway")
            clearLocalData()
            return Result.success(Unit)
        }

        return try {
            Log.d(TAG, "Logout: POST .../unsubscribe inst=$institutionId, patient=$patientId, measure=$measureId")

            val response = try {
                rootiCareApi.unsubscribePatient(institutionId, patientId, measureId)
            } catch (e: Exception) {
                Log.e(TAG, "Logout network error: ${e.message}")
                return Result.failure(Exception("無網路連線"))
            }

            val statusCode = response.code()
            val responseBody = response.body()?.string()
            Log.d(TAG, "Logout response: code=$statusCode, body=$responseBody")

            if (response.isSuccessful) {
                Log.d(TAG, "Logout success, clearing local data")
                clearLocalData(clearPendingLogout = true)
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "伺服器錯誤 ($statusCode)"
                Log.w(TAG, "Logout server error: $statusCode - $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Logout critical error", e)
            Result.failure(e)
        }
    }

    private fun enqueueLogoutWorker(institutionId: String, patientId: String, measureId: String) {
        try {
            val data = workDataOf(
                "institutionId" to institutionId,
                "patientId" to patientId,
                "measureId" to measureId
            )
            val request = OneTimeWorkRequestBuilder<LogoutWorker>()
                .setInputData(data)
                .addTag("LogoutWorker")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(ServiceLocator.appContext)
                .enqueueUniqueWork("LogoutWorker_$measureId", ExistingWorkPolicy.KEEP, request)
            Log.d(TAG, "Enqueued LogoutWorker for measureId: $measureId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue LogoutWorker: ${e.message}")
        }
    }

    fun enqueueTagUploadWorker() {
        try {
            val request = OneTimeWorkRequestBuilder<TagUploadWorker>()
                .addTag("TagUploadWorker")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(ServiceLocator.appContext)
                .enqueueUniqueWork("TagUploadWorker", ExistingWorkPolicy.KEEP, request)
            Log.d(TAG, "Enqueued TagUploadWorker")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue TagUploadWorker: ${e.message}")
        }
    }

    /**
     * Revoke an old/stale subscription during login 409 retry.
     * Unlike unsubscribePatient(), this does NOT clear local data and does NOT enqueue LogoutWorker,
     * so the ongoing login flow can continue safely.
     */
    suspend fun revokeOldSession(institutionId: String, patientId: String, measureId: String) {
        Log.d(TAG, "revokeOldSession: inst=$institutionId, patient=$patientId, meas=$measureId")
        try {
            val response = rootiCareApi.unsubscribePatient(institutionId, patientId, measureId)
            Log.d(TAG, "revokeOldSession response: code=${response.code()}")
        } catch (e: Exception) {
            // Best-effort only — don't schedule LogoutWorker here
            Log.w(TAG, "revokeOldSession network error (best-effort): ${e.message}")
        }
    }


    // ---- API #7: Upload Virtual Event Tags ----
    suspend fun uploadVirtualEventTags(tags: List<EventTagDbEntity>): Result<AddVirtualTagsResponse> {
        val institutionId = tokenManager.institutionId ?: ""
        val patientId = tokenManager.patientId ?: ""
        val measureId = tokenManager.measureRecordId ?: ""

        if (institutionId.isEmpty() || patientId.isEmpty() || measureId.isEmpty()) {
            return Result.failure(Exception("Missing required IDs for upload"))
        }

        return try {
            val request = AddVirtualTagsRequest(
                // Use the RootiRx hardware ID (captured during initial login) as the primary 
                // device identifier. This ensures consistency on the server dashboard 
                // even if the session is refreshed in the background.
                deviceUUID = tokenManager.serverDeviceId ?: tokenManager.deviceId,
                appVersion = tokenManager.appVersion,
                appType = 2, // Android
                tags = tags.map { it.toVirtualTagRequest() }
            )

            // Debug: 印出實際送出的資料
            request.tags.forEachIndexed { i, tag ->
                Log.d(TAG, "=== Tag[$i] Upload Payload ===")
                Log.d(TAG, "  tagTime: ${tag.tagTime}")
                Log.d(TAG, "  exerciseIntensity: ${tag.exerciseIntensity}")
                Log.d(TAG, "  symptomTypes.symptomTypes(${tag.symptomTypes?.symptomTypes?.size ?: 0}個): ${tag.symptomTypes?.symptomTypes}")
                Log.d(TAG, "  symptomTypes.others: ${tag.symptomTypes?.others}")
            }

            val result = rootiCareApi.addVirtualEventTags(
                institutionId = institutionId,
                patientId = patientId,
                measureId = measureId,
                body = request
            )

            if (result.isSuccessful) {
                val response = result.body()!!
                Log.d(TAG, "Upload success: addedSize=${response.addedSize}, updatedSize=${response.updatedSize}, failedSize=${response.failedSize}, ignoredSize=${response.ignoredSize}")
                // Mark as uploaded in local DB — isEdit=false means synced, isRead=true hides resend icon
                val updatedTags = tags.map { it.copy(isEdit = false, isRead = true) }
                database.eventTagDao().insertAll(updatedTags)
                Result.success(response)
            } else {
                val errorBody = result.errorBody()?.string()
                Log.e(TAG, "Upload failed: HTTP ${result.code()} - $errorBody")

                when {
                    // HTTP 409 invalid_measure_record = measure record is ABANDONED (closed).
                    // Per API doc, already-closed records CAN still receive tags.
                    // Treat as synced to avoid infinite retry.
                    result.code() == 409 && errorBody?.contains("invalid_measure_record") == true -> {
                        Log.w(TAG, "Measure record ABANDONED — marking tags as synced")
                        val updatedTags = tags.map { it.copy(isEdit = false, isRead = true) }
                        database.eventTagDao().insertAll(updatedTags)
                        Result.success(AddVirtualTagsResponse(addedSize = tags.size, failedSize = 0))
                    }
                    // HTTP 500 DuplicateKeyException = tag already exists on server.
                    result.code() == 500 && errorBody?.contains("DuplicateKeyException") == true -> {
                        Log.w(TAG, "Duplicate key detected — tag already on server, marking as synced")
                        val updatedTags = tags.map { it.copy(isEdit = false, isRead = true) }
                        database.eventTagDao().insertAll(updatedTags)
                        Result.success(AddVirtualTagsResponse(addedSize = tags.size, failedSize = 0))
                    }
                    else -> {
                        val apiError = parseError(errorBody)
                        // enqueueTagUploadWorker() // Disabled as per user request
                        Result.failure(Exception(apiError))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload exception", e)
            // enqueueTagUploadWorker() // Disabled as per user request
            Result.failure(e)
        }
    }

    suspend fun clearLocalData(clearPendingLogout: Boolean = false) {
        database.eventTagDao().clearAll()
        tokenManager.clearAll(clearPendingLogout)
    }

    suspend fun clearLocalEventTags() {
        Log.d(TAG, "clearLocalEventTags: clearing all local event tags")
        database.eventTagDao().clearAll()
    }

    // ---- Local DB ----
    suspend fun getLocalEventTags(): List<EventTagDbEntity> {
        return database.eventTagDao().getAll()
    }

    suspend fun getUnsyncedCount(): Int {
        return database.eventTagDao().getUnsyncedCount()
    }

    suspend fun saveEventTag(entity: EventTagDbEntity) {
        database.eventTagDao().insertAll(listOf(entity))
    }

    // ---- Helpers ----
    private fun parseError(errorJson: String?): String {
        if (errorJson == null) return "UNKNOWN_ERROR"
        
        if (errorJson.contains("invalid_patient", ignoreCase = true)) {
            return "INVALID_PATIENT"
        }
        if (errorJson.contains("invalid_institution_id", ignoreCase = true)) {
            return "INVALID_INSTITUTION_ID"
        }
        if (errorJson.contains("patient_already_subscribed", ignoreCase = true)) {
            return "ALREADY_LOGGED_IN"
        }

        return try {
            val adapter = moshi.adapter(ApiError::class.java)
            val error = adapter.fromJson(errorJson)
            when (error?.error) {
                "patient_already_subscribed" -> "ALREADY_LOGGED_IN"
                "invalid_patient" -> "INVALID_PATIENT"
                "invalid_institution_id" -> "INVALID_INSTITUTION_ID"
                else -> error?.errorDescription ?: "UNKNOWN_ERROR"
            }
        } catch (e: Exception) {
            "UNKNOWN_ERROR"
        }
    }

    private fun VirtualTagEntity.toDbEntity(measureId: String, measureMode: Int): EventTagDbEntity {
        val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        
        val rawTime = tagTime ?: 0L
        val serverMillis = if (rawTime > 0 && rawTime < 100000000000L) {
            rawTime * 1000
        } else {
            rawTime 
        }

        // 伺服器傳回 UTC 秒數，直接轉換為 UTC ms（不需要再加減時區）
        val tt = serverMillis

        val ei = exerciseIntensity ?: 0
        
        val symptoms = symptomTypes?.symptomTypes ?: emptyList()
        val symptomOthers = symptomTypes?.others

        // Debug: 印出從伺服器讀回的 symptomTypes 格式
        Log.d(TAG, "=== History Tag from Server ===")
        Log.d(TAG, "  raw symptomTypes.symptomTypes: $symptoms")
        Log.d(TAG, "  raw symptomTypes.others: $symptomOthers")

        return EventTagDbEntity(
            id = tagId ?: "TAG-${System.currentTimeMillis()}",
            tagTime = tt,
            tagLocalTime = if (tt > 0L) formatter.format(Date(tt)) else "Unknown",
            measureMode = measureMode,
            measureRecordId = measureId,
            eventType = symptoms,
            others = symptomOthers,
            exerciseIntensity = ei,
            isRead = true,
            isEdit = false
        )
    }

    private fun EventTagDbEntity.toVirtualTagRequest(): VirtualTagRequest {
        // 使用純 UTC 秒數上傳，與 iOS 保持一致（iOS 也送 UTC seconds）
        val utcSeconds = tagTime / 1000

        // Server expects symptomTypes as a nested object: {"symptomTypes": [...], "others": "..."}
        val symptomPayload = if (!eventType.isNullOrEmpty() || !others.isNullOrBlank()) {
            SymptomTypesPayload(
                symptomTypes = eventType?.takeIf { it.isNotEmpty() },
                others = others
            )
        } else null

        return VirtualTagRequest(
            tagTime = utcSeconds,
            exerciseIntensity = exerciseIntensity,
            symptomTypes = symptomPayload
        )
    }
}
