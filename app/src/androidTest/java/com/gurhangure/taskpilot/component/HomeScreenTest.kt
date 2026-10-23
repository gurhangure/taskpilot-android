package com.gurhangure.taskpilot.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.feature.home.HomeContent
import com.gurhangure.taskpilot.feature.home.HomeScreen
import com.gurhangure.taskpilot.feature.home.HomeUiState
import com.gurhangure.taskpilot.ui.theme.TaskPilotTheme
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises task-list rendering and action forwarding independently of networking. */
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val active = Task(UUID(0, 1), "Review notes", "Check the outline", false, Instant.EPOCH)
    private val completed = Task(UUID(0, 2), "Prepare demo", null, true, Instant.EPOCH)

    /** Initial loading is visible and refresh cannot issue a second request. */
    @Test
    fun displaysLoadingAndDisablesRefresh() {
        render(HomeUiState())
        compose.onNodeWithText("TaskPilot").assertIsDisplayed()
        compose.onNodeWithText("Refresh").assertIsNotEnabled()
        scrollTo("Loading tasks…")
        compose.onNodeWithText("Loading tasks…").assertIsDisplayed()
    }

    /** A successful empty response presents an empty state and known zero statistics. */
    @Test
    fun displaysEmptyListAndZeroCounts() {
        render(HomeUiState(HomeContent.Empty))
        compose.onNodeWithContentDescription("Total: 0").assertIsDisplayed()
        compose.onNodeWithContentDescription("Active: 0").assertIsDisplayed()
        scrollTo("No tasks yet")
        compose.onNodeWithText("No tasks yet").assertIsDisplayed()
    }

    /** Cards expose their title, optional description, and explicit completion label. */
    @Test
    fun displaysPopulatedListAndCompletionStatus() {
        render(HomeUiState(HomeContent.Success(listOf(active, completed))))
        compose.onNodeWithContentDescription("Total: 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Completed: 1").assertIsDisplayed()
        scrollTo(active.title)
        compose.onNode(hasText(active.title) and hasText("Check the outline") and hasText("Active")).assertIsDisplayed()
        scrollTo(completed.title)
        compose.onNode(hasText(completed.title) and hasText("Completed")).assertIsDisplayed()
    }

    /** Filter callbacks update selection and distinguish no matching tasks from an empty API list. */
    @Test
    fun filtersDisplayMatchingTasksAndAnExplicitNoMatchesState() {
        val state = mutableStateOf(HomeUiState(HomeContent.Success(listOf(active))))
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(state.value, {}, {}, { state.value = state.value.copy(filter = it) })
            }
        }
        compose.onNode(hasText("Completed") and hasClickAction()).performClick().assertIsSelected()
        scrollTo("No matching tasks")
        compose.onNodeWithText("No matching tasks").assertIsDisplayed()
        compose.onNodeWithText("No tasks yet").assertDoesNotExist()
        scrollTo("All")
        compose.onNode(hasText("Active") and hasClickAction()).performClick().assertIsSelected()
        scrollTo(active.title)
        compose.onNodeWithText(active.title).assertIsDisplayed()
    }

    /** Connection errors provide recovery instructions and forward retry actions. */
    @Test
    fun displaysErrorAndForwardsRetry() {
        var retries = 0
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(HomeUiState(HomeContent.Error(ApiError.Connection)), {}, { retries++ }, {})
            }
        }
        scrollTo("Unable to load tasks")
        compose.onNodeWithText("Unable to load tasks").assertIsDisplayed()
        scrollTo("Cannot reach the API. Check that the backend is running and your device can reach it, then retry.")
        compose.onNodeWithText("Cannot reach the API. Check that the backend is running and your device can reach it, then retry.").assertIsDisplayed()
        scrollTo("Retry")
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    /** API status failures are presented without raw response bodies. */
    @Test
    fun displaysHttpErrorGuidance() {
        render(HomeUiState(HomeContent.Error(ApiError.Http(503))))
        val guidance = "The API returned HTTP 503. Check the backend and retry."
        scrollTo(guidance)
        compose.onNodeWithText(guidance).assertIsDisplayed()
    }

    /** Refresh forwards its action and keeps loaded cards visible while progress is shown. */
    @Test
    fun refreshKeepsTasksAndShowsProgress() {
        var refreshes = 0
        val state = mutableStateOf(HomeUiState(HomeContent.Success(listOf(active))))
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(state.value, {
                    refreshes++
                    state.value = state.value.copy(isRefreshing = true)
                }, {}, {})
            }
        }
        compose.onNodeWithText("Refresh").performClick().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, refreshes) }
        scrollTo("Refreshing tasks…")
        compose.onNodeWithText("Refreshing tasks…").assertIsDisplayed()
        scrollTo(active.title)
        compose.onNodeWithText(active.title).assertIsDisplayed()
    }

    /** Failed refreshes show a warning alongside the previously loaded tasks. */
    @Test
    fun refreshFailureRetainsCardsAndOffersRetry() {
        render(HomeUiState(HomeContent.Success(listOf(active)), refreshError = ApiError.Timeout))
        scrollTo("Could not refresh. Showing the last loaded tasks.")
        compose.onNodeWithText("Could not refresh. Showing the last loaded tasks.").assertIsDisplayed()
        scrollTo("Retry")
        compose.onNodeWithText("Retry").assertIsDisplayed()
        scrollTo(active.title)
        compose.onNodeWithText(active.title).assertIsDisplayed()
    }

    /** Connection guidance and retry remain reachable when system text doubles in size. */
    @Test
    fun connectionGuidanceRemainsReadableAtLargeFontScale() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                TaskPilotTheme {
                    HomeScreen(HomeUiState(HomeContent.Error(ApiError.NotConfigured)), {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("TaskPilot").assertIsDisplayed()
        scrollTo("API connection not configured. Set the backend address in the build configuration, then reopen the app.")
        compose.onNodeWithText("API connection not configured. Set the backend address in the build configuration, then reopen the app.").assertIsDisplayed()
        scrollTo("Retry")
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    /** Supplies state directly to the screen so component tests never require a backend. */
    private fun render(state: HomeUiState) {
        compose.setContent { TaskPilotTheme { HomeScreen(state, {}, {}, {}) } }
    }

    /** Scrolls the lazy list to materialize a target that may not yet be composed. */
    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }
}
