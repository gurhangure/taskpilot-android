package com.gurhangure.taskpilot.data.settings

/** Stores only a validated, non-secret server URL independently of Android UI. */
interface EndpointSettings {
    /** Returns the saved URL, or null on a fresh installation; storage errors propagate. */
    suspend fun read(): String?

    /** Atomically persists a normalized URL; failures must leave the previous value intact. */
    suspend fun write(baseUrl: String)
}
