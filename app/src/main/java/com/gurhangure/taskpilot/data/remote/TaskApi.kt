package com.gurhangure.taskpilot.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Declares the backend's task routes with cancellable Retrofit coroutine calls. */
interface TaskApi {
    /** Retrieves the documented health status without making mutations. */
    @GET("api/health")
    suspend fun health(): Response<HealthResponseDto>

    /** Retrieves server-ordered task snapshots. */
    @GET("api/tasks")
    suspend fun list(): Response<List<TaskResponseDto>>

    /** Retrieves a single task or its HTTP error response. */
    @GET("api/tasks/{id}")
    suspend fun get(@Path("id") id: String): Response<TaskResponseDto>

    /** Creates an incomplete task from caller-editable content. */
    @POST("api/tasks")
    suspend fun create(@Body request: CreateTaskRequestDto): Response<TaskResponseDto>

    /** Replaces all editable fields of an existing task. */
    @PUT("api/tasks/{id}")
    suspend fun update(@Path("id") id: String, @Body request: UpdateTaskRequestDto): Response<TaskResponseDto>

    /** Deletes a task, expecting HTTP 204 with no response body. */
    @DELETE("api/tasks/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>
}
