package com.parlor.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.parlor.core.model.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

interface BackupSettingsStore {
    val settings: Flow<PlayerSettings>
    suspend fun replace(value: PlayerSettings)
}

class SettingsRepository private constructor(private val store: DataStore<Preferences>, private val disk: SettingsDisk?,
                                             private val storageHealth: StorageHealth?) : BackupSettingsStore {
    constructor(store: DataStore<Preferences>, storageHealth: StorageHealth? = null) : this(store, null, storageHealth)
    private constructor(disk: SettingsDisk, health: StorageHealth?) : this(disk.store, disk, health)
    constructor(context: Context, storageHealth: StorageHealth? = null) : this(SettingsDisk.get(context), storageHealth)
    private val key = SettingsDisk.SETTINGS_KEY
    override val settings = store.data.map { preferences ->
        preferences[key]?.let { ParlorJson.decodeFromString<PlayerSettings>(it) } ?: PlayerSettings()
    }.observeStorage(storageHealth, StorageArea.SETTINGS)
    private suspend fun edit(action: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        if (disk == null) store.edit { action(it) } else disk.edit(action)
    }
    suspend fun update(transform: (PlayerSettings) -> PlayerSettings) {
        edit { preferences ->
            val previous = preferences[key]?.let { ParlorJson.decodeFromString<PlayerSettings>(it) } ?: PlayerSettings()
            preferences[key] = ParlorJson.encodeToString(transform(previous))
        }
    }
    suspend fun selectTheme(theme: RoomTheme, hasPro: () -> Boolean) {
        update { current ->
            // All room palettes are free; the callback is retained for source compatibility.
            current.copy(theme = theme.id)
        }
    }
    override suspend fun replace(value: PlayerSettings) {
        edit { it[key] = ParlorJson.encodeToString(value) }
    }
    suspend fun resetUnreadableSettings() { requireNotNull(disk).reset() }
}
