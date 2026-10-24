package com.gurhangure.taskpilot.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.feature.home.HomeContent
import com.gurhangure.taskpilot.feature.home.HomeScreen
import com.gurhangure.taskpilot.feature.home.HomeUiState
import com.gurhangure.taskpilot.feature.home.TaskDraft
import com.gurhangure.taskpilot.feature.home.TaskEditorScreen
import com.gurhangure.taskpilot.ui.theme.TaskPilotTheme
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Verifies accessible CRUD controls and dialog/form behavior independently of networking. */
class TaskMutationScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val task = Task(UUID(0, 1), "Review demo", "Notes", false, Instant.EPOCH)

    /** The reusable form forwards content and Save/Cancel actions with keyboard handling. */
    @Test
    fun formEditsContentAndForwardsActions() {
        val draft = mutableStateOf(TaskDraft())
        var saves = 0
        var cancels = 0
        compose.setContent {
            TaskPilotTheme {
                TaskEditorScreen(draft.value, false,
                    { draft.value = draft.value.copy(title = it) },
                    { draft.value = draft.value.copy(description = it) }, { saves++ }, { cancels++ }, {})
            }
        }
        compose.onNodeWithText("Title").performTextReplacement("Write demo")
        compose.onNodeWithText("Description (optional)").performTextReplacement("Details")
        compose.onNodeWithText("Description (optional)").performImeAction()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Write demo", draft.value.title)
            assertEquals("Details", draft.value.description)
            assertEquals(1, saves)
            assertEquals(1, cancels)
        }
    }

    /** Server field validation appears next to retained input and general recovery guidance. */
    @Test
    fun formShowsValidationAndRetainsInput() {
        compose.setContent {
            TaskPilotTheme {
                TaskEditorScreen(TaskDraft(title = "Rejected title", error = ApiError.Validation(mapOf("Title" to listOf("Title rejected")))),
                    false, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Rejected title").assertIsDisplayed()
        compose.onNodeWithText("Title rejected").assertIsDisplayed()
        compose.onNodeWithText("Check the highlighted fields and try again.").performScrollTo().assertIsDisplayed()
    }

    /** Saving blocks duplicate submissions and edits without hiding the draft. */
    @Test
    fun pendingEditDisablesConflictingActions() {
        compose.setContent {
            TaskPilotTheme {
                TaskEditorScreen(TaskDraft(original = task), true, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Title").assertIsNotEnabled()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Delete").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Saving task…").performScrollTo().assertIsDisplayed()
    }

    /** List actions identify their task and forward completion, edit, delete, and creation events. */
    @Test
    fun taskCardsExposeLabelledCrudActions() {
        var creates = 0
        var edits = 0
        var toggles = 0
        var deletes = 0
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(HomeUiState(HomeContent.Success(listOf(task)), endpointAvailable = true), {}, {}, {},
                    onCreate = { creates++ }, onEdit = { edits++ }, onToggle = { toggles++ }, onRequestDelete = { deletes++ })
            }
        }
        compose.onNodeWithText("Create Task").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(task.title))
        compose.onNodeWithContentDescription("Complete task: Review demo").performClick()
        compose.onNodeWithContentDescription("Edit task: Review demo").performClick()
        compose.onNodeWithContentDescription("Delete task: Review demo").performClick()
        compose.runOnIdle {
            assertEquals(1, creates)
            assertEquals(1, edits)
            assertEquals(1, toggles)
            assertEquals(1, deletes)
        }
    }

    /** Confirmation names the task; cancellation cannot be confused with destructive confirmation. */
    @Test
    fun deleteDialogRequiresExplicitActionAndSupportsCancel() {
        var confirms = 0
        var cancels = 0
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(HomeUiState(HomeContent.Success(listOf(task)), deleteTask = task), {}, {}, {},
                    onConfirmDelete = { confirms++ }, onCancelDelete = { cancels++ })
            }
        }
        compose.onNodeWithText("Delete “Review demo”? This cannot be undone.").assertIsDisplayed()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(0, confirms); assertEquals(1, cancels) }
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(1, confirms) }
    }

    /** Pending deletion disables both dialog actions and presents progress. */
    @Test
    fun pendingDeleteCannotBeSubmittedTwice() {
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(HomeUiState(HomeContent.Success(listOf(task)), deleteTask = task, isMutating = true), {}, {}, {})
            }
        }
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        compose.onNodeWithText("Deleting task…").assertIsDisplayed()
    }

    /** Retry reflects the same mutation guard as Refresh rather than appearing actionable during a write. */
    @Test
    fun retryIsDisabledWhileAMutationIsPending() {
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(HomeUiState(HomeContent.Success(listOf(task)), isMutating = true,
                    pendingTaskId = task.id, refreshError = ApiError.Timeout), {}, {}, {})
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Retry"))
        compose.onNodeWithText("Retry").assertIsNotEnabled()
    }

    /** A failed deletion remains actionable and clearly distinguishes a missing task from bad configuration. */
    @Test
    fun deleteFailureKeepsConfirmationAndRecoveryMessage() {
        compose.setContent {
            TaskPilotTheme {
                HomeScreen(HomeUiState(HomeContent.Success(listOf(task)), deleteTask = task, mutationError = ApiError.NotFound), {}, {}, {})
            }
        }
        compose.onNode(hasText("This task no longer exists. Cancel and refresh the list.") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }
}
