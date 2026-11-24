package com.tas5805m.ctrl.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val autoReconnect: Boolean = true,
    val lastAddress: String? = null,
    val lastName: String? = null,
)

/** Preferensi tampilan + perangkat terakhir (untuk sambung otomatis). Disimpan di DataStore. */
class SettingsRepository(private val context: Context) {
    private val kTheme = stringPreferencesKey("theme")
    private val kDynamic = booleanPreferencesKey("dynamic")
    private val kAuto = booleanPreferencesKey("auto_reconnect")
    private val kAddr = stringPreferencesKey("last_address")
    private val kName = stringPreferencesKey("last_name")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == p[kTheme] } ?: ThemeMode.SYSTEM,
            dynamicColor = p[kDynamic] ?: false,
            autoReconnect = p[kAuto] ?: true,
            lastAddress = p[kAddr],
            lastName = p[kName],
        )
    }

    suspend fun setTheme(m: ThemeMode) { context.settingsStore.edit { it[kTheme] = m.name } }
    suspend fun setDynamic(on: Boolean) { context.settingsStore.edit { it[kDynamic] = on } }
    suspend fun setAutoReconnect(on: Boolean) { context.settingsStore.edit { it[kAuto] = on } }
    suspend fun setLastDevice(address: String, name: String) {
        context.settingsStore.edit { it[kAddr] = address; it[kName] = name }
    }
}
