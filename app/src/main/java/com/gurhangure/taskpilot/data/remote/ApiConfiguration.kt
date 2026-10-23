package com.gurhangure.taskpilot.data.remote

import java.net.URI

/** Validates an explicit HTTPS endpoint without making requests or weakening TLS. */
data class ApiConfiguration(val baseUrl: String) {
    init {
        val uri = try {
            URI(baseUrl)
        } catch (exception: java.net.URISyntaxException) {
            throw IllegalArgumentException("Enter a valid HTTPS API base URL.", exception)
        }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "API base URL must use HTTPS and include a host."
        }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "API base URL must not contain credentials, a query, or a fragment."
        }
        require(uri.port == -1 || uri.port in 1..65535) { "API port must be between 1 and 65535." }
    }
}
