package com.tas5805m.ctrl.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Satu laporan diagnosa yang tersimpan di riwayat. */
data class DiagReport(val id: String, val createdMs: Long, val title: String, val text: String)

/** Logika murni riwayat laporan diagnosa (JSON, batas jumlah, nama file) — bisa diuji di JVM. */
object DiagHistory {
    const val MAX = 20
    const val MAX_TEXT = 60_000
    const val MAX_TITLE = 60

    fun make(title: String, text: String, nowMs: Long): DiagReport = DiagReport(
        id = java.util.UUID.randomUUID().toString(),
        createdMs = nowMs,
        title = title.trim().ifEmpty { "Laporan" }.take(MAX_TITLE),
        text = text.take(MAX_TEXT),
    )

    /** Tambah di depan (terbaru dulu) dan potong ke MAX laporan terbaru. */
    fun add(list: List<DiagReport>, r: DiagReport): List<DiagReport> =
        (listOf(r) + list.filterNot { it.id == r.id }).take(MAX)

    fun toJson(list: List<DiagReport>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("t", it.createdMs).put("title", it.title).put("text", it.text))
        }
        return arr.toString()
    }

    fun fromJson(raw: String): List<DiagReport> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            DiagReport(id, o.optLong("t", 0L), o.optString("title").take(MAX_TITLE), o.optString("text").take(MAX_TEXT))
        }.take(MAX)
    } catch (_: Exception) {
        emptyList()
    }

    fun fileName(ms: Long): String =
        "diagnosa-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(ms)) + ".txt"

    fun stamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))
}
