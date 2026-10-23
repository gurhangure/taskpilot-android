package com.gurhangure.taskpilot.data.remote

import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.data.model.taskValidationErrors
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.Serializable

/** Matches the API TaskResult JSON; required fields intentionally have no fallback defaults. */
@Serializable
data class TaskResponseDto(
    val id: String,
    val title: String,
    val description: String?,
    val isCompleted: Boolean,
    val createdAtUtc: String,
) {
    /** Rejects corrupt identity/content/time fields before creating an application snapshot. */
    fun toTask(): Task {
        val uuid = UUID.fromString(id)
        require(uuid.toString().equals(id, ignoreCase = true))
        require(taskValidationErrors(title, description).isEmpty())
        val createdAt = OffsetDateTime.parse(createdAtUtc)
        require(createdAt.offset == ZoneOffset.UTC)
        return Task(uuid, title, description, isCompleted, createdAt.toInstant())
    }
}

/** Matches POST input; completion, identity, and creation time remain server-owned. */
@Serializable
data class CreateTaskRequestDto(val title: String, val description: String? = null)

/** Matches PUT replacement semantics, including explicit null descriptions and false completion. */
@Serializable
data class UpdateTaskRequestDto(
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
)

/** Reads only the field errors needed from ASP.NET Core ValidationProblemDetails. */
@Serializable
internal data class ValidationProblemDto(val errors: Map<String, List<String>> = emptyMap())
