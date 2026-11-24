package com.tas5805m.ctrl.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

/**
 * 7 tema ala HTML Final v4:
 * SYSTEM (auto), DARK, LIGHT, AMOLED, NORD, SUNSET, FOREST.
 */
enum class ThemeMode {
    SYSTEM, DARK, LIGHT, AMOLED, NORD, SUNSET, FOREST;

    val label: String
        get() = when (this) {
            SYSTEM -> "Sistem"
            DARK -> "Gelap"
            LIGHT -> "Terang"
            AMOLED -> "AMOLED"
            NORD -> "Nord"
            SUNSET -> "Sunset"
            FOREST -> "Forest"
        }

    /** true = skema gelap (background gelap). */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        else -> true
    }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val autoReconnect: Boolean = true,
    val lastAddress: String? = null,
    val lastName: String? = null,
    /** Gaya level meter Monitor: 0 Classic, 1 Segmented, 2 Minimal (sama HTML). */
    val meterStyle: Int = 0,
    /** Mode meter Monitor: false = PEAK, true = RMS. */
    val meterRms: Boolean = false,
)

/** Preferensi tampilan + perangkat terakhir (untuk sambung otomatis). Disimpan di DataStore. */
class SettingsRepository(private val context: Context) {
    private val kTheme = stringPreferencesKey("theme")
    private val kDynamic = booleanPreferencesKey("dynamic")
    private val kAuto = booleanPreferencesKey("auto_reconnect")
    private val kAddr = stringPreferencesKey("last_address")
    private val kName = stringPreferencesKey("last_name")
    private val kMeterStyle = intPreferencesKey("meter_style")
    private val kMeterRms = booleanPreferencesKey("meter_rms")

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == p[kTheme] } ?: ThemeMode.SYSTEM,
            dynamicColor = p[kDynamic] ?: false,
            autoReconnect = p[kAuto] ?: true,
            lastAddress = p[kAddr],
            lastName = p[kName],
            meterStyle = (p[kMeterStyle] ?: 0).coerceIn(0, 2),
            meterRms = p[kMeterRms] ?: false,
        )
    }

    suspend fun setTheme(m: ThemeMode) { context.settingsStore.edit { it[kTheme] = m.name } }
    suspend fun setDynamic(on: Boolean) { context.settingsStore.edit { it[kDynamic] = on } }
    suspend fun setDynamicColor(on: Boolean) = setDynamic(on)
    suspend fun setAutoReconnect(on: Boolean) { context.settingsStore.edit { it[kAuto] = on } }
    suspend fun setMeterStyle(i: Int) { context.settingsStore.edit { it[kMeterStyle] = i.coerceIn(0, 2) } }
    suspend fun setMeterRms(on: Boolean) { context.settingsStore.edit { it[kMeterRms] = on } }
    suspend fun setLastDevice(address: String, name: String) {
        context.settingsStore.edit { it[kAddr] = address; it[kName] = name }
    }
}
