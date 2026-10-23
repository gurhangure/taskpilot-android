package com.gurhangure.taskpilot

import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.data.repository.ConnectionState
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import com.gurhangure.taskpilot.data.repository.TaskRepository
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies endpoint replacement and connection-state transitions with deterministic repositories. */
class ConnectionRepositoryTest {
    /** Configuration remains optional, and invalid changes preserve the last usable client. */
    @Test
    fun configurationIsExplicitAndInvalidInputDoesNotReplaceClient() = runTest {
        val connections = InMemoryConnectionRepository({ ProbeRepository() })
        assertNull(connections.configuration())
        assertNull(connections.taskRepository())
        assertEquals(ConnectionState.Unconfigured, connections.state.value)
        assertEquals(ApiResult.Failure(ApiError.NotConfigured), connections.testConnection())
        assertEquals(ApiResult.Success(Unit), connections.configure("https://one.test"))
        val first = connections.taskRepository()
        assertEquals("https://one.test/", connections.configuration()?.baseUrl)
        assertEquals(ApiResult.Failure(ApiError.InvalidUrl), connections.configure("http://public.test/"))
        assertSame(first, connections.taskRepository())
        assertEquals(ConnectionState.Failed(ApiError.InvalidUrl), connections.state.value)
        connections.configure("https://two.test/")
        assertNotSame(first, connections.taskRepository())
        assertEquals(ConnectionState.Configured, connections.state.value)
    }

    /** A successful probe publishes Checking before Connected, and failures remain actionable. */
    @Test
    fun connectionProbePublishesSuccessAndFailureStates() = runTest {
        val started = CompletableDeferred<Unit>()
        val outcome = CompletableDeferred<ApiResult<List<Task>>>()
        val connections = InMemoryConnectionRepository({
            ProbeRepository { started.complete(Unit); outcome.await() }
        })
        connections.configure("https://example.test/")
        val probe = async { connections.testConnection() }
        started.await()
        assertEquals(ConnectionState.Checking, connections.state.value)
        outcome.complete(ApiResult.Success(emptyList()))
        assertEquals(ApiResult.Success(Unit), probe.await())
        assertEquals(ConnectionState.Connected, connections.state.value)

        val failing = InMemoryConnectionRepository({ ProbeRepository { ApiResult.Failure(ApiError.Timeout) } })
        failing.configure("https://example.test/")
        assertEquals(ApiResult.Failure(ApiError.Timeout), failing.testConnection())
        assertEquals(ConnectionState.Failed(ApiError.Timeout), failing.state.value)
    }

    /** Old responses cannot mark a newly selected endpoint as connected. */
    @Test
    fun ignoresStaleProbeAfterEndpointChange() = runTest {
        val started = CompletableDeferred<Unit>()
        val outcome = CompletableDeferred<ApiResult<List<Task>>>()
        val connections = InMemoryConnectionRepository({ ProbeRepository { started.complete(Unit); outcome.await() } })
        connections.configure("https://old.test/")
        val probe = async { connections.testConnection() }
        started.await()
        connections.configure("https://new.test/")
        outcome.complete(ApiResult.Success(emptyList()))
        probe.await()
        assertEquals(ConnectionState.Configured, connections.state.value)
        assertEquals("https://new.test/", connections.configuration()?.baseUrl)
    }

    /** Cancelling a probe restores unverified configuration and propagates cancellation. */
    @Test
    fun cancelledProbeDoesNotBecomeANetworkFailure() = runTest {
        val started = CompletableDeferred<Unit>()
        val outcome = CompletableDeferred<ApiResult<List<Task>>>()
        val connections = InMemoryConnectionRepository({ ProbeRepository { started.complete(Unit); outcome.await() } })
        connections.configure("https://example.test/")
        val probe = async { connections.testConnection() }
        started.await()
        probe.cancelAndJoin()
        assertTrue(probe.isCancelled)
        assertEquals(ConnectionState.Configured, connections.state.value)
    }

    /** Supplies only the operation used by connection checks; unexpected mutations fail loudly. */
    private class ProbeRepository(
        private val listResult: suspend () -> ApiResult<List<Task>> = { ApiResult.Success(emptyList()) },
    ) : TaskRepository {
        /** Executes the controlled connection-probe response. */
        override suspend fun list(): ApiResult<List<Task>> = listResult()

        /** Prevents a connection probe from accidentally retrieving individual tasks. */
        override suspend fun get(id: UUID): ApiResult<Task> = error("Unexpected get")

        /** Prevents a connection probe from creating data. */
        override suspend fun create(title: String, description: String?): ApiResult<Task> = error("Unexpected create")

        /** Prevents a connection probe from changing data. */
        override suspend fun update(id: UUID, title: String, description: String?, isCompleted: Boolean): ApiResult<Task> = error("Unexpected update")

        /** Prevents a connection probe from deleting data. */
        override suspend fun delete(id: UUID): ApiResult<Unit> = error("Unexpected delete")
    }
}
