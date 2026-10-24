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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Exercises task writes and endpoint isolation with deterministic, server-like repository snapshots. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskMutationTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val original = Task(UUID(0, 1), "Original", "Details", true, Instant.EPOCH)
    private val repository = MutationRepository()
    private val connections = InMemoryConnectionRepository({ repository })

    /** Uses a controlled main dispatcher before creating screen models. */
    @Before
    fun setUp() { Dispatchers.setMain(dispatcher); connections.configure("https://server.test/") }

    /** Cancels retained jobs without contacting a backend. */
    @After
    fun tearDown() { store.clear(); Dispatchers.resetMain() }

    /** Creation closes the form, reloads, and recalculates counts without changing the filter. */
    @Test
    fun createRefreshesStatisticsAndPreservesFilter() = runTest {
        val home = model()
        runCurrent()
        home.selectFilter(TaskFilter.Completed)
        home.createTask()
        home.editTitle("  New task  ")
        home.editDescription("Notes")
        home.saveTask()
        runCurrent()
        assertNull(home.uiState.value.editor)
        assertEquals(TaskStatistics(1, 1, 0), home.uiState.value.statistics)
        assertEquals(TaskFilter.Completed, home.uiState.value.filter)
        assertTrue(home.uiState.value.visibleTasks.isEmpty())
        assertEquals("  New task  ", repository.tasks.single().title)
        assertEquals("Notes", repository.tasks.single().description)
        assertEquals(2, repository.listCalls)
    }

    /** Shared .NET whitespace and UTF-16 limits reject invalid forms before network work. */
    @Test
    fun invalidCreateKeepsDraftAndDoesNotSubmit() = runTest {
        val home = model()
        runCurrent()
        home.createTask()
        listOf("", "\u0085\u00a0", "😀".repeat(101)).forEach { title ->
            home.editTitle(title)
            home.saveTask()
            assertTrue(home.uiState.value.editor!!.error is ApiError.Validation)
        }
        home.editTitle("Valid")
        home.editDescription("x".repeat(2001))
        home.saveTask()
        runCurrent()
        assertEquals(0, repository.createCalls)
        assertEquals("x".repeat(2001), home.uiState.value.editor!!.description)
    }

    /** API validation and connectivity failures retain user input for correction or explicit retry. */
    @Test
    fun createFailuresKeepInputAndPermitRetry() = runTest {
        val home = model()
        runCurrent()
        home.createTask()
        home.editTitle("Draft")
        val validation = ApiError.Validation(mapOf("Title" to listOf("Rejected by server")))
        repository.createResult = { ApiResult.Failure(validation) }
        home.saveTask()
        runCurrent()
        assertEquals(validation, home.uiState.value.editor!!.error)
        repository.createResult = { ApiResult.Failure(ApiError.Connection) }
        home.saveTask()
        runCurrent()
        assertEquals(ApiError.Connection, home.uiState.value.editor!!.error)
        assertEquals("Draft", home.uiState.value.editor!!.title)
        repository.createResult = { task -> ApiResult.Success(task) }
        home.saveTask()
        runCurrent()
        assertNull(home.uiState.value.editor)
        assertEquals(1, home.uiState.value.tasks.size)
    }

    /** Replacement edits retain identity, creation time, completion, and exact content semantics. */
    @Test
    fun editPreservesIdentityAndCompletionAndClearsDescription() = runTest {
        repository.tasks = listOf(original)
        val home = model()
        runCurrent()
        home.editTask(original)
        assertEquals("Details", home.uiState.value.editor!!.description)
        home.editTitle("Edited")
        home.editDescription("")
        home.saveTask()
        runCurrent()
        assertEquals(original.copy(title = "Edited", description = null), repository.tasks.single())
        assertEquals(repository.tasks, home.uiState.value.tasks)
        assertNull(home.uiState.value.editor)
    }

    /** An unsuccessful edit retains both the confirmed list and the user's unsaved replacement. */
    @Test
    fun failedEditCanBeCorrectedWithoutChangingConfirmedData() = runTest {
        repository.tasks = listOf(original)
        repository.updateResult = { ApiResult.Failure(ApiError.Http(503)) }
        val home = model()
        runCurrent()
        home.editTask(original)
        home.editTitle("Replacement")
        home.saveTask()
        runCurrent()
        assertEquals(listOf(original), home.uiState.value.tasks)
        assertEquals("Replacement", home.uiState.value.editor!!.title)
        assertEquals(ApiError.Http(503), home.uiState.value.editor!!.error)
        home.cancelEditor()
        assertNull(home.uiState.value.editor)
    }

    /** Completion stays server-confirmed, then moving between filters updates counts in both directions. */
    @Test
    fun completesAndReopensWithoutOptimisticChanges() = runTest {
        val active = original.copy(isCompleted = false)
        repository.tasks = listOf(active)
        val home = model()
        runCurrent()
        home.selectFilter(TaskFilter.Active)
        val response = CompletableDeferred<ApiResult<Task>>()
        repository.updateResult = { response.await() }
        home.toggleTask(active)
        runCurrent()
        assertEquals(listOf(active), home.uiState.value.visibleTasks)
        assertTrue(home.uiState.value.isMutating)
        response.complete(ApiResult.Success(original))
        runCurrent()
        assertTrue(home.uiState.value.visibleTasks.isEmpty())
        assertEquals(TaskStatistics(1, 0, 1), home.uiState.value.statistics)
        repository.updateResult = { ApiResult.Success(it) }
        home.toggleTask(original)
        runCurrent()
        assertEquals(listOf(active), home.uiState.value.visibleTasks)
        assertEquals(TaskStatistics(1, 1, 0), home.uiState.value.statistics)
    }

    /** A failed toggle retains the confirmed task and displays an actionable error. */
    @Test
    fun failedToggleKeepsConfirmedTask() = runTest {
        repository.tasks = listOf(original)
        repository.updateResult = { ApiResult.Failure(ApiError.Timeout) }
        val home = model()
        runCurrent()
        home.toggleTask(original)
        runCurrent()
        assertEquals(listOf(original), home.uiState.value.tasks)
        assertEquals(ApiError.Timeout, home.uiState.value.mutationError)
        assertFalse(home.uiState.value.isMutating)
    }

    /** Confirmation is mandatory; cancelling it never issues a delete. */
    @Test
    fun deletionRequiresConfirmationAndSupportsCancel() = runTest {
        repository.tasks = listOf(original)
        val home = model()
        runCurrent()
        home.confirmDelete()
        home.requestDelete(original)
        assertEquals(original, home.uiState.value.deleteTask)
        home.cancelDelete()
        home.confirmDelete()
        runCurrent()
        assertEquals(0, repository.deleteCalls)
        assertEquals(listOf(original), home.uiState.value.tasks)
    }

    /** Failed deletes retain confirmation and data, while a confirmed retry refreshes to empty. */
    @Test
    fun deleteFailureThenSuccessRetainsTheSelectedFilter() = runTest {
        repository.tasks = listOf(original)
        val home = model()
        runCurrent()
        home.selectFilter(TaskFilter.Completed)
        home.requestDelete(original)
        repository.deleteResult = { ApiResult.Failure(ApiError.NotFound) }
        home.confirmDelete()
        runCurrent()
        assertEquals(original, home.uiState.value.deleteTask)
        assertEquals(ApiError.NotFound, home.uiState.value.mutationError)
        assertEquals(listOf(original), home.uiState.value.tasks)
        repository.deleteResult = { ApiResult.Success(Unit) }
        home.confirmDelete()
        runCurrent()
        assertEquals(HomeContent.Empty, home.uiState.value.content)
        assertEquals(TaskStatistics(0, 0, 0), home.uiState.value.statistics)
        assertEquals(TaskFilter.Completed, home.uiState.value.filter)
        assertNull(home.uiState.value.deleteTask)
    }

    /** A pending delete rejects dismissal and repeated confirmation until its response is known. */
    @Test
    fun pendingDeleteIsSubmittedOnlyOnce() = runTest {
        repository.tasks = listOf(original)
        val response = CompletableDeferred<ApiResult<Unit>>()
        repository.deleteResult = { response.await() }
        val home = model()
        runCurrent()
        home.requestDelete(original)
        home.confirmDelete()
        home.confirmDelete()
        home.cancelDelete()
        runCurrent()
        assertEquals(1, repository.deleteCalls)
        assertEquals(original, home.uiState.value.deleteTask)
        assertTrue(home.uiState.value.isMutating)
        response.complete(ApiResult.Success(Unit))
        runCurrent()
        assertEquals(HomeContent.Empty, home.uiState.value.content)
    }

    /** Pending saves reject duplicate submissions, edits, dismissal, and competing write actions. */
    @Test
    fun duplicateAndConflictingMutationsAreIgnored() = runTest {
        repository.tasks = listOf(original)
        val response = CompletableDeferred<ApiResult<Task>>()
        repository.createResult = { response.await() }
        val home = model()
        runCurrent()
        home.createTask()
        home.editTitle("Pending")
        home.saveTask()
        home.saveTask()
        home.editTitle("Replacement")
        home.cancelEditor()
        home.toggleTask(original)
        home.requestDelete(original)
        home.refresh()
        runCurrent()
        assertEquals(1, repository.createCalls)
        assertEquals(0, repository.updateCalls)
        assertEquals("Pending", home.uiState.value.editor!!.title)
        assertEquals(1, repository.listCalls)
        response.complete(ApiResult.Success(original.copy(id = UUID(0, 2), title = "Pending")))
        runCurrent()
        assertEquals(2, home.uiState.value.tasks.size)
    }

    /** Old mutations ignoring cancellation cannot close a new draft or replace the new server's data. */
    @Test
    fun endpointChangeRejectsStaleMutationAndCancelsItsState() = runTest {
        repository.tasks = listOf(original)
        val response = CompletableDeferred<ApiResult<Task>>()
        var cancelled = false
        repository.createResult = {
            try { withContext(NonCancellable) { response.await() } } finally { cancelled = true }
        }
        val replacement = MutationRepository().apply { tasks = listOf(original) }
        val switching = InMemoryConnectionRepository({ if (it.baseUrl.contains("old.test")) repository else replacement })
        switching.configure("https://old.test/")
        val home = model(switching)
        runCurrent()
        home.createTask()
        home.editTitle("Old draft")
        home.saveTask()
        runCurrent()
        switching.configure("https://new.test/")
        home.toggleTask(original)
        runCurrent()
        assertEquals(0, replacement.updateCalls)
        assertFalse(home.uiState.value.isMutating)
        assertNull(home.uiState.value.editor)
        home.createTask()
        home.editTitle("New draft")
        response.complete(ApiResult.Success(original.copy(title = "Stale result")))
        runCurrent()
        assertTrue(cancelled)
        assertEquals("New draft", home.uiState.value.editor!!.title)
        assertEquals(listOf(original), home.uiState.value.tasks)
    }

    /** No endpoint means no form or mutation request can be started. */
    @Test
    fun unconfiguredServerCannotMutate() = runTest {
        val home = model(InMemoryConnectionRepository({ repository }))
        runCurrent()
        home.createTask()
        home.saveTask()
        home.toggleTask(original)
        home.requestDelete(original)
        home.confirmDelete()
        runCurrent()
        assertNull(home.uiState.value.editor)
        assertEquals(0, repository.createCalls + repository.updateCalls + repository.deleteCalls)
    }

    /** Local search follows every confirmed write while retaining the query and selected status. */
    @Test
    fun searchResultsFollowCreateEditCompleteReopenAndDelete() = runTest {
        val home = model()
        runCurrent()
        home.updateSearchQuery("needle")
        home.selectFilter(TaskFilter.Active)
        home.createTask()
        home.editTitle("Unrelated title")
        home.editDescription("Needle in the notes")
        home.saveTask()
        runCurrent()
        val created = home.uiState.value.visibleTasks.single()
        assertEquals(TaskStatistics(1, 1, 0), home.uiState.value.statistics)
        home.editTask(created)
        home.editTitle("Needle title")
        home.editDescription("")
        home.saveTask()
        runCurrent()
        val edited = home.uiState.value.visibleTasks.single()
        assertEquals("Needle title", edited.title)
        assertNull(edited.description)
        home.toggleTask(edited)
        runCurrent()
        assertTrue(home.uiState.value.visibleTasks.isEmpty())
        home.selectFilter(TaskFilter.Completed)
        val completed = home.uiState.value.visibleTasks.single()
        home.toggleTask(completed)
        runCurrent()
        assertTrue(home.uiState.value.visibleTasks.isEmpty())
        home.selectFilter(TaskFilter.Active)
        home.editTask(home.uiState.value.visibleTasks.single())
        home.editTitle("No longer matches")
        home.saveTask()
        runCurrent()
        assertTrue(home.uiState.value.visibleTasks.isEmpty())
        assertEquals("needle", home.uiState.value.searchQuery)
        home.updateSearchQuery("No longer")
        home.requestDelete(home.uiState.value.visibleTasks.single())
        home.confirmDelete()
        runCurrent()
        assertEquals("No longer", home.uiState.value.searchQuery)
        assertEquals(TaskFilter.Active, home.uiState.value.filter)
        assertEquals(HomeContent.Empty, home.uiState.value.content)
        assertEquals(TaskStatistics(0, 0, 0), home.uiState.value.statistics)
    }

    /** Retains the injected Home model in a cancellable lifecycle store. */
    private fun model(source: InMemoryConnectionRepository = connections): HomeViewModel =
        ViewModelProvider(store, HomeViewModel.factory(source))[HomeViewModel::class.java]

    /** Supplies confirmed snapshots and controlled write outcomes without transport or persistence. */
    private class MutationRepository : TaskRepository {
        var tasks = emptyList<Task>()
        var listCalls = 0
        var createCalls = 0
        var updateCalls = 0
        var deleteCalls = 0
        var createResult: suspend (Task) -> ApiResult<Task> = { ApiResult.Success(it) }
        var updateResult: suspend (Task) -> ApiResult<Task> = { ApiResult.Success(it) }
        var deleteResult: suspend () -> ApiResult<Unit> = { ApiResult.Success(Unit) }

        /** Returns the latest fixture server state. */
        override suspend fun list(): ApiResult<List<Task>> { listCalls++; return ApiResult.Success(tasks.toList()) }

        /** Retrieves fixture identities without inventing missing data. */
        override suspend fun get(id: UUID): ApiResult<Task> = tasks.find { it.id == id }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(ApiError.NotFound)

        /** Records a create and stores only a successful result. */
        override suspend fun create(title: String, description: String?): ApiResult<Task> {
            createCalls++
            val result = createResult(Task(UUID(0, 2), title, description, false, Instant.EPOCH))
            if (result is ApiResult.Success) tasks = tasks + result.value
            return result
        }

        /** Records full replacement content while preserving server-owned fields. */
        override suspend fun update(id: UUID, title: String, description: String?, isCompleted: Boolean): ApiResult<Task> {
            updateCalls++
            val previous = tasks.first { it.id == id }
            val result = updateResult(previous.copy(title = title, description = description, isCompleted = isCompleted))
            if (result is ApiResult.Success) tasks = tasks.map { if (it.id == id) result.value else it }
            return result
        }

        /** Deletes fixture state only after a successful response. */
        override suspend fun delete(id: UUID): ApiResult<Unit> {
            deleteCalls++
            val result = deleteResult()
            if (result is ApiResult.Success) tasks = tasks.filterNot { it.id == id }
            return result
        }
    }
}
