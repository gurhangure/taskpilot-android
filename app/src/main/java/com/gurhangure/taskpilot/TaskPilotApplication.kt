package com.gurhangure.taskpilot

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.gurhangure.taskpilot.data.remote.TaskApiFactory
import com.gurhangure.taskpilot.data.repository.ConnectionRepository
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import com.gurhangure.taskpilot.data.repository.RemoteTaskRepository
import com.gurhangure.taskpilot.data.settings.DataStoreEndpointSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val Context.connectionDataStore by preferencesDataStore(name = "connection")

/** Assembles process-scoped transport and a single persistent endpoint store. */
class TaskPilotApplication : Application() {
    lateinit var connections: ConnectionRepository
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Restores saved user settings asynchronously before allowing any task request. */
    override fun onCreate() {
        super.onCreate()
        val apiFactory = TaskApiFactory()
        connections = InMemoryConnectionRepository(
            createRepository = { RemoteTaskRepository(apiFactory.create(it)) },
            allowedHttpHost = BuildConfig.DEBUG_HTTP_HOST.takeIf { BuildConfig.DEBUG && it.isNotEmpty() },
            allowEmulatorHttp = true,
            settings = DataStoreEndpointSettings(connectionDataStore),
        )
        applicationScope.launch { connections.restore(BuildConfig.API_BASE_URL) }
    }
}
