package com.rootilabs.wmeCardiac.data.auth

import android.content.Context
import android.content.SharedPreferences

class TokenManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rooticare_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_SERVER_DEVICE_ID = "server_device_id"
        private const val KEY_VENDOR_NAME = "vendor_name"
        private const val KEY_PHONE_ID = "phone_identifier"
        private const val KEY_INSTITUTION_ID = "institution_id"
        private const val KEY_PATIENT_ID = "patient_id"
        private const val KEY_MEASURE_RECORD_ID = "measure_record_id"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_LOGIN_TIME = "login_time"
        private const val KEY_IS_MEASURING = "is_measuring"
    }

    var serverDeviceId: String?
        get() = prefs.getString(KEY_SERVER_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_SERVER_DEVICE_ID, value).apply()

    val deviceId: String
        get() {
            var id = prefs.getString(KEY_PHONE_ID, null)
            if (id == null) {
                // Generate a clean phone-specific ID (not a MAC address)
                val androidId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: java.util.UUID.randomUUID().toString()
                
                // Prefix it so it's clearly different from server hardware IDs (MACs)
                id = "PHONE_$androidId"
                prefs.edit().putString(KEY_PHONE_ID, id).apply()
            }
            return id
        }

    var pushToken: String
        get() {
            var token = prefs.getString("push_token", null)
            if (token == null) {
                token = java.util.UUID.randomUUID().toString() // Generate fake push token for now
                prefs.edit().putString("push_token", token).apply()
            }
            return token
        }
        set(value) = prefs.edit().putString("push_token", value).apply()

    var vendorName: String?
        get() = prefs.getString(KEY_VENDOR_NAME, null)
        set(value) = prefs.edit().putString(KEY_VENDOR_NAME, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var institutionId: String?
        get() = prefs.getString(KEY_INSTITUTION_ID, null)
        set(value) = prefs.edit().putString(KEY_INSTITUTION_ID, value).apply()

    var patientId: String?
        get() = prefs.getString(KEY_PATIENT_ID, null)
        set(value) = prefs.edit().putString(KEY_PATIENT_ID, value).apply()

    var measureRecordId: String?
        get() = prefs.getString(KEY_MEASURE_RECORD_ID, null)
        set(value) = prefs.edit().putString(KEY_MEASURE_RECORD_ID, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var loginTime: Long
        get() = prefs.getLong(KEY_LOGIN_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LOGIN_TIME, value).apply()

    var isMeasuring: Boolean
        get() = prefs.getBoolean(KEY_IS_MEASURING, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_MEASURING, value).apply()

    val appVersion: String
        get() = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }

    fun clearAll() {
        val deviceId = prefs.getString(KEY_PHONE_ID, null)
        val pushToken = prefs.getString("push_token", null)
        prefs.edit().clear().apply()
        if (deviceId != null) {
            prefs.edit().putString(KEY_PHONE_ID, deviceId).apply()
        }
        if (pushToken != null) {
            prefs.edit().putString("push_token", pushToken).apply()
        }
    }
}
