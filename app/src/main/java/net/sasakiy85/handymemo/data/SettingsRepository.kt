package net.sasakiy85.handymemo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name="settings")

class SettingsRepository(private val context: Context) {
    private val rootUriKey = stringPreferencesKey("root_uri")
    val rootUriFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[rootUriKey]
    }
    suspend fun saveRootUri(uriString: String) {
        context.dataStore.edit { preferences ->
            preferences[rootUriKey] = uriString
        }
    }

    private val lastUsedTemplateKey = stringPreferencesKey("last_used_template")
    val lastUsedTemplateFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[lastUsedTemplateKey] ?: ""
    }
    suspend fun saveLastUsedTemplate(templateText: String) {
        context.dataStore.edit { preferences ->
            preferences[lastUsedTemplateKey] = templateText
        }
    }
}