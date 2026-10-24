package com.gurhangure.taskpilot.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.data.model.taskValidationErrors
import com.gurhangure.taskpilot.data.repository.ConnectionRepository
import com.gurhangure.taskpilot.data.repository.ConnectionState
import com.gurhangure.taskpilot.data.repository.TaskRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads tasks and handles screen actions while keeping transport details outside Compose. */
class HomeViewModel(private val connections: ConnectionRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadVersion = 0L
    private var mutationJob: Job? = null
    private var mutationVersion = 0L
    private var selectedEndpoint: String? = null
    private var selectedRepository: TaskRepository? = null

    init {
        viewModelScope.launch {
            var observedEndpoint = false
            combine(connections.initialized, connections.endpoint, connections.state) { ready, endpoint, status ->
                Triple(ready, endpoint, status)
            }
                .filter { it.first }
                .distinctUntilChanged()
                .collectLatest { (_, endpoint, status) ->
                    val endpointChanged = !observedEndpoint || selectedEndpoint != endpoint ||
                        selectedRepository !== connections.taskRepository()
                    if (endpointChanged) {
                        observedEndpoint = true
                        selectedEndpoint = endpoint
                        selectedRepository = connections.taskRepository()
                        mutationVersion++
                        mutationJob?.cancel()
                        mutationJob = null
                        loadVersion++
                        loadJob?.cancel()
                        loadJob = null
                        mutableUiState.update { state ->
                            state.copy(
                                content = HomeContent.Loading, isRefreshing = false, refreshError = null,
                                endpointAvailable = endpoint != null, editor = null, deleteTask = null,
                                isMutating = false, pendingTaskId = null, mutationError = null,
                            )
                        }
                        refresh()
                    } else if (status == ConnectionState.Connected) {
                        // Verification may finish before or after the initial list request fails.
                        loadJob?.join()
                        val state = mutableUiState.value
                        if (state.content is HomeContent.Error || state.refreshError != null) refresh()
                    }
                }
        }
    }

    /** Fetches the current endpoint once, ignoring repeated actions while a request is active. */
    fun refresh() {
        if (!connections.initialized.value || loadJob?.isActive == true || mutableUiState.value.isMutating) return
        val version = ++loadVersion
        val endpoint = connections.endpoint.value
        val repository = connections.taskRepository()
        loadJob = viewModelScope.launch {
            val previous = mutableUiState.value
            mutableUiState.update {
                it.copy(
                    content = if (it.hasLoaded) it.content else HomeContent.Loading,
                    isRefreshing = it.hasLoaded,
                    refreshError = null,
                )
            }
            try {
                val result = repository?.list() ?: ApiResult.Failure(
                    (connections.state.value as? ConnectionState.Failed)?.error ?: ApiError.NotConfigured,
                )
                ensureActive()
                if (version != loadVersion || endpoint != connections.endpoint.value || repository !== connections.taskRepository()) return@launch
                mutableUiState.update { state ->
                    when (result) {
                        is ApiResult.Success -> state.copy(
                            content = if (result.value.isEmpty()) HomeContent.Empty
                            else HomeContent.Success(result.value.toList()),
                            isRefreshing = false,
                            refreshError = null,
                        )
                        is ApiResult.Failure -> state.copy(
                            content = if (state.hasLoaded) state.content
                            else if (result.error == ApiError.NotConfigured) HomeContent.ConnectionRequired
                            else HomeContent.Error(result.error),
                            isRefreshing = false,
                            refreshError = result.error.takeIf { state.hasLoaded },
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                if (version == loadVersion && endpoint == connections.endpoint.value && repository === connections.taskRepository()) mutableUiState.update {
                    it.copy(content = previous.content, isRefreshing = false, refreshError = previous.refreshError)
                }
                throw cancelled
            }
        }
    }

    /** Retries the latest failed load using the same duplicate-request guard as refresh. */
    fun retry() = refresh()

    /** Filters only the retrieved collection and preserves the selection across refreshes. */
    fun selectFilter(filter: TaskFilter) {
        mutableUiState.update { it.copy(filter = filter) }
    }

    /** Searches loaded titles and descriptions immediately without changing the filter or fetching data. */
    fun updateSearchQuery(query: String) {
        if (mutableUiState.value.searchQuery == query) return
        mutableUiState.update { it.copy(searchQuery = query) }
    }

    /** Opens a blank draft only for a selected server and outside another mutation. */
    fun createTask() {
        if (!canMutate()) return
        mutableUiState.update { it.copy(editor = TaskDraft(), mutationError = null) }
    }

    /** Opens the latest loaded snapshot so identity and completion survive content edits. */
    fun editTask(task: Task) {
        if (!canMutate()) return
        val current = mutableUiState.value.tasks.find { it.id == task.id } ?: return
        mutableUiState.update { it.copy(editor = TaskDraft(original = current), mutationError = null) }
    }

    /** Updates only the draft and clears obsolete validation feedback. */
    fun editTitle(title: String) {
        if (mutableUiState.value.isMutating) return
        mutableUiState.update { it.copy(editor = it.editor?.copy(title = title, error = null)) }
    }

    /** Keeps description content unchanged until a save explicitly clears or replaces it. */
    fun editDescription(description: String) {
        if (mutableUiState.value.isMutating) return
        mutableUiState.update { it.copy(editor = it.editor?.copy(description = description, error = null)) }
    }

    /** Discards an unsaved form; pending writes must resolve before dismissal. */
    fun cancelEditor() {
        if (!mutableUiState.value.isMutating) mutableUiState.update { it.copy(editor = null) }
    }

    /** Validates with the shared API rules and submits one create or replacement update. */
    fun saveTask() {
        if (!canMutate()) return
        val draft = mutableUiState.value.editor ?: return
        val description = draft.description.takeIf { it.isNotEmpty() }
        val fields = taskValidationErrors(draft.title, description)
        if (fields.isNotEmpty()) {
            mutableUiState.update { it.copy(editor = draft.copy(error = ApiError.Validation(fields))) }
            return
        }
        mutate(draft.original?.id, closeEditor = true) { repository ->
            val original = draft.original
            if (original == null) repository.create(draft.title, description)
            else repository.update(original.id, draft.title, description, original.isCompleted)
        }
    }

    /** Replaces completion using confirmed content rather than optimistic checkbox state. */
    fun toggleTask(task: Task) {
        if (!canMutate() || mutableUiState.value.editor != null || mutableUiState.value.deleteTask != null) return
        val current = mutableUiState.value.tasks.find { it.id == task.id } ?: return
        mutate(current.id) { it.update(current.id, current.title, current.description, !current.isCompleted) }
    }

    /** Selects a loaded task for explicit destructive confirmation without issuing a request. */
    fun requestDelete(task: Task) {
        if (!canMutate()) return
        val current = mutableUiState.value.tasks.find { it.id == task.id } ?: return
        mutableUiState.update { it.copy(deleteTask = current, mutationError = null) }
    }

    /** Dismisses deletion without touching server data. */
    fun cancelDelete() {
        if (!mutableUiState.value.isMutating) mutableUiState.update { it.copy(deleteTask = null, mutationError = null) }
    }

    /** Deletes only the task selected by the confirmation dialog. */
    fun confirmDelete() {
        if (!canMutate()) return
        val task = mutableUiState.value.deleteTask ?: return
        mutate(task.id, deleting = true) { repository ->
            when (val result = repository.delete(task.id)) {
                is ApiResult.Success -> ApiResult.Success(null)
                is ApiResult.Failure -> result
            }
        }
    }

    /** Requires an initialized active server and serializes conflicting write actions. */
    private fun canMutate(): Boolean = connections.initialized.value && connections.endpoint.value != null &&
        selectedEndpoint == connections.endpoint.value && selectedRepository === connections.taskRepository() &&
        mutableUiState.value.endpointAvailable && !mutableUiState.value.isMutating

    /** Applies confirmed writes, invalidates older reads, and refreshes without resetting filters. */
    private fun mutate(
        taskId: UUID?,
        closeEditor: Boolean = false,
        deleting: Boolean = false,
        request: suspend (TaskRepository) -> ApiResult<Task?>,
    ) {
        val repository = connections.taskRepository() ?: return
        val endpoint = connections.endpoint.value ?: return
        val version = ++mutationVersion
        loadVersion++
        loadJob?.cancel()
        loadJob = null
        mutableUiState.update {
            it.copy(isMutating = true, pendingTaskId = taskId, isRefreshing = false,
                mutationError = null, editor = it.editor?.copy(error = null))
        }
        mutationJob = viewModelScope.launch {
            try {
                val result = request(repository)
                ensureActive()
                if (version != mutationVersion || endpoint != connections.endpoint.value ||
                    repository !== connections.taskRepository()) return@launch
                when (result) {
                    is ApiResult.Success -> {
                        mutableUiState.update { state ->
                            val tasks = state.tasks.toMutableList()
                            if (deleting) tasks.removeAll { it.id == taskId }
                            else result.value?.let { saved ->
                                val index = tasks.indexOfFirst { it.id == saved.id }
                                if (index < 0) tasks.add(saved) else tasks[index] = saved
                            }
                            state.copy(
                                content = if (tasks.isEmpty()) HomeContent.Empty else HomeContent.Success(tasks.toList()),
                                editor = if (closeEditor || deleting) null else state.editor,
                                deleteTask = null, isMutating = false, pendingTaskId = null,
                                mutationError = null, refreshError = null, isRefreshing = true,
                            )
                        }
                        refresh()
                    }
                    is ApiResult.Failure -> {
                        mutableUiState.update {
                            it.copy(isMutating = false, pendingTaskId = null,
                                editor = if (closeEditor) it.editor?.copy(error = result.error) else it.editor,
                                mutationError = if (closeEditor) null else result.error)
                        }
                        if (!mutableUiState.value.hasLoaded) refresh()
                    }
                }
            } catch (cancelled: CancellationException) {
                if (version == mutationVersion && endpoint == connections.endpoint.value && repository === connections.taskRepository()) mutableUiState.update {
                    it.copy(isMutating = false, pendingTaskId = null)
                }
                throw cancelled
            }
        }
    }

    /** Constructs the screen with the application's existing connection repository. */
    companion object {
        /** Keeps dependency assembly at the activity boundary and retains ViewModels across recreation. */
        fun factory(connections: ConnectionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(connections) }
        }
    }
}
