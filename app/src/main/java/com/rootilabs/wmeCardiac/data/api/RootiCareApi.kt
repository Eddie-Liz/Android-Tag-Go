package com.rootilabs.wmeCardiac.data.api

import com.rootilabs.wmeCardiac.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface RootiCareApi {

    // API #2: Get Recording Measurements (User selects from here)
    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/recordingMeasurements")
    suspend fun getRecordingMeasurements(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String
    ): Response<List<MeasurementInfo>>

    // API #3: Auth Patient (Subscribe Patient MCT Events)
    @GET("/oauth/vendors/{institutionId}/patients/{patientId}/measures/{measureId}")
    suspend fun authPatient(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String
    ): Response<AuthPatientResponse>

    // API #4: Get Current Measurement Info
    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}")
    suspend fun getCurrentMeasurementInfo(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String
    ): Response<MeasurementInfo>

    // API #4: Get Total History Event Tag Count
    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags/totalHistory")
    suspend fun getTotalHistoryCount(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String
    ): Response<TotalHistoryResponse>

    // API #5: Get Event Tag History (Paginated)
    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags/history")
    suspend fun getEventTagHistory(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String,
        @Query("pageSize") pageSize: Int = 5,
        @Query("pageNumber") pageNumber: Int
    ): Response<EventTagHistoryResponse>

    // API #7: Unsubscribe Patient (Logout)
    @POST("/oauth/vendors/{institutionId}/patients/{patientId}/measures/{measureId}/unsubscribe")
    suspend fun unsubscribePatient(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String
    ): Response<ResponseBody>

    // API #7: Add Virtual Event Tags
    @POST("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags")
    suspend fun addVirtualEventTags(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String,
        @Body body: AddVirtualTagsRequest
    ): Response<AddVirtualTagsResponse>
}
