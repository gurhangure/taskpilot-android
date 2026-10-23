package com.gurhangure.taskpilot.integration

import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.data.remote.ApiConfiguration
import com.gurhangure.taskpilot.data.remote.TaskApiFactory
import com.gurhangure.taskpilot.data.repository.RemoteTaskRepository
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Exercises real Retrofit, JSON, OkHttp, and repository boundaries against an isolated server. */
class RemoteTaskRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var repository: RemoteTaskRepository
    private val id = UUID.fromString("6fd063b7-808c-4e66-9faf-dfe653ab608c")
    private val taskJson = """{"id":"$id","title":"  Keep spacing  ","description":null,"isCompleted":false,"createdAtUtc":"2026-10-22T12:00:00.1234567Z"}"""

    /** Uses the same production factory with a loopback-only test endpoint. */
    @Before
    fun setUp() {
        server = MockWebServer()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        client = TaskApiFactory.defaultClient()
        repository = repository(client)
    }

    /** Releases the test client's shared transport resources. */
    @After
    fun tearDown() {
        server.shutdown()
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }

    /** Covers all five routes, field names, null/false replacement semantics, and UTC precision. */
    @Test
    fun crudUsesTheDocumentedWireContract() = runBlocking {
        server.enqueue(jsonResponse(200, "[$taskJson]"))
        server.enqueue(jsonResponse(200, taskJson))
        server.enqueue(jsonResponse(201, taskJson))
        server.enqueue(jsonResponse(200, taskJson))
        server.enqueue(MockResponse().setResponseCode(204))

        val tasks = (repository.list() as ApiResult.Success<List<Task>>).value
        assertEquals(1, tasks.size)
        assertEquals(id, tasks.single().id)
        assertEquals(Instant.parse("2026-10-22T12:00:00.1234567Z"), tasks.single().createdAtUtc)
        assertEquals("  Keep spacing  ", tasks.single().title)
        assertEquals(tasks.single(), (repository.get(id) as ApiResult.Success<Task>).value)
        assertTrue(repository.create("  Keep spacing  ") is ApiResult.Success)
        assertTrue(repository.update(id, "  Keep spacing  ", null, false) is ApiResult.Success)
        assertEquals(ApiResult.Success(Unit), repository.delete(id))

        val requests = List(5) { requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        assertEquals(listOf("GET", "GET", "POST", "PUT", "DELETE"), requests.map { it.method })
        assertEquals(listOf("/deployment/api/tasks", "/deployment/api/tasks/$id", "/deployment/api/tasks", "/deployment/api/tasks/$id", "/deployment/api/tasks/$id"), requests.map { it.path })
        val created = TaskApiFactory.json.parseToJsonElement(requests[2].body.readUtf8()).jsonObject
        assertEquals(setOf("title", "description"), created.keys)
        assertEquals("  Keep spacing  ", created.getValue("title").jsonPrimitive.content)
        val updated = TaskApiFactory.json.parseToJsonElement(requests[3].body.readUtf8()).jsonObject
        assertEquals(JsonNull, updated["description"])
        assertEquals("false", updated.getValue("isCompleted").jsonPrimitive.content)
        assertTrue(requests[2].getHeader("Content-Type")!!.startsWith("application/json"))
    }

    /** Preserves server validation fields and distinguishes missing resources from other HTTP errors. */
    @Test
    fun mapsValidationMissingResourcesAndServerFailures() = runBlocking {
        server.enqueue(jsonResponse(400, """{"status":400,"title":"Validation failed","errors":{"Title":["Invalid title"]},"traceId":"ignored"}"""))
        assertEquals(ApiResult.Failure(ApiError.Validation(mapOf("Title" to listOf("Invalid title")))), repository.create("Valid"))
        repeat(3) { server.enqueue(MockResponse().setResponseCode(404)) }
        assertEquals(ApiResult.Failure(ApiError.NotFound), repository.get(id))
        assertEquals(ApiResult.Failure(ApiError.NotFound), repository.update(id, "Valid", null, true))
        assertEquals(ApiResult.Failure(ApiError.NotFound), repository.delete(id))
        server.enqueue(jsonResponse(503, "<html>unavailable</html>"))
        assertEquals(ApiResult.Failure(ApiError.Http(503)), repository.list())
        server.enqueue(jsonResponse(400, "not-json"))
        assertEquals(ApiResult.Failure(ApiError.Validation(emptyMap())), repository.list())
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "http://example.test/"))
        assertEquals(ApiResult.Failure(ApiError.Http(302)), repository.list())
    }

    /** Rejects malformed success bodies instead of presenting empty or partially valid data. */
    @Test
    fun rejectsUnexpectedSuccessBodies() = runBlocking {
        val invalidBodies = listOf(
            "", "null", "{}", "[", "[{\"title\":\"Missing fields\"}]",
            "[${taskJson.replace(id.toString(), "not-a-guid")}]",
            "[${taskJson.replace("2026-10-22T12:00:00.1234567Z", "yesterday")}]",
            "[${taskJson.replace("false", "\"false\"")}]",
            "[${taskJson.replace("false", "\"true\"")}]",
        )
        invalidBodies.forEach { body ->
            server.enqueue(jsonResponse(200, body))
            assertEquals("Unexpected body: $body", ApiResult.Failure(ApiError.InvalidResponse), repository.list())
        }
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(ApiResult.Failure(ApiError.InvalidResponse), repository.list())
    }

    /** Accepts additive server fields and valid empty task collections. */
    @Test
    fun acceptsUnknownFieldsAndEmptyLists() = runBlocking {
        server.enqueue(jsonResponse(200, "[]"))
        assertEquals(ApiResult.Success(emptyList<Any>()), repository.list())
        server.enqueue(jsonResponse(200, taskJson.dropLast(1) + ",\"futureField\":true}"))
        assertTrue(repository.get(id) is ApiResult.Success)
    }

    /** Enforces .NET whitespace and UTF-16 bounds without issuing invalid writes. */
    @Test
    fun validatesContentBeforeSendingAndAcceptsBoundaries() = runBlocking {
        listOf("", " \t\n", "\u0085", "\u00A0", "x".repeat(201), "😀".repeat(101)).forEach { title ->
            val result = repository.create(title) as ApiResult.Failure
            assertTrue((result.error as ApiError.Validation).fields.containsKey("Title"))
        }
        assertTrue((repository.update(id, "Valid", "d".repeat(2001), false) as ApiResult.Failure).error is ApiError.Validation)
        assertEquals(0, server.requestCount)
        server.enqueue(jsonResponse(201, taskJson))
        assertTrue(repository.create("😀".repeat(100), "d".repeat(2000)) is ApiResult.Success)
        server.enqueue(jsonResponse(201, taskJson))
        assertTrue(repository.create("\u001C") is ApiResult.Success)
    }

    /** Maps a deterministic DNS failure and a bounded stalled response separately. */
    @Test
    fun distinguishesConnectionFailuresAndTimeouts() = runBlocking {
        val failingClient = client.newBuilder().dns(object : Dns {
            /** Fails name resolution deterministically without contacting external DNS. */
            override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException("fixture")
        }).build()
        val failing = RemoteTaskRepository(TaskApiFactory(failingClient).create(ApiConfiguration("https://example.test/")))
        assertEquals(ApiResult.Failure(ApiError.Connection), failing.list())
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val timed = repository(client.newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build())
        withTimeout(5_000) {
            assertEquals(ApiResult.Failure(ApiError.Timeout), timed.list())
        }
    }

    /** Cancelling the coroutine cancels the OkHttp call rather than returning a network failure. */
    @Test
    fun cancellationReachesTheHttpCall() = runBlocking {
        val cancelled = CountDownLatch(1)
        val observedClient = client.newBuilder().eventListener(object : EventListener() {
            /** Records transport cancellation without logging request data. */
            override fun canceled(call: Call) { cancelled.countDown() }
        }).build()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val call = async { repository(observedClient).list() }
        val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
        assertNotNull(request)
        call.cancelAndJoin()
        assertTrue(call.isCancelled)
        assertTrue(withContext(Dispatchers.IO) { cancelled.await(2, TimeUnit.SECONDS) })
        assertFalse(call.isActive)
    }

    /** Reconfiguration directs subsequent probes to the new port rather than a cached old client. */
    @Test
    fun configurationChangesRecreateTheRealApiClient() = runBlocking {
        MockWebServer().use { secondServer ->
            secondServer.start(InetAddress.getByName("127.0.0.1"), 0)
            val firstUrl = server.url("/").newBuilder().host("127.0.0.1").build()
            val secondUrl = secondServer.url("/").newBuilder().host("127.0.0.1").build()
            val factory = TaskApiFactory(client)
            val connections = InMemoryConnectionRepository(
                createRepository = { RemoteTaskRepository(factory.create(it)) },
                allowedHttpHost = firstUrl.host,
            )
            server.enqueue(jsonResponse(200, """{"status":"ok"}"""))
            server.enqueue(jsonResponse(200, "[]"))
            connections.configure(firstUrl.toString())
            assertEquals(ApiResult.Success(Unit), connections.testConnection())
            secondServer.enqueue(jsonResponse(200, """{"status":"ok"}"""))
            secondServer.enqueue(jsonResponse(200, "[]"))
            connections.configure(secondUrl.toString())
            assertEquals(ApiResult.Success(Unit), connections.testConnection())
            assertEquals(2, server.requestCount)
            assertEquals(2, secondServer.requestCount)
        }
    }

    /** Requires both documented response shapes rather than accepting an unrelated HTTP success page. */
    @Test
    fun verifiesHealthAndTaskContracts() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":"ok"}"""))
        server.enqueue(jsonResponse(200, "[]"))
        assertEquals(ApiResult.Success(Unit), repository.testConnection())
        assertEquals("/deployment/api/health", server.takeRequest(2, TimeUnit.SECONDS)?.path)
        assertEquals("/deployment/api/tasks", server.takeRequest(2, TimeUnit.SECONDS)?.path)
        listOf("<html>hello</html>", "{}", """{"status":"wrong-service"}""").forEach { body ->
            server.enqueue(jsonResponse(200, body))
            assertEquals(ApiResult.Failure(ApiError.InvalidResponse), repository.testConnection())
        }
        server.enqueue(jsonResponse(200, """{"status":"ok"}"""))
        server.enqueue(jsonResponse(200, """{"status":"ok"}"""))
        assertEquals(ApiResult.Failure(ApiError.InvalidResponse), repository.testConnection())
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(ApiResult.Failure(ApiError.NotFound), repository.testConnection())
    }

    /** Retains a path prefix to test Retrofit base-URL normalization and relative endpoints. */
    private fun repository(httpClient: OkHttpClient): RemoteTaskRepository {
        val url = server.url("/deployment/").newBuilder().host("127.0.0.1").build()
        return RemoteTaskRepository(TaskApiFactory(httpClient).create(ApiConfiguration(url.toString(), url.host)))
    }

    /** Supplies a JSON response with an explicit HTTP status and media type. */
    private fun jsonResponse(status: Int, body: String): MockResponse = MockResponse()
        .setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
}
