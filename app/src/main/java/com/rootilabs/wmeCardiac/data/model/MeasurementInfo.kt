package com.rootilabs.wmeCardiac.data.model

data class MeasurementInfo(
    val measureRecordOid: Long? = null,
    val measureRecordId: String? = null,
    val mode: Int? = null,
    val state: Int? = null,
    val launchTime: Long? = null,
    val expectedEndTime: Long? = null,
    val deviceId: String? = null,
    val isPatientSubscribed: Boolean? = null
) {
    fun isMeasuring(): Boolean {
        val now = System.currentTimeMillis()
        val s = state ?: -1
        val et = expectedEndTime ?: 0L
        
        // If expectedEndTime is small (e.g. 10 digits), treat as seconds and convert to millis
        val endTimeMillis = if (et > 0 && et < 100000000000L) et * 1000 else et
        
        // Add 2-minute buffer for clock drift
        val bufferMillis = 120_000L
        
        // Allow only STATE_MEASURING (0) as active measuring state
        val isValidState = s == STATE_MEASURING
        val isTimeValid = et != 0L && now < (endTimeMillis + bufferMillis)
        
        val isOk = isValidState && isTimeValid
        
        if (!isOk) {
            android.util.Log.w("MeasurementInfo", "isMeasuring=false: state=$s, endTime=$endTimeMillis, now=$now, buffer=$bufferMillis")
        }
        return isOk
    }

    fun isVirtualTagMode(): Boolean = mode == MODE_VIRTUAL_TAG

    companion object {
        const val STATE_MEASURING = 0
        const val MODE_VIRTUAL_TAG = 0  // Holter
        const val MODE_MONITOR = 1
        const val MODE_MCT_TAG = 2
    }
}
