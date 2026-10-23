package com.gurhangure.taskpilot.feature.connection

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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Distinguishes draft verification from merely having a saved server URL. */
sealed interface ConnectionFeedback {
    /** The current draft has not been verified. */
    data object Disconnected : ConnectionFeedback
    /** A save or verification operation is in progress. */
    data object Connecting : ConnectionFeedback
    /** Health and task contracts matched for the current draft. */
    data object Connected : ConnectionFeedback
    /** The operation failed without deleting the saved endpoint. */
    data class Error(val error: ApiError) : ConnectionFeedback
}

/** Describes editable connection settings and the latest operation outcome. */
data class ConnectionUiState(
    val url: String = "",
    val savedUrl: String? = null,
    val restoring: Boolean = true,
    val feedback: ConnectionFeedback = ConnectionFeedback.Disconnected,
    val httpAcknowledged: Boolean = false,
) {
    val busy: Boolean get() = feedback == ConnectionFeedback.Connecting
    val usesHttp: Boolean get() = url.trim().startsWith("http://", ignoreCase = true)
    val canSubmit: Boolean get() = !restoring && !busy && url.isNotBlank() && (!usesHttp || httpAcknowledged)
}

/** Coordinates URL editing, durable saves, and cancellable verification outside Compose. */
class ConnectionViewModel(private val connections: ConnectionRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ConnectionUiState())
    val uiState = mutableState.asStateFlow()
    private var operation: Job? = null
    private var version = 0L

    init {
        viewModelScope.launch {
            connections.initialized.filter { it }.first()
            val url = connections.configuration()?.baseUrl
            mutableState.update {
                it.copy(
                    url = url.orEmpty(), savedUrl = url, restoring = false,
                    feedback = when (val status = connections.state.value) {
                        ConnectionState.Connected -> ConnectionFeedback.Connected
                        is ConnectionState.Failed -> ConnectionFeedback.Error(status.error)
                        else -> ConnectionFeedback.Disconnected
                    },
                )
            }
        }
    }

    /** Cancels obsolete work when the user edits the server address. */
    fun editUrl(url: String) {
        cancelPending()
        mutableState.update { it.copy(url = url, httpAcknowledged = false, feedback = ConnectionFeedback.Disconnected) }
    }

    /** Offers the emulator address only after an explicit user action. */
    fun useEmulatorSuggestion() = editUrl("http://10.0.2.2:5087/")

    /** Requires explicit acknowledgement before sending unencrypted HTTP requests. */
    fun acknowledgeHttp(acknowledged: Boolean) {
        cancelPending()
        mutableState.update { it.copy(httpAcknowledged = acknowledged) }
    }

    /** Verifies the editable draft without changing the selected or saved server. */
    fun testConnection() = connect(save = false)

    /** Saves a valid endpoint before probing it, retaining the choice even when the server is unavailable. */
    fun saveAndConnect() = connect(save = true)

    /** Invalidates UI results and cancels transport work when editing or leaving Settings. */
    fun cancelPending() {
        version++
        operation?.cancel()
        mutableState.update {
            if (it.busy) it.copy(feedback = ConnectionFeedback.Disconnected) else it
        }
    }

    /** Publishes results only while the originating draft and operation are still current. */
    private fun connect(save: Boolean) {
        if (!mutableState.value.canSubmit) return
        val url = mutableState.value.url
        val currentVersion = ++version
        mutableState.update { it.copy(feedback = ConnectionFeedback.Connecting) }
        operation = viewModelScope.launch {
            try {
                val saved = if (save) connections.save(url) else ApiResult.Success(Unit)
                ensureActive()
                if (currentVersion != version) return@launch
                val result = if (saved is ApiResult.Failure) saved else {
                    if (save) mutableState.update {
                        it.copy(url = connections.configuration()?.baseUrl.orEmpty(), savedUrl = connections.configuration()?.baseUrl)
                    }
                    if (save) connections.testConnection() else connections.probe(url)
                }
                ensureActive()
                if (currentVersion == version) mutableState.update {
                    it.copy(feedback = when (result) {
                        is ApiResult.Success -> ConnectionFeedback.Connected
                        is ApiResult.Failure -> ConnectionFeedback.Error(result.error)
                    })
                }
            } catch (cancelled: CancellationException) {
                if (currentVersion == version) mutableState.update { it.copy(feedback = ConnectionFeedback.Disconnected) }
                throw cancelled
            }
        }
    }

    /** Provides constructor injection through the existing Activity ViewModel store. */
    companion object {
        /** Creates Settings models without adding a dependency-injection framework. */
        fun factory(connections: ConnectionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConnectionViewModel(connections) }
        }
    }
}
