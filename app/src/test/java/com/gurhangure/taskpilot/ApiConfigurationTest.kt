package com.gurhangure.taskpilot

import com.gurhangure.taskpilot.data.remote.ApiConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Checks URL normalization and explicit local-HTTP opt-in independently of a live backend. */
class ApiConfigurationTest {
    /** Normalizes a deployment prefix for Retrofit relative routes without changing its port. */
    @Test
    fun normalizesHttpsDeploymentPath() {
        assertEquals("https://example.test:8443/taskpilot/", ApiConfiguration("https://example.test:8443/taskpilot").baseUrl)
        assertEquals("https://example.test/", ApiConfiguration("https://example.test").baseUrl)
    }

    /** Rejects insecure, malformed, and credential-bearing endpoints by default. */
    @Test
    fun rejectsInvalidEndpoints() {
        listOf(
            "", "example.test", "http://example.test", "https:///tasks",
            "https://user:password@example.test", "https://example.test?token=value",
            "https://example.test#fragment", "https://example.test:0",
            "https://example.test:65536", "https://bad host.test", "ftp://example.test/",
            "http://10.0.2.2:5080/",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { ApiConfiguration(url) }
        }
    }

    /** Supports the explicit emulator demo exception without allowing other HTTP hosts. */
    @Test
    fun emulatorPolicyIsExplicitAndNormalizesUserInput() {
        assertEquals("http://10.0.2.2:5087/prefix/", ApiConfiguration(
            "  http://10.0.2.2:5087/prefix  ", allowEmulatorHttp = true,
        ).baseUrl)
        listOf("http://192.168.1.20/", "http://example.test/", "http://10.0.2.2.example.test/").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { ApiConfiguration(url, allowEmulatorHttp = true) }
        }
    }

    /** Permits only the exact opted-in emulator or private LAN host, with configurable ports. */
    @Test
    fun localHttpRequiresAnExactPrivateHost() {
        listOf("10.0.2.2", "192.168.1.50", "172.16.0.2", "127.0.0.1").forEach { host ->
            val url = "http://$host:5087/"
            assertEquals(url, ApiConfiguration(url, host).baseUrl)
        }
        listOf("example.test", "8.8.8.8", "172.32.0.1", "localhost").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) { ApiConfiguration("http://$host/", host) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApiConfiguration("http://192.168.1.51/", "192.168.1.50")
        }
    }
}
