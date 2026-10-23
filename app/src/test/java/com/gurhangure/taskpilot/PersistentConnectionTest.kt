package com.gurhangure.taskpilot

import com.gurhangure.taskpilot.data.model.ApiError
import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.repository.ConnectionState
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import com.gurhangure.taskpilot.helpers.FixtureTaskRepository
import com.gurhangure.taskpilot.helpers.MemoryEndpointSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies startup precedence and durable endpoint changes without a developer's server. */
class PersistentConnectionTest {
    /** A fresh installation finishes restoration without selecting or contacting a server. */
    @Test
    fun freshInstallDoesNotGuessAnEndpoint() = runTest {
        val repository = FixtureTaskRepository()
        val connections = InMemoryConnectionRepository({ repository }, settings = MemoryEndpointSettings())
        assertFalse(connections.initialized.value)
        connections.restore()
        assertTrue(connections.initialized.value)
        assertNull(connections.configuration())
        assertEquals(0, repository.listCalls + repository.probeCalls)
    }

    /** User selection wins over build defaults and survives repository recreation. */
    @Test
    fun savedEndpointWinsAndRestoresAfterRecreation() = runTest {
        val settings = MemoryEndpointSettings("https://saved.test/prefix/")
        val connections = InMemoryConnectionRepository({ FixtureTaskRepository() }, settings = settings)
        connections.restore("https://build-default.test/")
        assertEquals("https://saved.test/prefix/", connections.endpoint.value)
        assertEquals(ApiResult.Success(Unit), connections.save(" https://new.test/deployment "))
        val recreated = InMemoryConnectionRepository({ FixtureTaskRepository() }, settings = settings)
        recreated.restore("https://build-default.test/")
        assertEquals("https://new.test/deployment/", recreated.endpoint.value)
    }

    /** Explicit development defaults remain usable but do not overwrite absent user settings. */
    @Test
    fun buildFallbackIsNotPersisted() = runTest {
        val settings = MemoryEndpointSettings()
        val connections = InMemoryConnectionRepository({ FixtureTaskRepository() }, settings = settings)
        connections.restore("https://development.test/")
        assertEquals("https://development.test/", connections.endpoint.value)
        assertNull(settings.saved)
    }

    /** Invalid saves, disk errors, and failed probes never delete a saved endpoint. */
    @Test
    fun failuresPreserveSavedSettings() = runTest {
        val settings = MemoryEndpointSettings("https://saved.test/")
        val connections = InMemoryConnectionRepository({ FixtureTaskRepository(probeResult = { ApiResult.Failure(ApiError.Connection) }) }, settings = settings)
        connections.restore()
        assertEquals(ApiResult.Failure(ApiError.InvalidUrl), connections.save("https://user:secret@bad.test/"))
        settings.failWrites = true
        assertEquals(ApiResult.Failure(ApiError.Storage), connections.save("https://new.test/"))
        assertEquals("https://saved.test/", connections.endpoint.value)
        assertEquals(ApiResult.Failure(ApiError.Connection), connections.testConnection())
        assertEquals("https://saved.test/", settings.saved)
    }

    /** Failed reads are surfaced instead of silently applying a different build endpoint. */
    @Test
    fun readFailureDoesNotOverrideUserSelection() = runTest {
        val settings = MemoryEndpointSettings("https://saved.test/").apply { failReads = true }
        val connections = InMemoryConnectionRepository({ FixtureTaskRepository() }, settings = settings)
        connections.restore("https://build.test/")
        assertNull(connections.endpoint.value)
        assertEquals(ConnectionState.Failed(ApiError.Storage), connections.state.value)
        assertEquals("https://saved.test/", settings.saved)
    }

    /** Draft verification uses its own endpoint and leaves active selection untouched. */
    @Test
    fun probingADraftDoesNotSaveOrApplyIt() = runTest {
        val settings = MemoryEndpointSettings("https://saved.test/")
        val tested = mutableListOf<String>()
        val connections = InMemoryConnectionRepository({ configuration ->
            FixtureTaskRepository(probeResult = { tested += configuration.baseUrl; ApiResult.Success(Unit) })
        }, settings = settings)
        connections.restore()
        assertEquals(ApiResult.Success(Unit), connections.probe("https://draft.test/"))
        assertEquals(listOf("https://draft.test/"), tested)
        assertEquals("https://saved.test/", connections.endpoint.value)
        assertEquals("https://saved.test/", settings.saved)
    }
}
