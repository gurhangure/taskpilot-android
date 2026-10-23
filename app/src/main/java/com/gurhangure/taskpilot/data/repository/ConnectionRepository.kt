package com.gurhangure.taskpilot.data.repository

import com.gurhangure.taskpilot.data.remote.ApiConfiguration

/** Read boundary for connection configuration; no endpoint is assumed at startup. */
interface ConnectionRepository {
    /** Returns the explicitly supplied configuration, or null before setup. */
    fun configuration(): ApiConfiguration?
}

/** Holds caller-supplied configuration until persistent Settings are implemented. */
class InMemoryConnectionRepository(
    private val configuration: ApiConfiguration? = null,
) : ConnectionRepository {
    /** Returns configuration without accessing a network or device storage. */
    override fun configuration(): ApiConfiguration? = configuration
}
