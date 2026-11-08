package com.rootilabs.wmeCardiac.ui.login

import com.rootilabs.wmeCardiac.data.auth.TokenManager
import com.rootilabs.wmeCardiac.data.model.AuthPatientResponse
import com.rootilabs.wmeCardiac.data.model.MeasurementInfo
import com.rootilabs.wmeCardiac.data.repository.RootiCareRepository
import com.rootilabs.wmeCardiac.di.ServiceLocator
import com.rootilabs.wmeCardiac.testing.FakeDatabase
import com.rootilabs.wmeCardiac.testing.InMemoryPrefsContext
import com.rootilabs.wmeCardiac.testing.RecordingProxySelector
import com.rootilabs.wmeCardiac.testing.ScriptedApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.ProxySelector
import java.net.URI
import kotlin.coroutines.CoroutineContext

/**
 * Drives the real [LoginViewModel], [TokenManager], [RootiCareRepository] and
 * [ServiceLocator.reinitWithBaseUrl]. The session starts on a repository backed by [ScriptedApi],
 * standing in for the old region. Any rebuild of the Retrofit stack replaces it with real OkHttp
 * clients, whose target host [RecordingProxySelector] records before refusing the connection, so
 * no request leaves the machine.
 */
class LoginViewModelTest {

    private val oldRegion = ServerRegion.AP
    private val newRegion = ServerRegion.EU
    private val oldToken = "old-region-token"

    private val proxySelector = RecordingProxySelector()
    private val oldRegionApi = ScriptedApi()
    private val previousProxySelector: ProxySelector? = ProxySelector.getDefault()

    private lateinit var tokenManager: TokenManager
    private lateinit var oldRegionRepository: RootiCareRepository

    @Before
    fun setUp() {
        ProxySelector.setDefault(proxySelector)
        tokenManager = TokenManager(InMemoryPrefsContext())
        tokenManager.serverUrl = oldRegion.url
        tokenManager.accessToken = oldToken

        val database = FakeDatabase()
        oldRegionRepository = RootiCareRepository(
            authApi = oldRegionApi,
            rootiCareApi = oldRegionApi,
            tokenManager = tokenManager,
            database = database,
            moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        )
        setLocatorField("tokenManager", tokenManager)
        setLocatorField("database", database)
        setLocatorField("repository", oldRegionRepository)
    }

    @After
    fun tearDown() {
        ProxySelector.setDefault(previousProxySelector)
    }

    @Test
    fun `phase 2 failure then region switch runs phase 1 against the new region`() {
        val viewModel = viewModelWithFailedPhase2()

        viewModel.onServerSelected(newRegion)
        viewModel.login()
        awaitLoginAttemptFinished(viewModel)

        assertEquals("the retry must not reuse the old region", 1, oldRegionApi.count("authPatient"))
        assertEquals(setOf(hostOf(newRegion)), proxySelector.hosts.toSet())
        assertEquals("Phase 1 stops at the refused token request", "GET_TOKEN_FAILED", viewModel.uiState.error)
    }

    @Test
    fun `old region measureRecordId is never stored after switching region`() {
        val viewModel = viewModelWithFailedPhase2()
        scriptSuccessfulPhase2()

        viewModel.onServerSelected(newRegion)
        viewModel.login()
        awaitLoginAttemptFinished(viewModel)

        assertFalse("login must not succeed against the old region", viewModel.uiState.loginSuccess)
        assertNull(tokenManager.measureRecordId)
    }

    @Test
    fun `region switch discards the selected device and the device list`() {
        val viewModel = viewModelWithFailedPhase2()
        seedUiState(
            viewModel,
            viewModel.uiState.copy(measurements = listOf(device("record-1"), device("record-2")), showDeviceSheet = true)
        )

        viewModel.onServerSelected(newRegion)

        val state = viewModel.uiState
        assertFalse(state.showDeviceSheet)
        assertNull(state.selectedMeasureRecordId)
        assertNull(state.selectedDeviceId)
        assertFalse(state.selectedDeviceIsLoggedIn)
        assertTrue(state.measurements.isEmpty())
        assertFalse(state.showServerSheet)
        assertEquals(newRegion, viewModel.selectedServer)
    }

