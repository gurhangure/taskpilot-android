package com.gurhangure.taskpilot.data.repository

import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.remote.ApiConfiguration
import com.gurhangure.taskpilot.data.settings.EndpointSettings
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Describes connection setup and explicit probes without storing fetched tasks. */
sealed interface ConnectionState {
    /** No endpoint has been selected. */
    data object Unconfigured : ConnectionState

    /** An endpoint is configured but connectivity has not been confirmed. */
    data object Configured : ConnectionState

    /** A caller-requested connection probe is in progress. */
    data object Checking : ConnectionState

    /** The configured endpoint returned a valid API probe response. */
    data object Connected : ConnectionState

    /** Configuration or the latest connection probe failed. */
    data class Failed(val error: ApiError) : ConnectionState
}

/** Owns endpoint selection and connection probes independently of Compose or HTTP details. */
interface ConnectionRepository {
    val state: StateFlow<ConnectionState>
    val endpoint: StateFlow<String?>
    val initialized: StateFlow<Boolean>

    /** Restores saved settings before considering an optional non-persistent build fallback. */
    suspend fun restore(defaultUrl: String = "")

    /** Validates a draft without changing the selected endpoint. */
    fun validate(baseUrl: String): ApiResult<ApiConfiguration>

    /** Persists and applies a valid URL; a failed write preserves the last selected endpoint. */
    suspend fun save(baseUrl: String): ApiResult<Unit>

    /** Checks an unsaved draft without changing persisted or active configuration. */
    suspend fun probe(baseUrl: String): ApiResult<Unit>

    /** Returns the current normalized configuration, or null before setup. */
    fun configuration(): ApiConfiguration?

    /** Validates and replaces the client; invalid input preserves the current configuration. */
    fun configure(baseUrl: String): ApiResult<Unit>

    /** Returns the current repository snapshot; callers should reacquire after reconfiguration. */
    fun taskRepository(): TaskRepository?

    /** Probes the configured API and reports state without retaining task data. */
    suspend fun testConnection(): ApiResult<Unit>
}

/** Owns active clients, optional durable endpoint settings, and versioned connection probes. */
class InMemoryConnectionRepository(
    private val createRepository: (ApiConfiguration) -> TaskRepository,
    private val allowedHttpHost: String? = null,
    private val allowEmulatorHttp: Boolean = false,
    private val settings: EndpointSettings? = null,
) : ConnectionRepository {
    private val lock = Any()
    private val persistenceMutex = Mutex()
    private var restored = false
    private val mutableEndpoint = MutableStateFlow<String?>(null)
    override val endpoint: StateFlow<String?> = mutableEndpoint.asStateFlow()
    private val mutableInitialized = MutableStateFlow(settings == null)
    override val initialized: StateFlow<Boolean> = mutableInitialized.asStateFlow()
    private var currentConfiguration: ApiConfiguration? = null
    private var currentRepository: TaskRepository? = null
    private var revision = 0L
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Unconfigured)
    override val state: StateFlow<ConnectionState> = mutableState.asStateFlow()

    /** Reads persisted configuration once; read failures never fall back to a different server. */
    override suspend fun restore(defaultUrl: String): Unit = persistenceMutex.withLock {
        if (restored) return@withLock
        try {
            val saved = settings?.read()
            val url = saved ?: defaultUrl.takeIf { it.isNotBlank() }
            if (url != null) configure(url)
        } catch (_: IOException) {
            mutableState.value = ConnectionState.Failed(ApiError.Storage)
        } finally {
            restored = true
            mutableInitialized.value = true
        }
    }

    /** Applies the exact transport policy without making requests or retaining invalid input. */
    override fun validate(baseUrl: String): ApiResult<ApiConfiguration> = try {
        ApiResult.Success(ApiConfiguration(baseUrl, allowedHttpHost, allowEmulatorHttp))
    } catch (_: IllegalArgumentException) {
        ApiResult.Failure(ApiError.InvalidUrl)
    }

    /** Serializes saves with restoration and publishes the new endpoint only after disk success. */
    override suspend fun save(baseUrl: String): ApiResult<Unit> {
        initialized.filter { it }.first()
        val validated = validate(baseUrl)
        if (validated is ApiResult.Failure) return validated
        val configuration = (validated as ApiResult.Success<ApiConfiguration>).value
        return persistenceMutex.withLock {
            currentCoroutineContext().ensureActive()
            try {
                settings?.write(configuration.baseUrl)
                currentCoroutineContext().ensureActive()
                configure(configuration.baseUrl)
            } catch (_: IOException) {
                ApiResult.Failure(ApiError.Storage)
            }
        }
    }

    /** Reuses the active repository when possible; unsaved drafts never replace it. */
    override suspend fun probe(baseUrl: String): ApiResult<Unit> {
        val validated = validate(baseUrl)
        if (validated is ApiResult.Failure) return validated
        val configuration = (validated as ApiResult.Success<ApiConfiguration>).value
        val repository = synchronized(lock) {
            if (currentConfiguration?.baseUrl == configuration.baseUrl) currentRepository else null
        } ?: createRepository(configuration)
        return repository.testConnection()
    }

    /** Returns a consistent endpoint snapshot. */
    override fun configuration(): ApiConfiguration? = synchronized(lock) { currentConfiguration }

    /** Rebuilds the API repository only after URL validation succeeds. */
    override fun configure(baseUrl: String): ApiResult<Unit> = synchronized(lock) {
        val configuration = try {
            ApiConfiguration(baseUrl, allowedHttpHost, allowEmulatorHttp)
        } catch (_: IllegalArgumentException) {
            revision++
            mutableState.value = ConnectionState.Failed(ApiError.InvalidUrl)
            return@synchronized ApiResult.Failure(ApiError.InvalidUrl)
        }
        if (currentConfiguration?.baseUrl == configuration.baseUrl && currentRepository != null) {
            return@synchronized ApiResult.Success(Unit)
        }
        val repository = createRepository(configuration)
        currentConfiguration = configuration
        currentRepository = repository
        revision++
        mutableState.value = ConnectionState.Configured
        mutableEndpoint.value = configuration.baseUrl
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
            val result = repository.testConnection()
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
