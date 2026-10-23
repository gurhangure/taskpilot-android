package com.gurhangure.taskpilot.data.repository

import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.data.model.taskValidationErrors
import com.gurhangure.taskpilot.data.remote.CreateTaskRequestDto
import com.gurhangure.taskpilot.data.remote.TaskApi
import com.gurhangure.taskpilot.data.remote.TaskApiFactory
import com.gurhangure.taskpilot.data.remote.UpdateTaskRequestDto
import com.gurhangure.taskpilot.data.remote.ValidationProblemDto
import java.io.IOException
import java.io.InterruptedIOException
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

/** Maps transport results into application data and explicit failures without hiding cancellation. */
class RemoteTaskRepository(
    private val api: TaskApi,
    private val json: Json = TaskApiFactory.json,
) : TaskRepository {
    /** Converts the entire response atomically so corrupt tasks are never silently dropped. */
    override suspend fun list(): ApiResult<List<Task>> = execute(200, { api.list() }) { body ->
        requireNotNull(body).map { it.toTask() }
    }

    /** Shares missing-resource behavior with update and delete. */
    override suspend fun get(id: UUID): ApiResult<Task> = execute(200, { api.get(id.toString()) }) {
        requireNotNull(it).toTask()
    }

    /** Validates backend content limits before posting an incomplete task. */
    override suspend fun create(title: String, description: String?): ApiResult<Task> {
        currentCoroutineContext().ensureActive()
        validate(title, description)?.let { return ApiResult.Failure(it) }
        return execute(201, { api.create(CreateTaskRequestDto(title, description)) }) {
            requireNotNull(it).toTask()
        }
    }

    /** Sends replacement fields without trimming text or omitting null/false values. */
    override suspend fun update(
        id: UUID, title: String, description: String?, isCompleted: Boolean,
    ): ApiResult<Task> {
        currentCoroutineContext().ensureActive()
        validate(title, description)?.let { return ApiResult.Failure(it) }
        return execute(200, { api.update(id.toString(), UpdateTaskRequestDto(title, description, isCompleted)) }) {
            requireNotNull(it).toTask()
        }
    }

    /** Accepts only the documented no-content status as a confirmed deletion. */
    override suspend fun delete(id: UUID): ApiResult<Unit> = execute(204, { api.delete(id.toString()) }) { Unit }

    /** Matches .NET whitespace and UTF-16 length rules while preserving content unchanged. */
    private fun validate(title: String, description: String?): ApiError.Validation? {
        val fields = taskValidationErrors(title, description)
        return fields.takeIf { it.isNotEmpty() }?.let { ApiError.Validation(it) }
    }

    /** Keeps body parsing off the main thread and maps only expected transport/data failures. */
    private suspend fun <T, R> execute(
        expectedStatus: Int,
        request: suspend () -> Response<T>,
        mapBody: (T?) -> R,
    ): ApiResult<R> = withContext(Dispatchers.IO) {
        try {
            val response = request()
            ensureActive()
            when {
                !response.isSuccessful -> ApiResult.Failure(httpError(response))
                response.code() != expectedStatus -> ApiResult.Failure(ApiError.InvalidResponse)
                else -> ApiResult.Success(mapBody(response.body()))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SerializationException) {
            ApiResult.Failure(ApiError.InvalidResponse)
        } catch (_: DateTimeParseException) {
            ApiResult.Failure(ApiError.InvalidResponse)
        } catch (_: IllegalArgumentException) {
            ApiResult.Failure(ApiError.InvalidResponse)
        } catch (_: InterruptedIOException) {
            ensureActive()
            ApiResult.Failure(ApiError.Timeout)
        } catch (_: IOException) {
            ensureActive()
            ApiResult.Failure(ApiError.Connection)
        }
    }

    /** Extracts validation fields only; raw error bodies and server diagnostics are not retained. */
    private fun httpError(response: Response<*>): ApiError {
        val status = response.code()
        val body = response.errorBody()
        if (status != 400) {
            body?.close()
            return if (status == 404) ApiError.NotFound else ApiError.Http(status)
        }
        val fields = body?.use {
            try {
                json.decodeFromString<ValidationProblemDto>(it.string()).errors
            } catch (_: SerializationException) {
                emptyMap()
            }
        }.orEmpty()
        return ApiError.Validation(fields)
    }
}