    @Test
    fun `old region device list without a selection cannot be reopened after switching region`() {
        val viewModel = newViewModel()
        seedUiState(viewModel, viewModel.uiState.copy(measurements = listOf(device("record-1"), device("record-2"))))

        viewModel.onServerSelected(newRegion)
        viewModel.onShowDeviceSheet()

        assertFalse(viewModel.uiState.showDeviceSheet)
    }

    @Test
    fun `region switch keeps token and pending logout after a refused token request`() {
        tokenManager.offlineLogoutPending = true
        tokenManager.lastLoggedOutMeasureId = "record-from-old-logout"
        val viewModel = viewModelWithFailedPhase2()

        viewModel.onServerSelected(newRegion)
        assertEquals(newRegion.url, tokenManager.serverUrl)
        assertEquals(oldToken, tokenManager.accessToken)

        viewModel.login()
        awaitLoginAttemptFinished(viewModel)

        assertEquals("a refused token request must keep the stored token", oldToken, tokenManager.accessToken)
        assertEquals(newRegion.url, tokenManager.serverUrl)
        assertTrue(tokenManager.offlineLogoutPending)
        assertEquals("record-from-old-logout", tokenManager.lastLoggedOutMeasureId)
    }

    @Test
    fun `same server retry goes straight to phase 2 and can succeed`() {
        val viewModel = viewModelWithFailedPhase2()
        scriptSuccessfulPhase2()

        viewModel.login()
        awaitLoginAttemptFinished(viewModel)

        assertSecondPhase2SucceededOnOldRegion(viewModel)
    }

    @Test
    fun `reselecting the current server keeps the selection and retries phase 2`() {
        val viewModel = viewModelWithFailedPhase2()
        scriptSuccessfulPhase2()

        viewModel.onShowServerSheet()
        viewModel.onServerSelected(oldRegion)

        assertFalse(viewModel.uiState.showServerSheet)
        assertEquals("record-1", viewModel.uiState.selectedMeasureRecordId)

        viewModel.login()
        awaitLoginAttemptFinished(viewModel)

        assertSecondPhase2SucceededOnOldRegion(viewModel)
    }

    @Test
    fun `409 for the recently logged out session is repaired and login succeeds`() {
        tokenManager.offlineLogoutPending = true
        tokenManager.lastLoggedOutMeasureId = "record-1"
        oldRegionApi.authPatientResults.addLast { Response.error(409, "".toResponseBody(null)) }
        scriptSuccessfulPhase2()
        val viewModel = newViewModel()

        viewModel.onMeasurementSelected(device("record-1"))
        awaitLoginAttemptFinished(viewModel)

        assertTrue(viewModel.uiState.loginSuccess)
        assertEquals(
            listOf("authPatient", "unsubscribePatient", "authPatient"),
            oldRegionApi.calls.take(3)
        )
        assertEquals("record-1", tokenManager.measureRecordId)
    }

    @Test
    fun `409 for another session is reported and the same server retry stays in phase 2`() {
        oldRegionApi.authPatientResults.addLast { Response.error(409, "".toResponseBody(null)) }
        oldRegionApi.authPatientResults.addLast { Response.error(409, "".toResponseBody(null)) }
        val viewModel = newViewModel()

        viewModel.onMeasurementSelected(device("record-1"))
        awaitLoginAttemptFinished(viewModel)

        assertEquals("ALREADY_SUBSCRIBED", viewModel.uiState.error)
        assertEquals(0, oldRegionApi.count("unsubscribePatient"))

        viewModel.clearError()
        viewModel.login()
        awaitLoginAttemptFinished(viewModel)

        assertEquals("ALREADY_SUBSCRIBED", viewModel.uiState.error)
        assertEquals(2, oldRegionApi.count("authPatient"))
        assertTrue(proxySelector.hosts.isEmpty())
    }

    @Test
    fun `already logged in device alerts without any request and dismissal clears it`() {
        val viewModel = newViewModel()

        viewModel.onMeasurementSelected(device("record-1", subscribed = true))

        assertEquals("ALREADY_LOGGED_IN", viewModel.uiState.error)
        assertTrue(viewModel.uiState.showAlreadyLoggedInAlert)

        viewModel.onDismissAlreadyLoggedInAlert()
        assertNull(viewModel.uiState.selectedMeasureRecordId)
        assertFalse(viewModel.uiState.showAlreadyLoggedInAlert)

        viewModel.onMeasurementSelected(device("record-1", subscribed = true))
        viewModel.login()

        assertTrue(oldRegionApi.calls.isEmpty())
        assertTrue(proxySelector.hosts.isEmpty())
    }

