package fr.shelves.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val googleKey = stringPreferencesKey("google_books_api_key")

    val googleBooksApiKey: Flow<String> = context.dataStore.data.map { it[googleKey].orEmpty() }

    suspend fun currentGoogleBooksApiKey(): String = googleBooksApiKey.first()

    suspend fun setGoogleBooksApiKey(value: String) {
        context.dataStore.edit { it[googleKey] = value.trim() }
    }
}
