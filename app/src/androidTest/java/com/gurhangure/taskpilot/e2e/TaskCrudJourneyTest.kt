package com.gurhangure.taskpilot.e2e

import androidx.compose.ui.test.assertIsDisplayed
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
import com.gurhangure.taskpilot.TaskPilotContent
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import com.gurhangure.taskpilot.data.repository.TaskRepository
import com.gurhangure.taskpilot.ui.theme.TaskPilotTheme
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs the actual app composition and ViewModels on an emulator with a deterministic repository fixture. */
class TaskCrudJourneyTest {
    @get:Rule
    val compose = createComposeRule()

    /** Creates, edits, completes, reopens, cancels deletion, and confirms deletion through the UI. */
    @Test
    fun managesTaskThroughServerConfirmedScreens() {
        val repository = JourneyRepository()
        val connections = InMemoryConnectionRepository({ repository }).apply { configure("https://fixture.test/") }
        compose.setContent { TaskPilotTheme { TaskPilotContent(connections) } }
        compose.onNodeWithContentDescription("Create Task").performClick()
        compose.onNodeWithText("Title").performTextReplacement("Prepare demo")
        compose.onNodeWithText("Description (optional)").performTextReplacement("Initial notes")
        compose.onNodeWithText("Description (optional)").performImeAction()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Total: 1").assertIsDisplayed()
        scrollTo("Search tasks")
        compose.onNodeWithText("Search tasks").performTextReplacement("NOTES")
        compose.onNodeWithText("Search tasks").performImeAction()
        scrollTo("Prepare demo")
        compose.onNodeWithContentDescription("Edit task: Prepare demo").performClick()
        compose.onNodeWithText("Initial notes").assertIsDisplayed()
        compose.onNodeWithText("Title").performTextReplacement("Polish demo")
        compose.onNodeWithText("Description (optional)").performTextReplacement("Final notes")
        compose.onNodeWithText("Description (optional)").performImeAction()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        scrollTo("Search tasks")
        compose.onNodeWithText("NOTES").assertIsDisplayed()
        scrollTo("Polish demo")
        compose.onNodeWithContentDescription("Complete task: Polish demo").performClick()
        scrollTo("Total")
        compose.onNodeWithContentDescription("Completed: 1").assertIsDisplayed()
        scrollTo("Polish demo")
        compose.onNodeWithContentDescription("Reopen task: Polish demo").performClick()
        scrollTo("Total")
        compose.onNodeWithContentDescription("Active: 1").assertIsDisplayed()
        scrollTo("Polish demo")
        compose.onNodeWithContentDescription("Delete task: Polish demo").performClick()
        compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(0, repository.deleteCalls) }
        compose.onNodeWithContentDescription("Delete task: Polish demo").performClick()
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        scrollTo("No tasks yet")
        compose.onNodeWithText("No tasks yet").assertIsDisplayed()
        scrollTo("Total")
        compose.onNodeWithContentDescription("Total: 0").assertIsDisplayed()
        scrollTo("Search tasks")
        compose.onNodeWithText("NOTES").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, repository.deleteCalls); assertEquals(3, repository.updateCalls) }
    }

    /** Materializes list content without depending on device height. */
    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    /** Emulates the repository's confirmed-write contract without network or developer data. */
    private class JourneyRepository : TaskRepository {
        private var tasks = emptyList<Task>()
        var deleteCalls = 0
        var updateCalls = 0

        /** Returns an isolated copy of current fixture tasks. */
        override suspend fun list(): ApiResult<List<Task>> = ApiResult.Success(tasks.toList())

        /** Resolves existing fixture identities only. */
        override suspend fun get(id: UUID): ApiResult<Task> = tasks.find { it.id == id }?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(ApiError.NotFound)

        /** Assigns server-owned identity and creation time with an initially active status. */
        override suspend fun create(title: String, description: String?): ApiResult<Task> {
            val task = Task(UUID(0, 1), title, description, false, Instant.EPOCH)
            tasks = tasks + task
            return ApiResult.Success(task)
        }

        /** Replaces editable fields while retaining identity and creation time. */
        override suspend fun update(id: UUID, title: String, description: String?, isCompleted: Boolean): ApiResult<Task> {
            val task = tasks.first { it.id == id }.copy(title = title, description = description, isCompleted = isCompleted)
            tasks = tasks.map { if (it.id == id) task else it }
            updateCalls++
            return ApiResult.Success(task)
        }

        /** Removes the confirmed identity and records explicit deletion only. */
        override suspend fun delete(id: UUID): ApiResult<Unit> {
            tasks = tasks.filterNot { it.id == id }
            deleteCalls++
            return ApiResult.Success(Unit)
        }
    }
}
