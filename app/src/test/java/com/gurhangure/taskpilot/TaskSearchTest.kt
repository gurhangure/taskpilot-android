package com.gurhangure.taskpilot

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import com.gurhangure.taskpilot.feature.home.HomeContent
import com.gurhangure.taskpilot.feature.home.HomeUiState
import com.gurhangure.taskpilot.feature.home.HomeViewModel
import com.gurhangure.taskpilot.feature.home.TaskFilter
import com.gurhangure.taskpilot.feature.home.TaskStatistics
import com.gurhangure.taskpilot.helpers.FixtureTaskRepository
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Covers local substring matching and its interaction with existing Home state and requests. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskSearchTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val active = Task(UUID(0, 1), "İstanbul plan", "Review the release notes", false, Instant.EPOCH)
    private val completed = Task(UUID(0, 2), "Ship update", "IĞDIR delivery", true, Instant.EPOCH)
    private val other = Task(UUID(0, 3), "ışık check", null, false, Instant.EPOCH)
    private val tasks = listOf(active, completed, other)

    /** Installs controlled scheduling for screen actions. */
    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    /** Cancels retained screen work and restores the main dispatcher. */
    @After
    fun tearDown() { store.clear(); Dispatchers.resetMain() }

    /** Title matching uses case-insensitive substrings while statistics count all loaded data. */
    @Test
    fun searchesTitlesWithoutChangingGlobalCounts() {
        val state = state("PLAN")
        assertEquals(listOf(active), state.visibleTasks)
        assertEquals(TaskStatistics(3, 2, 1), state.statistics)
    }

    /** Optional descriptions participate in matching without treating null as searchable text. */
    @Test
    fun searchesDescriptionsAndHandlesNull() {
        assertEquals(listOf(active), state("reLEASE").visibleTasks)
        assertEquals(listOf(completed), state("delivery").visibleTasks)
        assertTrue(state("null").visibleTasks.isEmpty())
    }

    /** Unicode simple case matching treats dotted and dotless I alike, independently of device locale. */
    @Test
    fun matchesTurkishDottedAndDotlessI() {
        listOf("I", "i", "İ", "ı").forEach { query ->
            assertEquals("Query: $query", tasks, state(query).visibleTasks)
        }
        assertEquals(listOf(completed), state("ığdır").visibleTasks)
        assertEquals(listOf(other), state("IŞIK").visibleTasks)
        assertEquals(listOf(active), state("istanbul").visibleTasks)
    }

    /** Search and status filters intersect without changing either control's value. */
    @Test
    fun combinesSearchWithEveryStatusFilter() {
        assertEquals(tasks, state("i", TaskFilter.All).visibleTasks)
        assertEquals(listOf(active, other), state("i", TaskFilter.Active).visibleTasks)
        assertEquals(listOf(completed), state("i", TaskFilter.Completed).visibleTasks)
        val noMatch = state("delivery", TaskFilter.Active)
        assertTrue(noMatch.visibleTasks.isEmpty())
        assertEquals(TaskFilter.Active, noMatch.filter)
        assertEquals(HomeContent.Success(tasks), noMatch.content)
    }

    /** Blank queries show the selected collection; matching ignores surrounding whitespace only. */
    @Test
    fun trimsQueryAndKeepsLoadedDataWhenNothingMatches() {
        assertEquals(listOf(active), state("  plan  ").visibleTasks)
        assertEquals(listOf(active, other), state(" \t ", TaskFilter.Active).visibleTasks)
        assertTrue(state("missing").visibleTasks.isEmpty())
        assertTrue(state("missing").hasLoaded)
    }

    /** Typing, repeating, and clearing searches are synchronous state changes with no repository calls. */
    @Test
    fun searchAndClearDoNotFetchOrResetTheFilter() = runTest {
        val repository = FixtureTaskRepository(listResult = { ApiResult.Success(tasks) })
        val home = model(repository)
        runCurrent()
        home.selectFilter(TaskFilter.Completed)
        home.updateSearchQuery("delivery")
        assertEquals(listOf(completed), home.uiState.value.visibleTasks)
        val before = home.uiState.value
        home.updateSearchQuery("delivery")
        assertSame(before, home.uiState.value)
        home.updateSearchQuery("missing")
        assertTrue(home.uiState.value.visibleTasks.isEmpty())
        home.updateSearchQuery("")
        runCurrent()
        assertEquals(listOf(completed), home.uiState.value.visibleTasks)
        assertEquals(TaskFilter.Completed, home.uiState.value.filter)
        assertEquals(1, repository.listCalls)
    }

    /** Refresh preserves current search/filter values, including query edits made during the request. */
    @Test
    fun refreshKeepsSearchAndFiltersWithFreshResults() = runTest {
        var response: suspend () -> ApiResult<List<Task>> = { ApiResult.Success(tasks) }
        val repository = FixtureTaskRepository(listResult = { response() })
        val home = model(repository)
        runCurrent()
        home.updateSearchQuery("plan")
        home.selectFilter(TaskFilter.Active)
        val pending = CompletableDeferred<ApiResult<List<Task>>>()
        response = { pending.await() }
        home.refresh()
        runCurrent()
        assertEquals("plan", home.uiState.value.searchQuery)
        home.updateSearchQuery("delivery")
        val updated = completed.copy(isCompleted = false)
        pending.complete(ApiResult.Success(listOf(updated, other)))
        runCurrent()
        assertEquals("delivery", home.uiState.value.searchQuery)
        assertEquals(TaskFilter.Active, home.uiState.value.filter)
        assertEquals(listOf(updated), home.uiState.value.visibleTasks)
        assertEquals(TaskStatistics(2, 2, 0), home.uiState.value.statistics)
        assertEquals(2, repository.listCalls)
    }

    /** Builds a loaded immutable snapshot without transport or mutable search indexes. */
    private fun state(query: String, filter: TaskFilter = TaskFilter.All): HomeUiState =
        HomeUiState(HomeContent.Success(tasks), filter = filter, searchQuery = query)

    /** Injects a deterministic repository using the app's existing connection boundary. */
    private fun model(repository: FixtureTaskRepository): HomeViewModel {
        val connections = InMemoryConnectionRepository({ repository }).apply { configure("https://fixture.test/") }
        return ViewModelProvider(store, HomeViewModel.factory(connections))[HomeViewModel::class.java]
    }
}
