package com.gurhangure.taskpilot.data.repository

import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.remote.ApiConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Describes connection setup and explicit probes without storing fetched tasks. */
sealed interface ConnectionState {
    /** No endpoint has been selected. */
    data object Unconfigured : ConnectionState

    /** An endpoint is configured but connectivity has not been confirmed. */
    data object Configured : ConnectionState

    /** A caller-requested connection probe is in progress. */
    data object Checking : ConnectionState

    /** The configured endpoint returned a valid task-list response. */
    data object Connected : ConnectionState

    /** Configuration or the latest connection probe failed. */
    data class Failed(val error: ApiError) : ConnectionState
}

/** Owns endpoint selection and connection probes independently of Compose or HTTP details. */
interface ConnectionRepository {
    val state: StateFlow<ConnectionState>

    /** Returns the current normalized configuration, or null before setup. */
    fun configuration(): ApiConfiguration?

    /** Validates and replaces the client; invalid input preserves the current configuration. */
    fun configure(baseUrl: String): ApiResult<Unit>

    /** Returns the current repository snapshot; callers should reacquire after reconfiguration. */
    fun taskRepository(): TaskRepository?

    /** Probes GET /api/tasks and reports state without retaining task data. */
    suspend fun testConnection(): ApiResult<Unit>
}

/** Keeps configuration in memory and rejects stale probe results after endpoint changes. */
class InMemoryConnectionRepository(
    private val createRepository: (ApiConfiguration) -> TaskRepository,
    private val allowedHttpHost: String? = null,
) : ConnectionRepository {
    private val lock = Any()
    private var currentConfiguration: ApiConfiguration? = null
    private var currentRepository: TaskRepository? = null
    private var revision = 0L
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Unconfigured)
    override val state: StateFlow<ConnectionState> = mutableState.asStateFlow()

    /** Returns a consistent endpoint snapshot. */
    override fun configuration(): ApiConfiguration? = synchronized(lock) { currentConfiguration }

    /** Rebuilds the API repository only after URL validation succeeds. */
    override fun configure(baseUrl: String): ApiResult<Unit> = synchronized(lock) {
        val configuration = try {
            ApiConfiguration(baseUrl, allowedHttpHost)
        } catch (_: IllegalArgumentException) {
            revision++
            mutableState.value = ConnectionState.Failed(ApiError.InvalidUrl)
            return@synchronized ApiResult.Failure(ApiError.InvalidUrl)
        }
        val repository = createRepository(configuration)
        currentConfiguration = configuration
        currentRepository = repository
        revision++
        mutableState.value = ConnectionState.Configured
        ApiResult.Success(Unit)
    }

    /** Supplies the repository for the endpoint selected at the moment of this call. */
    override fun taskRepository(): TaskRepository? = synchronized(lock) { currentRepository }

    /** Only the newest probe may publish state; cancellation never becomes a connection failure. */
    override suspend fun testConnection(): ApiResult<Unit> {
        currentCoroutineContext().ensureActive()
        val (repository, probeRevision) = synchronized(lock) {
            val repository = currentRepository ?: return ApiResult.Failure(ApiError.NotConfigured)
            revision++
            mutableState.value = ConnectionState.Checking
            repository to revision
        }
        try {
            val result = when (val tasks = repository.list()) {
                is ApiResult.Success -> ApiResult.Success(Unit)
                is ApiResult.Failure -> tasks
            }
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                if (revision == probeRevision) {
                    mutableState.value = when (result) {
                        is ApiResult.Success -> ConnectionState.Connected
                        is ApiResult.Failure -> ConnectionState.Failed(result.error)
                    }
                }
            }
            return result
        } catch (cancelled: CancellationException) {
            synchronized(lock) {
                if (revision == probeRevision) mutableState.value = ConnectionState.Configured
            }
            throw cancelled
        }
    }
}
