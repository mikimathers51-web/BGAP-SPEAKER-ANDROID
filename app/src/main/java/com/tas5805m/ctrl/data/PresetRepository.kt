package com.tas5805m.ctrl.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("presets")

class PresetRepository(private val context: Context) {
    private val KEY = stringPreferencesKey("presets_json")

    val presets: Flow<List<Preset>> = context.dataStore.data.map { prefs ->
        parse(prefs[KEY] ?: "[]")
    }

    /** Tambah di depan daftar. Nama yang sudah ada diberi akhiran (2), (3), … agar mudah dibedakan. */
    suspend fun add(p: Preset) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY] ?: "[]")
            if (list.size >= PresetTools.MAX_PRESETS) return@edit
            val clean = PresetTools.sanitize(p)
            val taken = list.mapTo(HashSet()) { it.name.lowercase() }
            var name = clean.name
            var n = 2
            while (name.lowercase() in taken) { name = "${clean.name.take(PresetTools.MAX_NAME - 5)} ($n)"; n++ }
            prefs[KEY] = serialize(listOf(clean.copy(name = name)) + list)
        }
    }

    /** Hapus berdasarkan id (bukan indeks) sehingga tidak salah hapus bila daftar berubah di antara tampil dan tap. */
    suspend fun removeById(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY] = serialize(parse(prefs[KEY] ?: "[]").filterNot { it.id == id })
        }
    }

    private fun serialize(list: List<Preset>): String = PresetJson.toJson(list)

    private fun parse(raw: String): List<Preset> = PresetJson.fromJson(raw)

    /** Semua preset pengguna sebagai JSON (untuk dibagikan / dicadangkan). */
    suspend fun exportJson(): String = serialize(presets.first())

    /**
     * Gabungkan preset dari JSON. Nilai di-clamp, duplikat persis dilewati, nama kembar diberi akhiran.
     * Mengembalikan jumlah preset yang benar-benar ditambahkan (0 = JSON tidak valid / semua duplikat).
     */
    suspend fun importJson(raw: String): Int {
        val text = raw.trim()
        if (text.isEmpty() || text.length > PresetTools.MAX_IMPORT_CHARS) return 0
        val incoming = parse(text)
        if (incoming.isEmpty()) return 0
        var added = 0
        context.dataStore.edit { prefs ->
            val (merged, n) = PresetTools.merge(parse(prefs[KEY] ?: "[]"), incoming)
            added = n
            if (n > 0) prefs[KEY] = serialize(merged)
        }
        return added
    }
}
