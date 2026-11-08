package com.rootilabs.wmeCardiac.testing

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.rootilabs.wmeCardiac.data.api.AuthApi
import com.rootilabs.wmeCardiac.data.api.RootiCareApi
import com.rootilabs.wmeCardiac.data.local.AppDatabase
import com.rootilabs.wmeCardiac.data.local.EventTagDao
import com.rootilabs.wmeCardiac.data.local.EventTagDbEntity
import com.rootilabs.wmeCardiac.data.model.AddVirtualTagsRequest
import com.rootilabs.wmeCardiac.data.model.AddVirtualTagsResponse
import com.rootilabs.wmeCardiac.data.model.AuthPatientResponse
import com.rootilabs.wmeCardiac.data.model.EventTagHistoryResponse
import com.rootilabs.wmeCardiac.data.model.MeasurementInfo
import com.rootilabs.wmeCardiac.data.model.TokenResponse
import com.rootilabs.wmeCardiac.data.model.TotalHistoryResponse
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/** Records every host OkHttp tries to reach and points it at a closed local port. */
internal class RecordingProxySelector : ProxySelector() {
    val hosts = CopyOnWriteArrayList<String>()
    private val closedPort = ServerSocket(0).use { it.localPort }

    override fun select(uri: URI): List<Proxy> {
        // java.net.Socket also asks the default selector how to reach the proxy address itself.
        if (uri.scheme == "socket") return listOf(Proxy.NO_PROXY)
        hosts += uri.host
        return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", closedPort)))
    }

    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
}

internal class ScriptedApi : AuthApi, RootiCareApi {
    val calls = CopyOnWriteArrayList<String>()
    val patientsByCall = CopyOnWriteArrayList<Pair<String, String>>()
    val authPatientResults = ArrayDeque<() -> Response<AuthPatientResponse>>()
    val unsubscribeResults = ArrayDeque<() -> Response<ResponseBody>>()
    var expectedEndTime = System.currentTimeMillis() + ONE_HOUR_MILLIS
    var uploadResponse: AddVirtualTagsResponse? = null

    fun count(name: String): Int = calls.count { it == name }

    fun patientsOf(name: String): List<String> = patientsByCall.filter { it.first == name }.map { it.second }

    private fun record(name: String, patientId: String) {
        calls += name
        patientsByCall += name to patientId
    }

    override suspend fun getToken(basicAuth: String, body: Map<String, String>): Response<TokenResponse> {
        calls += "getToken"
        return Response.success(TokenResponse(accessToken = "scripted-token"))
    }

    override suspend fun getRecordingMeasurements(
        institutionId: String,
        patientId: String
    ): Response<List<MeasurementInfo>> {
        record("getRecordingMeasurements", patientId)
        return Response.success(emptyList())
    }

    override suspend fun authPatient(
        institutionId: String,
        patientId: String,
        measureId: String
    ): Response<AuthPatientResponse> {
        record("authPatient", patientId)
        return authPatientResults.removeFirst().invoke()
    }

    override suspend fun getCurrentMeasurementInfo(
        institutionId: String,
        patientId: String,
        measureId: String
    ): Response<MeasurementInfo> {
        record("getCurrentMeasurementInfo", patientId)
        return Response.success(
            MeasurementInfo(
                measureRecordId = measureId,
                mode = MeasurementInfo.MODE_VIRTUAL_TAG,
                state = MeasurementInfo.STATE_MEASURING,
                expectedEndTime = expectedEndTime,
                deviceId = "device-$measureId"
            )
        )
    }

    override suspend fun getTotalHistoryCount(
        institutionId: String,
        patientId: String,
        measureId: String
    ): Response<TotalHistoryResponse> {
        record("getTotalHistoryCount", patientId)
        return Response.success(TotalHistoryResponse(totalRow = 0))
    }

    override suspend fun getEventTagHistory(
        institutionId: String,
        patientId: String,
        measureId: String,
        pageSize: Int,
        pageNumber: Int
    ): Response<EventTagHistoryResponse> = error("History is empty in these scripts")

    override suspend fun unsubscribePatient(
        institutionId: String,
        patientId: String,
        measureId: String
    ): Response<ResponseBody> {
        record("unsubscribePatient", patientId)
        return unsubscribeResults.removeFirstOrNull()?.invoke() ?: Response.success("".toResponseBody(null))
    }

    override suspend fun addVirtualEventTags(
        institutionId: String,
        patientId: String,
        measureId: String,
        body: AddVirtualTagsRequest
    ): Response<AddVirtualTagsResponse> {
        record("addVirtualEventTags", patientId)
        return Response.success(checkNotNull(uploadResponse) { "This script does not upload tags" })
    }

    private companion object {
        const val ONE_HOUR_MILLIS = 3_600_000L
    }
}

internal class FakeDatabase : AppDatabase() {
    val inserted = CopyOnWriteArrayList<EventTagDbEntity>()

    private val dao = object : EventTagDao {
        override suspend fun insertAll(tags: List<EventTagDbEntity>) {
            inserted += tags
        }
        override suspend fun getAllByMeasureMode(measureMode: Int): List<EventTagDbEntity> = emptyList()
        override suspend fun getAll(): List<EventTagDbEntity> = emptyList()
        override suspend fun getTotalCount(): Int = 0
        override suspend fun getUnsyncedCount(): Int = 0
        override suspend fun deleteById(id: String) = Unit
        override suspend fun clearAll() = Unit
    }

    override fun eventTagDao(): EventTagDao = dao
    override fun createInvalidationTracker() = error("Room is never initialised in these tests")
    override fun clearAllTables() = Unit
}

internal class InMemoryPrefsContext : ContextWrapper(null) {
    private val prefs = InMemoryPrefs()
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
}

private class InMemoryPrefs : SharedPreferences {
    private val values = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = synchronized(values) { HashMap(values) }
    override fun getString(key: String, defValue: String?): String? = read(key) ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = read(key) ?: defValues
    override fun getInt(key: String, defValue: Int): Int = read(key) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = read(key) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = read(key) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = read(key) ?: defValue
    override fun contains(key: String): Boolean = synchronized(values) { key in values }
    override fun edit(): SharedPreferences.Editor = InMemoryEditor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(key: String): T? = synchronized(values) { values[key] as T? }

    private inner class InMemoryEditor : SharedPreferences.Editor {
        private val pending = ArrayList<(MutableMap<String, Any?>) -> Unit>()

        private fun enqueue(change: (MutableMap<String, Any?>) -> Unit) = apply { pending += change }

        override fun putString(key: String, value: String?) = enqueue { it[key] = value }
        override fun putStringSet(key: String, value: MutableSet<String>?) = enqueue { it[key] = value }
        override fun putInt(key: String, value: Int) = enqueue { it[key] = value }
        override fun putLong(key: String, value: Long) = enqueue { it[key] = value }
        override fun putFloat(key: String, value: Float) = enqueue { it[key] = value }
        override fun putBoolean(key: String, value: Boolean) = enqueue { it[key] = value }
        override fun remove(key: String) = enqueue { it.remove(key) }
        override fun clear() = enqueue { it.clear() }

        override fun commit(): Boolean {
            synchronized(values) { pending.forEach { change -> change(values) } }
            pending.clear()
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
