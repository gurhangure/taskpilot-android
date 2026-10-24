package com.gurhangure.taskpilot.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.repository.ConnectionRepository
import com.gurhangure.taskpilot.data.repository.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Loads tasks and handles screen actions while keeping transport details outside Compose. */
class HomeViewModel(private val connections: ConnectionRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
    private var loadJob: Job? = null
    private var loadVersion = 0L

    init {
        viewModelScope.launch {
            var observedEndpoint = false
            var selectedEndpoint: String? = null
            combine(connections.initialized, connections.endpoint, connections.state) { ready, endpoint, status ->
                Triple(ready, endpoint, status)
            }
                .filter { it.first }
                .distinctUntilChanged()
                .collectLatest { (_, endpoint, status) ->
                    val endpointChanged = !observedEndpoint || selectedEndpoint != endpoint
                    if (endpointChanged) {
                        observedEndpoint = true
                        selectedEndpoint = endpoint
                        loadVersion++
                        loadJob?.cancel()
                        loadJob = null
                        mutableUiState.update { state -> state.copy(content = HomeContent.Loading, isRefreshing = false, refreshError = null) }
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
        if (!connections.initialized.value || loadJob?.isActive == true) return
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
                if (version != loadVersion || endpoint != connections.endpoint.value) return@launch
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
                if (version == loadVersion && endpoint == connections.endpoint.value) mutableUiState.update {
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

    /** Constructs the screen with the application's existing connection repository. */
    companion object {
        /** Keeps dependency assembly at the activity boundary and retains ViewModels across recreation. */
        fun factory(connections: ConnectionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(connections) }
        }
    }
}
