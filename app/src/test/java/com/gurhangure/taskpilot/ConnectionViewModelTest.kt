package com.gurhangure.taskpilot

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import com.gurhangure.taskpilot.feature.connection.ConnectionFeedback
import com.gurhangure.taskpilot.feature.connection.ConnectionViewModel
import com.gurhangure.taskpilot.feature.home.HomeContent
import com.gurhangure.taskpilot.feature.home.HomeViewModel
import com.gurhangure.taskpilot.helpers.FixtureTaskRepository
import com.gurhangure.taskpilot.helpers.MemoryEndpointSettings
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Covers Settings actions and their effect on the running Task List without a live backend. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    /** Installs deterministic scheduling for both screen ViewModels. */
    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    /** Cancels screen jobs and restores the platform dispatcher. */
    @After
    fun tearDown() { store.clear(); Dispatchers.resetMain() }

    /** Restoration populates the editor without treating a saved URL as a verified connection. */
    @Test
    fun showsPersistedEndpointAndDisconnectedStatus() = runTest {
        val connections = InMemoryConnectionRepository({ FixtureTaskRepository() }, settings = MemoryEndpointSettings("https://saved.test/"))
        val settings = model(connections)
        assertTrue(settings.uiState.value.restoring)
        connections.restore()
        runCurrent()
        assertEquals("https://saved.test/", settings.uiState.value.url)
        assertEquals(ConnectionFeedback.Disconnected, settings.uiState.value.feedback)
    }

    /** HTTP requires acknowledgement, and a successful draft test still does not save it. */
    @Test
    fun emulatorSuggestionRequiresConsentAndTestDoesNotSave() = runTest {
        val storage = MemoryEndpointSettings()
        val repository = FixtureTaskRepository()
        val connections = InMemoryConnectionRepository({ repository }, allowEmulatorHttp = true, settings = storage)
        connections.restore()
        val settings = model(connections)
        runCurrent()
        settings.useEmulatorSuggestion()
        assertFalse(settings.uiState.value.canSubmit)
        settings.testConnection()
        runCurrent()
        assertEquals(0, repository.probeCalls)
        settings.acknowledgeHttp(true)
        settings.testConnection()
        runCurrent()
        assertEquals(ConnectionFeedback.Connected, settings.uiState.value.feedback)
        assertEquals(null, storage.saved)
        assertEquals(null, connections.endpoint.value)
    }

    /** Home waits for restoration, then automatically fetches the saved server without a restart. */
    @Test
    fun restoredEndpointAutomaticallyLoadsTheTaskList() = runTest {
        val repository = FixtureTaskRepository()
        val connections = InMemoryConnectionRepository({ repository }, settings = MemoryEndpointSettings("https://saved.test/"))
        val home = ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
        runCurrent()
        assertEquals(HomeContent.Loading, home.uiState.value.content)
        assertEquals(0, repository.listCalls)
        connections.restore("https://build.test/")
        runCurrent()
        assertEquals(HomeContent.Empty, home.uiState.value.content)
        assertEquals(1, repository.listCalls)
        assertEquals("https://saved.test/", connections.endpoint.value)
    }

    /** Saving connects the existing Home ViewModel and persists normalized configuration. */
    @Test
    fun saveLoadsTasksWithoutRestarting() = runTest {
        val task = Task(UUID(0, 1), "Server task", null, false, Instant.EPOCH)
        val storage = MemoryEndpointSettings()
        val repository = FixtureTaskRepository(listResult = { ApiResult.Success(listOf(task)) })
        val connections = InMemoryConnectionRepository({ repository }, settings = storage)
        connections.restore()
        val settings = model(connections)
        val home = ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
        runCurrent()
        assertEquals(HomeContent.ConnectionRequired, home.uiState.value.content)
        assertEquals(0, repository.listCalls)
        settings.editUrl(" https://server.test/prefix ")
        settings.saveAndConnect()
        runCurrent()
        assertEquals("https://server.test/prefix/", storage.saved)
        assertEquals(ConnectionFeedback.Connected, settings.uiState.value.feedback)
        assertEquals(HomeContent.Success(listOf(task)), home.uiState.value.content)
        assertEquals(1, repository.listCalls)
    }

    /** Changing servers cancels the old list fetch and prevents old tasks overwriting the new list. */
    @Test
    fun switchingEndpointsCancelsOldTaskLoad() = runTest {
        val pending = CompletableDeferred<ApiResult<List<Task>>>()
        var cancelled = false
        val task = Task(UUID(0, 2), "New server", null, true, Instant.EPOCH)
        val connections = InMemoryConnectionRepository({ configuration ->
            if (configuration.baseUrl.contains("old.test")) FixtureTaskRepository(listResult = {
                try { pending.await() } finally { cancelled = true }
            }) else FixtureTaskRepository(listResult = { ApiResult.Success(listOf(task)) })
        })
        connections.configure("https://old.test/")
        val home = ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
        runCurrent()
        connections.save("https://new.test/")
        runCurrent()
        assertTrue(cancelled)
        assertEquals(HomeContent.Success(listOf(task)), home.uiState.value.content)
    }

    /** Verification failure preserves the saved address and publishes a typed error. */
    @Test
    fun failedConnectionDoesNotDeleteTheSavedAddress() = runTest {
        val storage = MemoryEndpointSettings()
        val connections = InMemoryConnectionRepository({ FixtureTaskRepository(probeResult = { ApiResult.Failure(ApiError.Timeout) }) }, settings = storage)
        connections.restore()
        val settings = model(connections)
        runCurrent()
        settings.editUrl("https://offline.test/")
        settings.saveAndConnect()
        runCurrent()
        assertEquals(ConnectionFeedback.Error(ApiError.Timeout), settings.uiState.value.feedback)
        assertEquals("https://offline.test/", storage.saved)
        assertEquals("https://offline.test/", settings.uiState.value.savedUrl)
    }

    /** Invalid input does not issue verification calls or replace saved settings. */
    @Test
    fun invalidDraftDoesNotConnect() = runTest {
        val repository = FixtureTaskRepository()
        val connections = InMemoryConnectionRepository({ repository })
        val settings = model(connections)
        runCurrent()
        settings.editUrl("not a URL")
        settings.saveAndConnect()
        runCurrent()
        assertEquals(ConnectionFeedback.Error(ApiError.InvalidUrl), settings.uiState.value.feedback)
        assertEquals(0, repository.probeCalls)
    }

    /** Editing cancels a probe, and even an uncooperative old response cannot replace newer feedback. */
    @Test
    fun staleProbeCannotOverwriteNewerConnectionFeedback() = runTest {
        val oldResult = CompletableDeferred<ApiResult<Unit>>()
        val connections = InMemoryConnectionRepository({ configuration ->
            FixtureTaskRepository(probeResult = {
                if (configuration.baseUrl.contains("old.test")) withContext(NonCancellable) { oldResult.await() }
                else ApiResult.Success(Unit)
            })
        })
        val settings = model(connections)
        runCurrent()
        settings.editUrl("https://old.test/")
        settings.testConnection()
        runCurrent()
        assertEquals(ConnectionFeedback.Connecting, settings.uiState.value.feedback)
        settings.editUrl("https://new.test/")
        settings.testConnection()
        runCurrent()
        assertEquals(ConnectionFeedback.Connected, settings.uiState.value.feedback)
        oldResult.complete(ApiResult.Failure(ApiError.Connection))
        runCurrent()
        assertEquals(ConnectionFeedback.Connected, settings.uiState.value.feedback)
        assertEquals("https://new.test/", settings.uiState.value.url)
    }

    /** Leaving Settings cancels a hanging verification request and resets its progress feedback. */
    @Test
    fun leavingSettingsCancelsTheProbe() = runTest {
        val pending = CompletableDeferred<ApiResult<Unit>>()
        var cancelled = false
        val connections = InMemoryConnectionRepository({ FixtureTaskRepository(probeResult = {
            try { pending.await() } finally { cancelled = true }
        }) })
        val settings = model(connections)
        runCurrent()
        settings.editUrl("https://server.test/")
        settings.testConnection()
        runCurrent()
        settings.cancelPending()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(ConnectionFeedback.Disconnected, settings.uiState.value.feedback)
    }

    /** Retains the model in a lifecycle store to verify structured cancellation. */
    private fun model(connections: InMemoryConnectionRepository): ConnectionViewModel =
        ViewModelProvider(store, ConnectionViewModel.factory(connections))[ConnectionViewModel::class.java]
}
