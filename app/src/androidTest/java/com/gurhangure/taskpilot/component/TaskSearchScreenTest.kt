package com.gurhangure.taskpilot.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.feature.home.HomeContent
import com.gurhangure.taskpilot.feature.home.HomeScreen
import com.gurhangure.taskpilot.feature.home.HomeUiState
import com.gurhangure.taskpilot.feature.home.TaskDraft
import com.gurhangure.taskpilot.feature.home.TaskEditorScreen
import com.gurhangure.taskpilot.feature.home.TaskFilter
import com.gurhangure.taskpilot.ui.theme.TaskPilotTheme
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises search semantics, local results, and constrained-screen keyboard/form behavior. */
class TaskSearchScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val active = Task(UUID(0, 1), "İstanbul plan", "Release notes", false, Instant.EPOCH)
    private val completed = Task(UUID(0, 2), "Ship update", "İzmir delivery", true, Instant.EPOCH)
    private val tasks = listOf(active, completed)

    /** Typing searches descriptions, while the Search IME action leaves the entered query intact. */
    @Test
    fun searchFieldUpdatesDescriptionResultsAndSupportsIme() {
        val state = mutableStateOf(HomeUiState(HomeContent.Success(tasks)))
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(state.value, {}, {}, {}, onSearchChanged = { state.value = state.value.copy(searchQuery = it) })
            }
        }
        scrollTo("Search tasks")
        compose.onNodeWithText("Search tasks").performTextReplacement("izmir")
        compose.onNodeWithText("Search tasks").performImeAction()
        scrollTo(completed.title)
        compose.onNodeWithText(completed.title).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("izmir", state.value.searchQuery)
            assertEquals(listOf(completed), state.value.visibleTasks)
        }
    }

    /** Search intersects status selection; its accessible clear action restores the selected collection. */
    @Test
    fun noMatchesAndClearPreserveTheStatusFilter() {
        val state = mutableStateOf(HomeUiState(HomeContent.Success(tasks)))
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(state.value, {}, {}, { state.value = state.value.copy(filter = it) },
                    onSearchChanged = { state.value = state.value.copy(searchQuery = it) })
            }
        }
        scrollTo("All")
        compose.onNode(hasText("Completed") and hasClickAction()).performClick()
        scrollTo("Search tasks")
        compose.onNodeWithText("Search tasks").performTextReplacement("ISTANBUL")
        compose.onNodeWithText("Search tasks").performImeAction()
        scrollTo("No matching tasks")
        compose.onNodeWithText("No matching tasks").assertIsDisplayed()
        compose.onNodeWithText("No tasks yet").assertDoesNotExist()
        scrollTo("Try another search or change the status filter.")
        compose.onNodeWithText("Try another search or change the status filter.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performScrollTo().assertHasClickAction().performClick()
        compose.runOnIdle {
            assertEquals("", state.value.searchQuery)
            assertEquals(TaskFilter.Completed, state.value.filter)
        }
        scrollTo(completed.title)
        compose.onNodeWithText(completed.title).assertIsDisplayed()
        scrollTo("Total")
        compose.onNodeWithContentDescription("Total: 2").assertIsDisplayed()
    }

    /** An empty backend remains distinct from a populated collection with no matching search results. */
    @Test
    fun emptyCollectionKeepsItsFirstTaskGuidance() {
        compose.setContent {
            TaskPilotTheme { HomeScreen(HomeUiState(HomeContent.Empty, searchQuery = "missing"), {}, {}, {}) }
        }
        scrollTo("No tasks yet")
        compose.onNodeWithText("No tasks yet").assertIsDisplayed()
        compose.onNodeWithText("No matching tasks").assertDoesNotExist()
    }

    /** Search, its IME action, and clearing stay reachable in a small viewport with large text. */
    @Test
    fun searchRemainsUsableOnSmallScreensWithLargeText() {
        val state = mutableStateOf(HomeUiState(HomeContent.Success(tasks)))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
                TaskPilotTheme {
                    Box(Modifier.width(320.dp).height(420.dp)) {
                        HomeScreen(state.value, {}, {}, {}, onSearchChanged = { state.value = state.value.copy(searchQuery = it) })
                    }
                }
            }
        }
        scrollTo("Search tasks")
        compose.onNodeWithText("Search tasks").performTextReplacement("plan")
        compose.onNodeWithText("Search tasks").performImeAction()
        compose.onNodeWithContentDescription("Clear search").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("", state.value.searchQuery) }
    }

    /** Wrapped Save/Cancel controls remain reachable after entering multiline content at large text sizes. */
    @Test
    fun taskFormActionsRemainReachableInAConstrainedViewport() {
        val draft = mutableStateOf(TaskDraft(original = active))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
                TaskPilotTheme {
                    Box(Modifier.width(320.dp).height(420.dp)) {
                        TaskEditorScreen(draft.value, false, { draft.value = draft.value.copy(title = it) },
                            { draft.value = draft.value.copy(description = it) }, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Title").performScrollTo().performTextReplacement("A task")
        compose.onNodeWithText("Description (optional)").performScrollTo().performTextReplacement("Line one\nLine two")
        compose.onNodeWithText("Description (optional)").performImeAction()
        compose.onNodeWithText("Save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Delete").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    /** Materializes lazy list content independently of the emulator's viewport size. */
    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }
}
