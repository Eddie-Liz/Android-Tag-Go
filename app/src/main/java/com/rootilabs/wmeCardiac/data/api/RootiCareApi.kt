package com.rootilabs.wmeCardiac.data.api

import com.rootilabs.wmeCardiac.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface RootiCareApi {

    // API #2: Auth Patient (Subscribe Patient MCT Events)
    @GET("/oauth/vendors/{institutionId}/patients/{patientId}")
    suspend fun authPatient(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String
    ): Response<AuthPatientResponse>

    // API #3: Get Current Measurement Info
    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/currentMeasurement")
    suspend fun getCurrentMeasurementInfo(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String
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

    // API #6: Unsubscribe Patient (Logout) - no body needed per API doc
    @POST("/oauth/vendors/{institutionId}/patients/{patientId}/unsubscribe")
    suspend fun unsubscribePatient(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String
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
