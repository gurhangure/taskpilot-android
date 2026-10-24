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
import com.gurhangure.taskpilot.feature.home.TaskFilter
import com.gurhangure.taskpilot.feature.home.TaskStatistics
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

    /** Saving the selected URL recovers an earlier list error once verification succeeds. */
    @Test
    fun savingSameEndpointClearsAnEarlierConnectionError() = runTest {
        val verification = CompletableDeferred<ApiResult<Unit>>()
        var result: ApiResult<List<Task>> = ApiResult.Failure(ApiError.Connection)
        val repository = FixtureTaskRepository(listResult = { result }, probeResult = { verification.await() })
        val connections = InMemoryConnectionRepository({ repository })
        connections.configure("https://server.test/")
        val home = ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
        val settings = model(connections)
        runCurrent()
        assertEquals(HomeContent.Error(ApiError.Connection), home.uiState.value.content)
        result = ApiResult.Success(emptyList())
        settings.saveAndConnect()
        runCurrent()
        assertEquals(1, repository.listCalls)
        verification.complete(ApiResult.Success(Unit))
        runCurrent()
        assertEquals(HomeContent.Empty, home.uiState.value.content)
        assertEquals(null, home.uiState.value.refreshError)
        assertEquals(2, repository.listCalls)
    }

    /** Verification waits for an in-flight list before recovering its failure, without overlapping loads. */
    @Test
    fun verificationBeforeListFailureRecoversWithoutDuplicateRequests() = runTest {
        val verification = CompletableDeferred<ApiResult<Unit>>()
        val firstList = CompletableDeferred<ApiResult<List<Task>>>()
        var first = true
        val repository = FixtureTaskRepository(listResult = {
            if (first) { first = false; firstList.await() } else ApiResult.Success(emptyList())
        }, probeResult = { verification.await() })
        val connections = InMemoryConnectionRepository({ repository })
        val home = ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
        val settings = model(connections)
        runCurrent()
        settings.editUrl("https://server.test/")
        settings.saveAndConnect()
        runCurrent()
        verification.complete(ApiResult.Success(Unit))
        runCurrent()
        home.refresh()
        home.retry()
        runCurrent()
        assertEquals(1, repository.listCalls)
        firstList.complete(ApiResult.Failure(ApiError.Connection))
        runCurrent()
        assertEquals(HomeContent.Empty, home.uiState.value.content)
        assertEquals(2, repository.listCalls)
    }

    /** A healthy list is not reloaded by same-URL saves, while refresh and retry still work. */
    @Test
    fun unchangedEndpointDoesNotReloadHealthyData() = runTest {
        var result: ApiResult<List<Task>> = ApiResult.Success(emptyList())
        val repository = FixtureTaskRepository(listResult = { result })
        val connections = InMemoryConnectionRepository({ repository })
        connections.configure("https://server.test/")
        val home = ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
        val settings = model(connections)
        runCurrent()
        settings.editUrl(" https://server.test ")
        settings.saveAndConnect()
        runCurrent()
        settings.saveAndConnect()
        runCurrent()
        assertEquals(1, repository.listCalls)
        result = ApiResult.Failure(ApiError.Timeout)
        home.refresh()
        runCurrent()
        assertEquals(ApiError.Timeout, home.uiState.value.refreshError)
        result = ApiResult.Success(emptyList())
        home.retry()
        runCurrent()
        assertEquals(null, home.uiState.value.refreshError)
        assertEquals(3, repository.listCalls)
    }

    /** Switching servers clears old errors and retains filters while deriving fresh statistics. */
    @Test
    fun newEndpointClearsOldErrorsBeforeItsListArrives() = runTest {
        val response = CompletableDeferred<ApiResult<List<Task>>>()
        val task = Task(UUID(0, 3), "New completed task", null, true, Instant.EPOCH)
        val connections = InMemoryConnectionRepository({ configuration ->
            FixtureTaskRepository(listResult = {
                if (configuration.baseUrl.contains("old.test")) ApiResult.Failure(ApiError.Connection)
                else response.await()
            })
        })
        connections.configure("https://old.test/")
        val home = ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
        runCurrent()
        assertEquals(HomeContent.Error(ApiError.Connection), home.uiState.value.content)
        home.selectFilter(TaskFilter.Completed)
        connections.save("https://new.test/")
        runCurrent()
        assertEquals(HomeContent.Loading, home.uiState.value.content)
        assertEquals(null, home.uiState.value.refreshError)
        response.complete(ApiResult.Success(listOf(task)))
        runCurrent()
        assertEquals(listOf(task), home.uiState.value.visibleTasks)
        assertEquals(TaskFilter.Completed, home.uiState.value.filter)
        assertEquals(TaskStatistics(1, 0, 1), home.uiState.value.statistics)
    }

    /** A cancelled server that ignores cancellation cannot delay or overwrite the replacement list. */
    @Test
    fun staleListCannotOverwriteNewEndpointState() = runTest {
        val oldResponse = CompletableDeferred<ApiResult<List<Task>>>()
        val newResponse = CompletableDeferred<ApiResult<List<Task>>>()
        val newRepository = FixtureTaskRepository(listResult = { newResponse.await() })
        val connections = InMemoryConnectionRepository({ configuration ->
            if (configuration.baseUrl.contains("old.test")) FixtureTaskRepository(listResult = {
                withContext(NonCancellable) { oldResponse.await() }
            }) else newRepository
        })
        connections.configure("https://old.test/")
        val home = ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
        runCurrent()
        connections.save("https://new.test/")
        runCurrent()
        assertEquals(1, newRepository.listCalls)
        newResponse.complete(ApiResult.Success(emptyList()))
        runCurrent()
        oldResponse.complete(ApiResult.Success(listOf(Task(UUID(0, 4), "Stale task", null, false, Instant.EPOCH))))
        runCurrent()
        assertEquals(HomeContent.Empty, home.uiState.value.content)
        assertEquals(null, home.uiState.value.refreshError)
        assertFalse(home.uiState.value.isLoading)
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
