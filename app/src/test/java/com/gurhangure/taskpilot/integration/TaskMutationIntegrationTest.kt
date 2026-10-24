package com.gurhangure.taskpilot.integration

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.remote.ApiConfiguration
import com.gurhangure.taskpilot.data.remote.TaskApiFactory
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import com.gurhangure.taskpilot.data.repository.RemoteTaskRepository
import com.gurhangure.taskpilot.feature.home.HomeContent
import com.gurhangure.taskpilot.feature.home.HomeViewModel
import com.gurhangure.taskpilot.feature.home.TaskFilter
import com.gurhangure.taskpilot.feature.home.TaskStatistics
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies mutation-to-refresh data flow through the real Retrofit repository against controlled HTTP. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskMutationIntegrationTest {
    /** Confirmed writes survive a failed reload, then completion and deletion update filtered state. */
    @Test
    fun confirmedMutationsRefreshThroughTheRealApiBoundary() = runBlocking {
        val server = MockWebServer()
        val client = TaskApiFactory.defaultClient()
        val store = ViewModelStore()
        val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(main)
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            val url = server.url("/").newBuilder().host("127.0.0.1").build()
            val repository = RemoteTaskRepository(TaskApiFactory(client).create(ApiConfiguration(url.toString(), url.host)))
            val configured = InMemoryConnectionRepository({ repository }, allowedHttpHost = url.host).apply { configure(url.toString()) }
            server.enqueue(json(200, "[]"))
            withContext(main) {
                val home = ViewModelProvider(store, HomeViewModel.factory(configured))[HomeViewModel::class.java]
                withTimeout(10_000) {
                    home.uiState.first { it.hasLoaded }
                    home.selectFilter(TaskFilter.Active)
                    val id = UUID(0, 1)
                    val task = """{"id":"$id","title":"HTTP task","description":null,"isCompleted":false,"createdAtUtc":"2026-10-24T12:00:00Z"}"""
                    server.enqueue(json(201, task))
                    server.enqueue(MockResponse().setResponseCode(503))
                    home.createTask()
                    home.editTitle("HTTP task")
                    home.saveTask()
                    val failedReload = home.uiState.first { it.refreshError != null }
                    assertEquals(ApiError.Http(503), failedReload.refreshError)
                    assertEquals("HTTP task", failedReload.tasks.single().title)
                    assertEquals(TaskStatistics(1, 1, 0), failedReload.statistics)
                    val completed = task.replace("false", "true")
                    server.enqueue(json(200, completed))
                    server.enqueue(json(200, "[$completed]"))
                    home.toggleTask(failedReload.tasks.single())
                    val updated = home.uiState.first { !it.isMutating && !it.isRefreshing && it.tasks.singleOrNull()?.isCompleted == true }
                    assertEquals(TaskFilter.Active, updated.filter)
                    assertEquals(emptyList<Any>(), updated.visibleTasks)
                    assertEquals(TaskStatistics(1, 0, 1), updated.statistics)
                    server.enqueue(MockResponse().setResponseCode(204))
                    server.enqueue(json(200, "[]"))
                    home.requestDelete(updated.tasks.single())
                    home.confirmDelete()
                    val deleted = home.uiState.first { it.content == HomeContent.Empty && !it.isMutating && !it.isRefreshing }
                    assertEquals(TaskStatistics(0, 0, 0), deleted.statistics)
                    assertEquals(TaskFilter.Active, deleted.filter)
                    val requests = List(7) { requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
                    assertEquals(listOf("GET", "POST", "GET", "PUT", "GET", "DELETE", "GET"), requests.map { it.method })
                    assertEquals(listOf("/api/tasks", "/api/tasks", "/api/tasks", "/api/tasks/$id", "/api/tasks", "/api/tasks/$id", "/api/tasks"), requests.map { it.path })
                }
            }
        } finally {
            withContext(main) { store.clear() }
            Dispatchers.resetMain()
            main.close()
            server.shutdown()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    /** Supplies deterministic JSON bodies with the backend's documented status codes. */
    private fun json(status: Int, body: String): MockResponse = MockResponse()
        .setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
}
