package com.gurhangure.taskpilot

import android.app.Application
import com.gurhangure.taskpilot.data.remote.TaskApiFactory
import com.gurhangure.taskpilot.data.repository.ConnectionRepository
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import com.gurhangure.taskpilot.data.repository.RemoteTaskRepository

/** Assembles application-scoped networking dependencies without making startup requests. */
class TaskPilotApplication : Application() {
    lateinit var connections: ConnectionRepository
        private set

    /** Applies non-secret build configuration and the debug-only HTTP host policy. */
    override fun onCreate() {
        super.onCreate()
        val apiFactory = TaskApiFactory()
        connections = InMemoryConnectionRepository(
            createRepository = { RemoteTaskRepository(apiFactory.create(it)) },
            allowedHttpHost = BuildConfig.DEBUG_HTTP_HOST.takeIf { BuildConfig.DEBUG && it.isNotEmpty() },
        )
        if (BuildConfig.API_BASE_URL.isNotEmpty()) connections.configure(BuildConfig.API_BASE_URL)
    }
}
