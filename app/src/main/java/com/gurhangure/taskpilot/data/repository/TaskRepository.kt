package com.gurhangure.taskpilot.data.repository

import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.model.Task
import java.util.UUID

/** Provides asynchronous task operations without exposing HTTP or serialization types. */
interface TaskRepository {
    /** Returns tasks in the backend's stable order. */
    suspend fun list(): ApiResult<List<Task>>

    /** Returns a task or a NotFound failure. */
    suspend fun get(id: UUID): ApiResult<Task>

    /** Creates a task without trimming caller-supplied content. */
    suspend fun create(title: String, description: String? = null): ApiResult<Task>

    /** Replaces editable fields, including description clearing and completion state. */
    suspend fun update(id: UUID, title: String, description: String?, isCompleted: Boolean): ApiResult<Task>

    /** Deletes an existing task; missing tasks remain a NotFound failure. */
    suspend fun delete(id: UUID): ApiResult<Unit>
}
