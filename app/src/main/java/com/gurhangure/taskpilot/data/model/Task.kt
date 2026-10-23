package com.gurhangure.taskpilot.data.model

import java.time.Instant
import java.util.UUID

/** Server-confirmed task snapshot independent of Retrofit and JSON serialization. */
data class Task(
    val id: UUID,
    val title: String,
    val description: String?,
    val isCompleted: Boolean,
    val createdAtUtc: Instant,
)
