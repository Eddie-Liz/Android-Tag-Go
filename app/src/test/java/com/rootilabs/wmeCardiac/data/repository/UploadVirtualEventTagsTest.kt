package com.rootilabs.wmeCardiac.data.repository

import com.rootilabs.wmeCardiac.data.auth.TokenManager
import com.rootilabs.wmeCardiac.data.local.EventTagDbEntity
import com.rootilabs.wmeCardiac.data.model.AddVirtualTagsResponse
import com.rootilabs.wmeCardiac.data.model.FailedTag
import com.rootilabs.wmeCardiac.data.model.MeasurementInfo
import com.rootilabs.wmeCardiac.testing.FakeDatabase
import com.rootilabs.wmeCardiac.testing.InMemoryPrefsContext
import com.rootilabs.wmeCardiac.testing.ScriptedApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UploadVirtualEventTagsTest {

    private val api = ScriptedApi()
    private val database = FakeDatabase()
    private lateinit var repository: RootiCareRepository

    @Before
    fun setUp() {
        val tokenManager = TokenManager(InMemoryPrefsContext()).apply {
            institutionId = "institution-1"
            patientId = "patient-1"
            measureRecordId = "record-1"
            serverDeviceId = "device-1"
        }
        repository = RootiCareRepository(
            authApi = api,
            rootiCareApi = api,
            tokenManager = tokenManager,
            database = database,
            moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        )
    }

    @Test
    fun `tag rejected by a 2xx response stays unsynced while the accepted ones are marked synced`() {
        val accepted = tag("TAG-1", seconds = 1_700_000_001L)
        val rejected = tag("TAG-2", seconds = 1_700_000_002L)
        api.uploadResponse = AddVirtualTagsResponse(
            addedSize = 1,
            failedSize = 1,
            failedAdding = listOf(FailedTag(tagTime = 1_700_000_002L, exerciseIntensity = 0))
        )

        val result = runBlocking { repository.uploadVirtualEventTags(listOf(accepted, rejected)) }

        assertTrue(result.isFailure)
        assertEquals(listOf("TAG-1"), database.inserted.map { it.id })
        assertFalse(database.inserted.single().isEdit)
        assertTrue(database.inserted.single().isRead)
    }

    @Test
    fun `whole batch stays unsynced when failedAdding cannot account for failedSize`() {
        api.uploadResponse = AddVirtualTagsResponse(addedSize = 1, failedSize = 1, failedAdding = null)

        val result = runBlocking {
            repository.uploadVirtualEventTags(listOf(tag("TAG-1", 1_700_000_001L), tag("TAG-2", 1_700_000_002L)))
        }

        assertTrue(result.isFailure)
        assertTrue(database.inserted.isEmpty())
    }

    @Test
    fun `two tags in the rejected second keep the whole batch unsynced`() {
        api.uploadResponse = AddVirtualTagsResponse(
            addedSize = 1,
            failedSize = 1,
            failedAdding = listOf(FailedTag(tagTime = 1_700_000_001L, exerciseIntensity = 0))
        )

        val result = runBlocking {
            repository.uploadVirtualEventTags(listOf(tag("TAG-1", 1_700_000_001L), tag("TAG-2", 1_700_000_001L)))
        }

        assertTrue(result.isFailure)
        assertTrue(database.inserted.isEmpty())
    }

    @Test
    fun `fully accepted batch is marked synced and succeeds`() {
        api.uploadResponse = AddVirtualTagsResponse(addedSize = 2)

        val result = runBlocking {
            repository.uploadVirtualEventTags(listOf(tag("TAG-1", 1_700_000_001L), tag("TAG-2", 1_700_000_002L)))
        }

        assertTrue(result.isSuccess)
        assertEquals(listOf("TAG-1", "TAG-2"), database.inserted.map { it.id })
        assertTrue(database.inserted.none { it.isEdit })
    }

    private fun tag(id: String, seconds: Long) = EventTagDbEntity(
        id = id,
        tagTime = seconds * 1000 + 500,
        tagLocalTime = "local-time",
        measureMode = MeasurementInfo.MODE_VIRTUAL_TAG,
        measureRecordId = "record-1",
        eventType = emptyList(),
        others = null,
        exerciseIntensity = 0,
        isRead = false,
        isEdit = true
    )
}
