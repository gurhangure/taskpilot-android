package com.gurhangure.taskpilot.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Persists the selected endpoint in the application's single Preferences DataStore instance. */
class DataStoreEndpointSettings(private val store: DataStore<Preferences>) : EndpointSettings {
    private val endpointKey = stringPreferencesKey("server_url")

    /** Reads the current disk-backed value without substituting a build default. */
    override suspend fun read(): String? = store.data.first()[endpointKey]

    /** Changes only the server URL in an atomic DataStore transaction. */
    override suspend fun write(baseUrl: String) {
        store.edit { it[endpointKey] = baseUrl }
    }
}
