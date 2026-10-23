package com.gurhangure.taskpilot.feature.home

import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.Task

/** Selects tasks locally without issuing another API request. */
enum class TaskFilter { All, Active, Completed }

/** Represents the first-load outcome independently of refresh progress. */
sealed interface HomeContent {
    /** The first request is still running. */
    data object Loading : HomeContent

    /** The API returned an empty task list. */
    data object Empty : HomeContent

    /** The API returned server-confirmed task snapshots. */
    data class Success(val tasks: List<Task>) : HomeContent

    /** The list could not be loaded or no valid endpoint is configured. */
    data class Error(val error: ApiError) : HomeContent
}

/** Counts the entire retrieved collection, independently of the selected filter. */
data class TaskStatistics(val total: Int, val active: Int, val completed: Int)

/** Supplies all screen content and derived values through immutable snapshots. */
data class HomeUiState(
    val content: HomeContent = HomeContent.Loading,
    val filter: TaskFilter = TaskFilter.All,
    val isRefreshing: Boolean = false,
    val refreshError: ApiError? = null,
) {
    val isLoading: Boolean get() = content == HomeContent.Loading || isRefreshing
    val hasLoaded: Boolean get() = content is HomeContent.Success || content == HomeContent.Empty

    private val tasks: List<Task> get() = (content as? HomeContent.Success)?.tasks.orEmpty()

    val statistics: TaskStatistics?
        get() = if (hasLoaded) {
            val completed = tasks.count { it.isCompleted }
            TaskStatistics(tasks.size, tasks.size - completed, completed)
        } else null

    val visibleTasks: List<Task>
        get() = when (filter) {
            TaskFilter.All -> tasks
            TaskFilter.Active -> tasks.filterNot { it.isCompleted }
            TaskFilter.Completed -> tasks.filter { it.isCompleted }
        }
}
