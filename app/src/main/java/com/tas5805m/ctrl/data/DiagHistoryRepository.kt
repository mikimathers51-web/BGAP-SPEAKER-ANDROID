package com.tas5805m.ctrl.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.diagStore by preferencesDataStore("diag_history")

/** Riwayat laporan diagnosa (maks 20, terbaru dulu), disimpan di DataStore — bertahan setelah app ditutup. */
class DiagHistoryRepository(private val context: Context) {
    private val key = stringPreferencesKey("reports_json")

    val reports: Flow<List<DiagReport>> = context.diagStore.data.map { DiagHistory.fromJson(it[key] ?: "[]") }

    suspend fun add(r: DiagReport) {
        context.diagStore.edit { p -> p[key] = DiagHistory.toJson(DiagHistory.add(DiagHistory.fromJson(p[key] ?: "[]"), r)) }
    }

    suspend fun remove(id: String) {
        context.diagStore.edit { p -> p[key] = DiagHistory.toJson(DiagHistory.fromJson(p[key] ?: "[]").filterNot { it.id == id }) }
    }

    suspend fun clear() {
        context.diagStore.edit { p -> p[key] = "[]" }
    }
}
