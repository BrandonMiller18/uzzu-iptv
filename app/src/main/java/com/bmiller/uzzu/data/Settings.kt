package com.bmiller.uzzu.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persistent settings. The playlist URL is a secret: never log it unmasked. */
class Settings(private val store: DataStore<Preferences>) {

    val playlistUrl: Flow<String?> = store.data.map { it[PLAYLIST_URL] }
    val lastFetchAt: Flow<Long?> = store.data.map { it[LAST_FETCH_AT] }
    val lastFetchResult: Flow<String?> = store.data.map { it[LAST_FETCH_RESULT] }
    val lastUpdateCheckAt: Flow<Long?> = store.data.map { it[LAST_UPDATE_CHECK_AT] }

    suspend fun playlistUrl(): String? = playlistUrl.first()

    suspend fun setPlaylistUrl(url: String) = store.edit { it[PLAYLIST_URL] = url.trim() }

    suspend fun recordFetch(atMillis: Long, result: String) = store.edit {
        it[LAST_FETCH_AT] = atMillis
        it[LAST_FETCH_RESULT] = result
    }

    suspend fun setLastUpdateCheckAt(atMillis: Long) = store.edit { it[LAST_UPDATE_CHECK_AT] = atMillis }

    private companion object {
        val PLAYLIST_URL = stringPreferencesKey("playlist_url")
        val LAST_FETCH_AT = longPreferencesKey("last_fetch_at")
        val LAST_FETCH_RESULT = stringPreferencesKey("last_fetch_result")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
    }
}
