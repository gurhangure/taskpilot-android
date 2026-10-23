package com.gurhangure.taskpilot

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import com.gurhangure.taskpilot.data.repository.TaskRepository
import com.gurhangure.taskpilot.feature.home.HomeContent
import com.gurhangure.taskpilot.feature.home.HomeViewModel
import com.gurhangure.taskpilot.feature.home.TaskFilter
import com.gurhangure.taskpilot.feature.home.TaskStatistics
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Exercises screen state and actions with controlled repositories and a deterministic main dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val active = task("Review notes", false)
    private val completed = task("Prepare demo", true)

    /** Installs the dispatcher before any ViewModel scope is created. */
    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    /** Cancels ViewModel work and restores the platform dispatcher. */
    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    /** Publishes loading immediately and ignores duplicate requests until the first one completes. */
    @Test
    fun loadingThenSuccessDoesNotDuplicateRequests() = runTest {
        val response = CompletableDeferred<ApiResult<List<Task>>>()
        val repository = FakeTaskRepository { response.await() }
        val viewModel = viewModel(repository)
        assertEquals(HomeContent.Loading, viewModel.uiState.value.content)
        runCurrent()
        viewModel.refresh()
        viewModel.retry()
        runCurrent()
        assertEquals(1, repository.calls)
        response.complete(ApiResult.Success(listOf(active, completed)))
        runCurrent()
        assertEquals(HomeContent.Success(listOf(active, completed)), viewModel.uiState.value.content)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    /** Treats an empty API array as loaded data with known zero counts. */
    @Test
    fun emptyResponseHasAnExplicitState() = runTest {
        val viewModel = viewModel(FakeTaskRepository { ApiResult.Success(emptyList()) })
        runCurrent()
        assertEquals(HomeContent.Empty, viewModel.uiState.value.content)
        assertEquals(TaskStatistics(0, 0, 0), viewModel.uiState.value.statistics)
    }

    /** Surfaces repository errors and allows a successful retry. */
    @Test
    fun retryRecoversFromARepositoryFailure() = runTest {
        var result: ApiResult<List<Task>> = ApiResult.Failure(ApiError.Connection)
        val repository = FakeTaskRepository { result }
        val viewModel = viewModel(repository)
        runCurrent()
        assertEquals(HomeContent.Error(ApiError.Connection), viewModel.uiState.value.content)
        assertEquals(null, viewModel.uiState.value.statistics)
        result = ApiResult.Success(listOf(active))
        viewModel.retry()
        runCurrent()
        assertEquals(HomeContent.Success(listOf(active)), viewModel.uiState.value.content)
        assertEquals(2, repository.calls)
    }

    /** Distinguishes absent and invalid endpoint configuration without invoking a task repository. */
    @Test
    fun configurationProblemsBecomeActionableErrors() = runTest {
        val connections = InMemoryConnectionRepository({ error("No valid endpoint should be used") })
        val viewModel = ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
        runCurrent()
        assertEquals(HomeContent.ConnectionRequired, viewModel.uiState.value.content)
        connections.configure("not a URL")
        viewModel.retry()
        runCurrent()
        assertEquals(HomeContent.Error(ApiError.InvalidUrl), viewModel.uiState.value.content)
    }

    /** Computes counts from all tasks while changing filters without fetching again. */
    @Test
    fun filtersKeepGlobalStatisticsAndDoNotRequestData() = runTest {
        val repository = FakeTaskRepository { ApiResult.Success(listOf(active, completed)) }
        val viewModel = viewModel(repository)
        runCurrent()
        assertEquals(TaskStatistics(2, 1, 1), viewModel.uiState.value.statistics)
        viewModel.selectFilter(TaskFilter.Active)
        assertEquals(listOf(active), viewModel.uiState.value.visibleTasks)
        viewModel.selectFilter(TaskFilter.Completed)
        assertEquals(listOf(completed), viewModel.uiState.value.visibleTasks)
        assertEquals(TaskStatistics(2, 1, 1), viewModel.uiState.value.statistics)
        viewModel.selectFilter(TaskFilter.All)
        assertEquals(listOf(active, completed), viewModel.uiState.value.visibleTasks)
        assertEquals(1, repository.calls)
    }

    /** Keeps loaded data and the filter during refresh, then replaces it with server-confirmed data. */
    @Test
    fun refreshPreservesSelectionAndUpdatesStatistics() = runTest {
        var response: suspend () -> ApiResult<List<Task>> = { ApiResult.Success(listOf(active, completed)) }
        val repository = FakeTaskRepository { response() }
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.selectFilter(TaskFilter.Active)
        val pending = CompletableDeferred<ApiResult<List<Task>>>()
        response = { pending.await() }
        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshing)
        assertEquals(listOf(active), viewModel.uiState.value.visibleTasks)
        viewModel.refresh()
        assertEquals(2, repository.calls)
        pending.complete(ApiResult.Success(listOf(completed)))
        runCurrent()
        assertEquals(TaskFilter.Active, viewModel.uiState.value.filter)
        assertTrue(viewModel.uiState.value.visibleTasks.isEmpty())
        assertEquals(TaskStatistics(1, 0, 1), viewModel.uiState.value.statistics)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    /** Retains the last successful list on refresh failure and clears the warning after retry. */
    @Test
    fun refreshFailureKeepsDataAndCanBeRetried() = runTest {
        var result: ApiResult<List<Task>> = ApiResult.Success(listOf(active))
        val viewModel = viewModel(FakeTaskRepository { result })
        runCurrent()
        result = ApiResult.Failure(ApiError.Http(503))
        viewModel.refresh()
        runCurrent()
        assertEquals(HomeContent.Success(listOf(active)), viewModel.uiState.value.content)
        assertEquals(ApiError.Http(503), viewModel.uiState.value.refreshError)
        result = ApiResult.Success(emptyList())
        viewModel.retry()
        runCurrent()
        assertEquals(HomeContent.Empty, viewModel.uiState.value.content)
        assertEquals(null, viewModel.uiState.value.refreshError)
    }

    /** Clearing the ViewModel cancels its repository request without presenting cancellation as an API error. */
    @Test
    fun clearingViewModelCancelsRefresh() = runTest {
        val pending = CompletableDeferred<ApiResult<List<Task>>>()
        var cancelled = false
        var first = true
        val viewModel = viewModel(FakeTaskRepository {
            if (first) {
                first = false
                ApiResult.Success(listOf(active))
            } else {
                try { pending.await() } finally { cancelled = true }
            }
        })
        runCurrent()
        viewModel.refresh()
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(HomeContent.Success(listOf(active)), viewModel.uiState.value.content)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(null, viewModel.uiState.value.refreshError)
    }

    /** Injects the existing connection boundary and retains the model in a cancellable store. */
    private fun viewModel(repository: TaskRepository): HomeViewModel {
        val connections = InMemoryConnectionRepository({ repository })
        connections.configure("https://example.test/")
        return ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
    }

    /** Creates immutable task fixtures with deterministic identities. */
    private fun task(title: String, isCompleted: Boolean): Task = Task(
        UUID.nameUUIDFromBytes(title.toByteArray()), title, null, isCompleted, Instant.EPOCH,
    )

    /** Serves controlled list responses and rejects unexpected mutation calls. */
    private class FakeTaskRepository(private val response: suspend () -> ApiResult<List<Task>>) : TaskRepository {
        var calls = 0
            private set

        /** Records only actual list requests. */
        override suspend fun list(): ApiResult<List<Task>> { calls++; return response() }

        /** Keeps single-task retrieval outside this screen's contract. */
        override suspend fun get(id: UUID): ApiResult<Task> = error("Unexpected get")

        /** Fails if a read-only screen attempts creation. */
        override suspend fun create(title: String, description: String?): ApiResult<Task> = error("Unexpected create")

        /** Fails if a read-only screen attempts editing. */
        override suspend fun update(id: UUID, title: String, description: String?, isCompleted: Boolean): ApiResult<Task> = error("Unexpected update")

        /** Fails if a read-only screen attempts deletion. */
        override suspend fun delete(id: UUID): ApiResult<Unit> = error("Unexpected delete")
    }
}
