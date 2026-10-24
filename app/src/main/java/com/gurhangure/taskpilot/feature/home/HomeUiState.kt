package com.gurhangure.taskpilot.feature.home

import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.Task
import java.util.UUID

/** Selects tasks locally without issuing another API request. */
enum class TaskFilter { All, Active, Completed }

/** Represents the first-load outcome independently of refresh progress. */
sealed interface HomeContent {
    /** The first request is still running. */
    data object Loading : HomeContent

    /** No server has been chosen; no network request has been attempted. */
    data object ConnectionRequired : HomeContent

    /** The API returned an empty task list. */
    data object Empty : HomeContent

    /** The API returned server-confirmed task snapshots. */
    data class Success(val tasks: List<Task>) : HomeContent

    /** The list could not be loaded or no valid endpoint is configured. */
    data class Error(val error: ApiError) : HomeContent
}

/** Counts the entire retrieved collection, independently of the selected filter. */
data class TaskStatistics(val total: Int, val active: Int, val completed: Int)

/** Retains editable content and the original identity/completion snapshot until a confirmed save. */
data class TaskDraft(
    val original: Task? = null,
    val title: String = original?.title.orEmpty(),
    val description: String = original?.description.orEmpty(),
    val error: ApiError? = null,
)

/** Supplies all screen content and derived values through immutable snapshots. */
data class HomeUiState(
    val content: HomeContent = HomeContent.Loading,
    val filter: TaskFilter = TaskFilter.All,
    val isRefreshing: Boolean = false,
    val refreshError: ApiError? = null,
    val endpointAvailable: Boolean = false,
    val editor: TaskDraft? = null,
    val deleteTask: Task? = null,
    val isMutating: Boolean = false,
    val pendingTaskId: UUID? = null,
    val mutationError: ApiError? = null,
    val searchQuery: String = "",
) {
    val isLoading: Boolean get() = content == HomeContent.Loading || isRefreshing
    val hasLoaded: Boolean get() = content is HomeContent.Success || content == HomeContent.Empty

    val tasks: List<Task> get() = (content as? HomeContent.Success)?.tasks.orEmpty()

    val statistics: TaskStatistics?
        get() = if (hasLoaded) {
            val completed = tasks.count { it.isCompleted }
            TaskStatistics(tasks.size, tasks.size - completed, completed)
        } else null

    // Compute once per immutable state snapshot rather than on every Compose read.
    val visibleTasks: List<Task> = run {
        val query = searchQuery.trim()
        tasks.filter { task ->
            val matchesStatus = when (filter) {
                TaskFilter.All -> true
                TaskFilter.Active -> !task.isCompleted
                TaskFilter.Completed -> task.isCompleted
            }
            matchesStatus && (query.isEmpty() || task.title.contains(query, ignoreCase = true) ||
                task.description?.contains(query, ignoreCase = true) == true)
        }
    }
}