    @Test
    fun `already logged in selection from the old region is discarded by a region switch`() {
        val viewModel = newViewModel()
        viewModel.onMeasurementSelected(device("record-1", subscribed = true))

        viewModel.onServerSelected(newRegion)

        assertNull(viewModel.uiState.selectedMeasureRecordId)
        assertFalse(viewModel.uiState.selectedDeviceIsLoggedIn)
        assertFalse(viewModel.uiState.showAlreadyLoggedInAlert)
        assertNull(viewModel.uiState.error)

        viewModel.login()
        awaitLoginAttemptFinished(viewModel)

        assertEquals("GET_TOKEN_FAILED", viewModel.uiState.error)
        assertEquals(setOf(hostOf(newRegion)), proxySelector.hosts.toSet())
        assertFalse(viewModel.uiState.showAlreadyLoggedInAlert)
    }

    @Test
    fun `main api of the rebuilt stack targets the new region`() {
        val viewModel = viewModelWithFailedPhase2()
        viewModel.onServerSelected(newRegion)
        viewModel.login()
        awaitLoginAttemptFinished(viewModel)
        proxySelector.hosts.clear()

        runBlocking { runCatching { ServiceLocator.rootiCareApi.getRecordingMeasurements("institution-1", "patient-1") } }

        assertEquals(listOf(hostOf(newRegion)), proxySelector.hosts.distinct())
    }

    @Test
    fun `server selection is ignored while a login is in flight`() {
        val viewModel = viewModelWithFailedPhase2()
        seedUiState(viewModel, viewModel.uiState.copy(isLoading = true, showServerSheet = true))

        viewModel.onServerSelected(newRegion)

        assertEquals(oldRegion, viewModel.selectedServer)
        assertEquals(oldRegion.url, tokenManager.serverUrl)
        assertEquals("record-1", viewModel.uiState.selectedMeasureRecordId)
        assertFalse(viewModel.uiState.showServerSheet)
    }

    @Test
    fun `changing the patient after a failed phase 2 runs phase 1 instead of reusing the record`() {
        val viewModel = viewModelWithFailedPhase2()

        viewModel.patientId = "patient-2"
        viewModel.login()
        awaitLoginAttemptFinished(viewModel)

        assertEquals("the old record must not be sent for another patient", 1, oldRegionApi.count("authPatient"))
        assertNull(viewModel.uiState.selectedMeasureRecordId)
        assertEquals("GET_TOKEN_FAILED", viewModel.uiState.error)
    }

    @Test
    fun `device list of another patient cannot be reopened`() {
        val viewModel = viewModelWithFailedPhase2()
        seedUiState(viewModel, viewModel.uiState.copy(measurements = listOf(device("record-1"), device("record-2"))))

        viewModel.institutionId = "institution-2"
        viewModel.onShowDeviceSheet()

        assertFalse(viewModel.uiState.showDeviceSheet)
        assertTrue(viewModel.uiState.measurements.isEmpty())
    }

    @Test
    fun `record that is not measuring is dropped so the retry fetches the device list again`() {
        val viewModel = newViewModel()
        scriptSuccessfulPhase2()
        oldRegionApi.expectedEndTime = 1L

        viewModel.onMeasurementSelected(device("record-1"))
        awaitLoginAttemptFinished(viewModel)

        assertEquals("NOT_MEASURING", viewModel.uiState.error)
        assertNull(viewModel.uiState.selectedMeasureRecordId)

        viewModel.clearError()
        viewModel.login()
        awaitLoginAttemptFinished(viewModel)

        assertEquals(1, oldRegionApi.count("authPatient"))
        assertEquals("GET_TOKEN_FAILED", viewModel.uiState.error)
    }

    @Test
    fun `login that fails after authPatient with a failed cleanup leaves no resumable session`() {
        val viewModel = newViewModel()
        scriptSuccessfulPhase2()
        oldRegionApi.expectedEndTime = 1L
        oldRegionApi.unsubscribeResults.addLast { throw IOException("network down") }

        viewModel.onMeasurementSelected(device("record-1"))
        awaitLoginAttemptFinished(viewModel)

        assertEquals("NOT_MEASURING", viewModel.uiState.error)
        assertFalse(tokenManager.isLoggedIn)
        assertFalse(tokenManager.hasActiveSession)
    }

