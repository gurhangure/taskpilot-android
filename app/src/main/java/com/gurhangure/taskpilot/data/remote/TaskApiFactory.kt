package com.gurhangure.taskpilot.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Shares HTTP resources while creating endpoint-specific Retrofit services. */
class TaskApiFactory(private val client: OkHttpClient = defaultClient()) {
    /** Creates a service without making requests; relative routes preserve deployment prefixes. */
    fun create(configuration: ApiConfiguration): TaskApi = Retrofit.Builder()
        .baseUrl(configuration.baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TaskApi::class.java)

    /** Defines shared serialization and bounded transport defaults. */
    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            // Explicit nulls remain enabled so PUT can clear an existing description.
            isLenient = false
            coerceInputValues = false
        }

        /** Uses normal TLS validation and avoids automatic retries, redirects, and body logging. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
