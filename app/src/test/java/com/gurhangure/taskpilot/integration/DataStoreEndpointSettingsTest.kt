package com.gurhangure.taskpilot.integration

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.gurhangure.taskpilot.data.settings.DataStoreEndpointSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Verifies real Preferences DataStore persistence using an isolated temporary file. */
class DataStoreEndpointSettingsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    /** A normalized server URL survives closing and recreating the DataStore instance. */
    @Test
    fun savesAndRestoresFromDisk() = runBlocking {
        val file = temporary.root.resolve("connection.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstStore = PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file })
        try {
            val first = DataStoreEndpointSettings(firstStore)
            assertNull(first.read())
            first.write("https://server.test/prefix/")
            assertEquals("https://server.test/prefix/", first.read())
        } finally {
            firstScope.cancel()
            firstScope.coroutineContext.job.join()
        }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val second = DataStoreEndpointSettings(PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }))
            assertEquals("https://server.test/prefix/", second.read())
        } finally {
            secondScope.cancel()
            secondScope.coroutineContext.job.join()
        }
    }
}
