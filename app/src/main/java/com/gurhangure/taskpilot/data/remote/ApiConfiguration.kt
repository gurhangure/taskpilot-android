package com.gurhangure.taskpilot.data.remote

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Validates and normalizes an endpoint; HTTP requires the emulator exception or one explicitly permitted private debug host. */
class ApiConfiguration(baseUrl: String, allowedHttpHost: String? = null, allowEmulatorHttp: Boolean = false) {
    val baseUrl: String

    init {
        val uri = try {
            URI(baseUrl.trim())
        } catch (_: java.net.URISyntaxException) {
            throw IllegalArgumentException("Enter a valid API base URL.")
        }
        require(!uri.host.isNullOrBlank()) { "API base URL must include a host." }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "API base URL must not contain credentials, a query, or a fragment."
        }
        require(uri.port == -1 || uri.port in 1..65535) { "API port must be between 1 and 65535." }
        val secure = uri.scheme.equals("https", ignoreCase = true)
        val localHttp = uri.scheme.equals("http", ignoreCase = true) &&
            ((allowEmulatorHttp && uri.host == "10.0.2.2") ||
                (uri.host == allowedHttpHost && isLocalDevelopmentHost(uri.host)))
        require(secure || localHttp) { "Use HTTPS. HTTP is supported only for the emulator or an explicitly permitted debug host." }
        val url = baseUrl.trim().toHttpUrl()
        this.baseUrl = url.newBuilder()
            .encodedPath(url.encodedPath.trimEnd('/') + "/")
            .build().toString()
    }

    /** Contains host checks without DNS resolution or implicit public-network exceptions. */
    companion object {
        /** Matches the private IPv4/loopback policy used by debug resource generation. */
        private fun isLocalDevelopmentHost(host: String): Boolean {
            val octets = host.split('.').mapNotNull { it.toIntOrNull() }
            return octets.size == 4 && octets.all { it in 0..255 } &&
                octets.joinToString(".") == host &&
                (host == "127.0.0.1" || octets[0] == 10 ||
                    (octets[0] == 172 && octets[1] in 16..31) ||
                    (octets[0] == 192 && octets[1] == 168))
        }
    }
}