    @Test
    fun `session persisted by an older version without a record is not resumable`() {
        tokenManager.isLoggedIn = true
        assertFalse(tokenManager.hasActiveSession)

        tokenManager.measureRecordId = "record-1"
        assertTrue(tokenManager.hasActiveSession)
    }

    @Test
    fun `scanning during phase 2 cannot change the identity of the login in flight`() {
        val viewModel = newViewModel()
        oldRegionApi.authPatientResults.addLast {
            viewModel.onBarcodeScanned("institution-2,patient-2")
            Response.success(AuthPatientResponse(vendorName = "vendor"))
        }

        viewModel.onMeasurementSelected(device("record-1"))
        awaitLoginAttemptFinished(viewModel)

        assertTrue(viewModel.uiState.loginSuccess)
        assertEquals(listOf("patient-1"), oldRegionApi.patientsOf("getCurrentMeasurementInfo"))
        assertEquals(listOf("patient-1"), oldRegionApi.patientsOf("getTotalHistoryCount"))
        assertEquals("patient-1", tokenManager.patientId)
    }

    private fun newViewModel(): LoginViewModel = LoginViewModel().also {
        assertEquals(oldRegion, it.selectedServer)
        installViewModelScope(it)
        it.institutionId = "institution-1"
        it.patientId = "patient-1"
    }

    /**
     * `viewModelScope` binds to `Dispatchers.Main`, which cannot initialise on the local JVM, and
     * kotlinx-coroutines-test is not a dependency of this module. Pre-registering the scope under
     * lifecycle's own key makes the getter return it. A lifecycle upgrade that renames either the
     * class or the key fails every test here with the missing Main dispatcher error.
     */
    private fun installViewModelScope(viewModel: LoginViewModel) {
        val scope = Class.forName("androidx.lifecycle.viewmodel.internal.CloseableCoroutineScope")
            .getConstructor(CoroutineContext::class.java)
            .newInstance(SupervisorJob() + Dispatchers.Unconfined) as AutoCloseable
        viewModel.addCloseable("androidx.lifecycle.viewmodel.internal.ViewModelCoroutineScope.JOB_KEY", scope)
    }

    private fun viewModelWithFailedPhase2(): LoginViewModel {
        val viewModel = newViewModel()
        oldRegionApi.authPatientResults.addLast { throw IOException("network down") }

        viewModel.onMeasurementSelected(device("record-1"))
        awaitLoginAttemptFinished(viewModel)

        assertEquals("network down", viewModel.uiState.error)
        assertEquals("record-1", viewModel.uiState.selectedMeasureRecordId)
        viewModel.clearError()
        return viewModel
    }

    private fun scriptSuccessfulPhase2() {
        oldRegionApi.authPatientResults.addLast { Response.success(AuthPatientResponse(vendorName = "vendor")) }
    }

    private fun assertSecondPhase2SucceededOnOldRegion(viewModel: LoginViewModel) {
        assertTrue(viewModel.uiState.loginSuccess)
        assertEquals(2, oldRegionApi.count("authPatient"))
        assertEquals("record-1", tokenManager.measureRecordId)
        assertTrue(tokenManager.hasActiveSession)
        assertSame("a same-server retry must not rebuild Retrofit", oldRegionRepository, ServiceLocator.repository)
        assertTrue(proxySelector.hosts.isEmpty())
    }

    private fun awaitLoginAttemptFinished(viewModel: LoginViewModel) {
        awaitUntil("the login attempt to finish") {
            val state = viewModel.uiState
            !state.isLoading && (state.error != null || state.loginSuccess)
        }
    }

    private fun awaitUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for $description")
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun hostOf(region: ServerRegion): String = URI(region.url).host

    private fun device(recordId: String, subscribed: Boolean = false) = MeasurementInfo(
        measureRecordId = recordId,
        deviceId = "device-$recordId",
        isPatientSubscribed = subscribed
    )

    /** `uiState` has a private setter; a device list can only reach it through a real Phase 1. */
    private fun seedUiState(viewModel: LoginViewModel, state: LoginUiState) {
        LoginViewModel::class.java
            .getDeclaredMethod("setUiState", LoginUiState::class.java)
            .apply { isAccessible = true }
            .invoke(viewModel, state)
    }

    private fun setLocatorField(name: String, value: Any) {
        ServiceLocator::class.java
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .set(null, value)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 5L
    }
}
