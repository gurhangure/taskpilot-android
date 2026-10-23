package com.gurhangure.taskpilot

import com.gurhangure.taskpilot.data.remote.ApiConfiguration
import com.gurhangure.taskpilot.data.repository.InMemoryConnectionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Guards endpoint validation and explicit connection configuration without a server. */
class ApiConfigurationTest {
    /** Allows secure deployment prefixes and preserves caller-supplied configuration. */
    @Test
    fun preservesHttpsDeploymentPath() {
        val url = "https://example.test:8443/taskpilot/"
        val configuration = ApiConfiguration(url)
        assertEquals(url, configuration.baseUrl)
        assertEquals(configuration, InMemoryConnectionRepository(configuration).configuration())
    }

    /** Leaves startup independent of any assumed backend address. */
    @Test
    fun startsWithoutAnEndpoint() {
        assertNull(InMemoryConnectionRepository().configuration())
    }

    /** Rejects insecure, malformed, and credential-bearing endpoints. */
    @Test
    fun rejectsInvalidEndpoints() {
        listOf(
            "", "example.test", "http://example.test", "https:///tasks",
            "https://user:password@example.test", "https://example.test?token=value",
            "https://example.test#fragment", "https://example.test:0",
            "https://example.test:65536", "https://bad host.test",
        ).forEach { url ->
            assertThrows("Expected rejection for $url", IllegalArgumentException::class.java) {
                ApiConfiguration(url)
            }
        }
    }
}
