package com.rootilabs.wmeCardiac.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class TokenManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rooticare_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "TokenManager"
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
        private const val KEY_LAST_LOGGED_OUT_ID = "last_logged_out_id"
        private const val KEY_SERVER_URL = "server_url"
    }

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var lastLoggedOutMeasureId: String?
        get() = prefs.getString(KEY_LAST_LOGGED_OUT_ID, null)
        set(value) = prefs.edit().putString(KEY_LAST_LOGGED_OUT_ID, value).apply()

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
                Log.i(TAG, "Generating NEW persistent deviceId: $id")
                // Use commit() to ensure it hits disk immediately
                prefs.edit().putString(KEY_PHONE_ID, id).commit()
            } else {
                Log.v(TAG, "Retrieved existing deviceId: $id")
            }
            return id
        }

    var pushToken: String
        get() {
            var token = prefs.getString("push_token", null)
            if (token == null) {
                token = java.util.UUID.randomUUID().toString()
                Log.i(TAG, "Generating NEW persistent pushToken: $token")
                // Use commit() for safety
                prefs.edit().putString("push_token", token).commit()
            }
            return token
        }
        set(value) { prefs.edit().putString("push_token", value).commit() }

    var vendorName: String?
        get() = prefs.getString(KEY_VENDOR_NAME, null)
        set(value) = prefs.edit().putString(KEY_VENDOR_NAME, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var institutionId: String?
        get() = prefs.getString(KEY_INSTITUTION_ID, null)?.lowercase()
        set(value) = prefs.edit().putString(KEY_INSTITUTION_ID, value?.lowercase()).apply()

    var patientId: String?
        get() = prefs.getString(KEY_PATIENT_ID, null)?.lowercase()
        set(value) = prefs.edit().putString(KEY_PATIENT_ID, value?.lowercase()).apply()

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
        Log.w(TAG, "clearAll() called: clearing most preferences but preserving identity IDs")
        val phoneId = prefs.getString(KEY_PHONE_ID, null)
        val pushToken = prefs.getString("push_token", null)
        val lastLoggedOutId = prefs.getString(KEY_LAST_LOGGED_OUT_ID, null)
        val savedServerUrl = prefs.getString(KEY_SERVER_URL, null)
        
        // Single atomic-like transaction to clear and restore
        val editor = prefs.edit().clear()
        if (phoneId != null) editor.putString(KEY_PHONE_ID, phoneId)
        if (pushToken != null) editor.putString("push_token", pushToken)
        if (lastLoggedOutId != null) editor.putString(KEY_LAST_LOGGED_OUT_ID, lastLoggedOutId)
        if (savedServerUrl != null) editor.putString(KEY_SERVER_URL, savedServerUrl)
        editor.commit() // Use commit() here for immediate persistence
    }
}
