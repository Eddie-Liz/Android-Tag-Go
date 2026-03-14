package com.rootilabs.wmeCardiac.data.model

import com.squareup.moshi.Json

data class AddVirtualTagsRequest(
    val deviceUUID: String,
    val appVersion: String,
    val appType: Int = 1, // 1 = Android (per API doc)
    val tags: List<VirtualTagRequest>
)

// Server expects symptomTypes as a nested object, not a plain array
data class SymptomTypesPayload(
    val symptomTypes: List<Int>? = null,
    val others: String? = null
)

data class VirtualTagRequest(
    val tagTime: Long,
    val exerciseIntensity: Int,
    val symptomTypes: SymptomTypesPayload? = null
)

data class AddVirtualTagsResponse(
    val institutionId: String? = null,
    val patientId: String? = null,
    val measureRecordId: String? = null,
    val addedSize: Int = 0,
    val updatedSize: Int = 0,
    val failedSize: Int = 0,
    val ignoredSize: Int = 0,
    val failedAdding: List<FailedTag>? = null
)

data class FailedTag(
    val tagTime: Long,
    val exerciseIntensity: Int,
    val symptomTypes: List<Int>? = null
)
