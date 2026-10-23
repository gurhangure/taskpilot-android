package com.gurhangure.taskpilot.helpers

import com.gurhangure.taskpilot.data.model.ApiResult
import com.gurhangure.taskpilot.data.model.Task
import com.gurhangure.taskpilot.data.repository.TaskRepository
import com.gurhangure.taskpilot.data.settings.EndpointSettings
import java.io.IOException
import java.util.UUID

/** Models atomic settings persistence without disk or a live backend. */
class MemoryEndpointSettings(var saved: String? = null) : EndpointSettings {
    var failWrites = false
    var failReads = false

    /** Supplies the persisted value or a controlled read failure. */
    override suspend fun read(): String? {
        if (failReads) throw IOException("fixture")
        return saved
    }

    /** Preserves the previous value on a controlled storage failure. */
    override suspend fun write(baseUrl: String) {
        if (failWrites) throw IOException("fixture")
        saved = baseUrl
    }
}

/** Provides separate list and verification outcomes while rejecting unexpected mutations. */
class FixtureTaskRepository(
    private val listResult: suspend () -> ApiResult<List<Task>> = { ApiResult.Success(emptyList()) },
    private val probeResult: suspend () -> ApiResult<Unit> = { ApiResult.Success(Unit) },
) : TaskRepository {
    var listCalls = 0
    var probeCalls = 0

    /** Returns controlled list data and records fetches. */
    override suspend fun list(): ApiResult<List<Task>> { listCalls++; return listResult() }

    /** Records real verification requests independently of list loads. */
    override suspend fun testConnection(): ApiResult<Unit> { probeCalls++; return probeResult() }

    /** Rejects unexpected single-task retrieval. */
    override suspend fun get(id: UUID): ApiResult<Task> = error("Unexpected get")

    /** Rejects unexpected writes from connection flows. */
    override suspend fun create(title: String, description: String?): ApiResult<Task> = error("Unexpected create")

    /** Rejects unexpected updates from connection flows. */
    override suspend fun update(id: UUID, title: String, description: String?, isCompleted: Boolean): ApiResult<Task> = error("Unexpected update")

    /** Rejects unexpected deletion from connection flows. */
    override suspend fun delete(id: UUID): ApiResult<Unit> = error("Unexpected delete")
}
