package com.gurhangure.taskpilot.component

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.feature.home.HomeContent
import com.gurhangure.taskpilot.feature.home.HomeScreen
import com.gurhangure.taskpilot.feature.home.HomeUiState
import com.gurhangure.taskpilot.feature.home.TaskDraft
import com.gurhangure.taskpilot.ui.theme.TaskPilotTheme
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Exercises compact controls and platform-inset-driven FAB behavior without backend or keyboard timing. */
class HomeRefinementTest {
    @get:Rule
    val compose = createComposeRule()
    private val task = Task(UUID(0, 1), "Review demo", "Keep the controls reachable", false, Instant.EPOCH)

    /** IME visibility, even without an occluding inset, hides the FAB and closing it restores creation. */
    @Test
    fun fabTracksAndroidImeVisibilityAndRestoresCreateAction() {
        lateinit var view: View
        var creates = 0
        compose.setContent {
            view = LocalView.current
            TaskPilotTheme {
                HomeScreen(HomeUiState(HomeContent.Success(listOf(task)), endpointAvailable = true), {}, {}, {}, onCreate = { creates++ })
            }
        }
        compose.onNodeWithContentDescription("Create Task").assertIsDisplayed().assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, creates) }
        dispatchImeVisibility(view, true)
        compose.onNodeWithContentDescription("Create Task").assertDoesNotExist()
        dispatchImeVisibility(view, false)
        compose.onNodeWithContentDescription("Create Task").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, creates) }
    }

    /** Icon-only navigation retains discoverable labels and the existing refresh/loading guard. */
    @Test
    fun headerIconsForwardActionsAndRespectLoading() {
        val state = mutableStateOf(HomeUiState(HomeContent.Empty))
        var settings = 0
        var refreshes = 0
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(state.value, { refreshes++ }, {}, {}, onOpenSettings = { settings++ })
            }
        }
        compose.onNodeWithText("TaskPilot").assertIsDisplayed()
        compose.onNodeWithContentDescription("Connection Settings").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Refresh").assertHasClickAction().performClick()
        compose.runOnIdle {
            assertEquals(1, settings)
            assertEquals(1, refreshes)
            state.value = state.value.copy(isRefreshing = true)
        }
        compose.onNodeWithContentDescription("Refresh").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Connection Settings").assertIsEnabled()
    }

    /** Card actions remain separately clickable at the end of a list while the FAB is visible. */
    @Test
    fun createFabDoesNotInterceptCardEditOrDelete() {
        val tasks = List(8) { task.copy(id = UUID(0, it.toLong() + 1), title = "Task $it") }
        val last = tasks.last()
        var creates = 0
        var edits = 0
        var deletes = 0
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(HomeUiState(HomeContent.Success(tasks), endpointAvailable = true), {}, {}, {},
                    onCreate = { creates++ }, onEdit = { edits++ }, onRequestDelete = { deletes++ })
            }
        }
        scrollTo(last.title)
        compose.onNodeWithContentDescription("Edit task: ${last.title}").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Delete task: ${last.title}").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, edits)
            assertEquals(1, deletes)
            assertEquals(0, creates)
        }
        compose.onNodeWithContentDescription("Create Task").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, creates) }
    }

    /** Long task content, filters, metrics, and form actions remain reachable in a narrow, short viewport. */
    @Test
    fun longTasksRemainEditableWithLargeTextAndLimitedSpace() {
        val longTask = task.copy(title = "A detailed task title ".repeat(8), description = "Long description with useful context. ".repeat(20))
        val state = mutableStateOf(HomeUiState(HomeContent.Success(listOf(longTask)), endpointAvailable = true))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
                TaskPilotTheme {
                    Box(Modifier.width(320.dp).height(360.dp)) {
                        HomeScreen(state.value, {}, {}, { state.value = state.value.copy(filter = it) },
                            onEdit = { state.value = state.value.copy(editor = TaskDraft(original = it)) },
                            onCancelEditor = { state.value = state.value.copy(editor = null) })
                    }
                }
            }
        }
        scrollTo("Total")
        compose.onNodeWithContentDescription("Total: 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Active: 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Completed: 0").assertIsDisplayed()
        scrollTo("All")
        compose.onNode(hasText("Active") and hasClickAction()).performClick()
        scrollTo(longTask.title)
        compose.onNodeWithText(longTask.title, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit task: ${longTask.title}").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(longTask.title, state.value.tasks.single().title) }
    }

    /** Sends controlled Android visibility flags to the same view observed by Compose WindowInsets. */
    private fun dispatchImeVisibility(view: View, visible: Boolean) {
        compose.runOnIdle {
            val insets = WindowInsetsCompat.Builder(ViewCompat.getRootWindowInsets(view) ?: WindowInsetsCompat.CONSUMED)
                .setVisible(WindowInsetsCompat.Type.ime(), visible)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        compose.waitForIdle()
    }

    /** Materializes list content without depending on device pixel coordinates. */
    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }
}
