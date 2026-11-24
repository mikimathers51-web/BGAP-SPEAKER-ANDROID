package com.tas5805m.ctrl.data

import org.json.JSONArray
import org.json.JSONObject

/** (De)serialisasi preset ke JSON — dipakai DataStore dan fitur ekspor/impor via clipboard. */
object PresetJson {
    fun toJson(list: List<Preset>): String {
        val arr = JSONArray()
        list.forEach { arr.put(toObj(it)) }
        return arr.toString()
    }

    fun toObj(p: Preset): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("kind", p.kind)
        put("note", p.note)
        put("mode", p.mode)
        put("xover", p.xover)
        p.xoverLp?.let { put("xoverLp", it) }
        put("master", p.master)
        put("sat", p.sat)
        put("sub", p.sub)
        put("ctr", p.ctr)
        put("gain", p.gain)
        put("eqOn", p.eqOn)
        put("ll", p.ll.toDouble()); put("lr", p.lr.toDouble())
        put("rl", p.rl.toDouble()); put("rr", p.rr.toDouble())
        put("eqLeft", JSONArray(p.eqLeft.map { it.toDouble() }))
        put("eqRight", JSONArray(p.eqRight.map { it.toDouble() }))
        put("created", p.created)
        p.clip?.let { put("clip", JSONArray(it)) }
        p.subInv?.let { put("subInv", it) }
        p.standby?.let { put("standby", it) }
        p.trim?.let { put("trim", it) }
        p.eqCtr?.let { put("eqCtr", JSONArray(it.map { v -> v.toDouble() })) }
        p.eqSub?.let { put("eqSub", JSONArray(it.map { v -> v.toDouble() })) }
        p.voice?.let { v ->
            put("voice", JSONObject().apply {
                put("on", v.on); put("hpf", v.hpf); put("pkHz", v.pkHz); put("pkDb10", v.pkDb10)
                put("pkQ10", v.pkQ10); put("harm", v.harm); put("limit", v.limit)
            })
        }
    }

    fun fromJson(raw: String): List<Preset> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { fromObj(it, i) } }
    } catch (_: Exception) {
        emptyList()
    }

    private fun floats(o: JSONObject, key: String): List<Float>? {
        val a = o.optJSONArray(key) ?: return null
        return (0 until 15).map { a.optDouble(it, 0.0).toFloat() }
    }

    private fun optInt(o: JSONObject, key: String): Int? =
        if (o.has(key) && !o.isNull(key)) o.optInt(key) else null

    /** Preset lama (sebelum 3.12) tidak punya id: buat id deterministik agar hapus-berdasar-id konsisten antar pembacaan. */
    private fun legacyId(o: JSONObject, index: Int): String =
        "legacy-" + Integer.toHexString((o.optString("name") + "|" + o.optString("created") + "|" + index).hashCode())

    /** `index` = posisi di array JSON (hanya untuk id preset lama). Nilai selalu di-clamp lewat PresetTools.sanitize. */
    fun fromObj(o: JSONObject, index: Int = 0): Preset {
        val clip = o.optJSONArray("clip")?.let { a -> (0 until a.length()).map { a.optInt(it) } }
        val voice = o.optJSONObject("voice")?.let { v ->
            VoiceParams(
                on = v.optBoolean("on", false),
                hpf = v.optInt("hpf", 32),
                pkHz = v.optInt("pkHz", 55),
                pkDb10 = v.optInt("pkDb10", 0),
                pkQ10 = v.optInt("pkQ10", 10),
                harm = v.optInt("harm", 0),
                limit = v.optInt("limit", 100),
            )
        }
        return PresetTools.sanitize(Preset(
            id = o.optString("id").ifBlank { legacyId(o, index) },
            name = o.optString("name"),
            kind = o.optString("kind", "full").ifEmpty { "full" },
            note = o.optString("note"),
            mode = o.optInt("mode"),
            xover = o.optInt("xover", 100),
            xoverLp = if (o.has("xoverLp")) o.optInt("xoverLp", 100) else null,
            master = o.optInt("master", 80),
            sat = o.optInt("sat", 100),
            sub = o.optInt("sub", 100),
            ctr = o.optInt("ctr", 100),
            gain = o.optInt("gain", 18),
            eqOn = o.optBoolean("eqOn", true),
            ll = o.optDouble("ll", 1.0).toFloat(),
            lr = o.optDouble("lr", 0.0).toFloat(),
            rl = o.optDouble("rl", 0.0).toFloat(),
            rr = o.optDouble("rr", 1.0).toFloat(),
            eqLeft = floats(o, "eqLeft") ?: List(15) { 0f },
            eqRight = floats(o, "eqRight") ?: List(15) { 0f },
            created = o.optString("created"),
            clip = clip,
            subInv = if (o.has("subInv")) o.optBoolean("subInv") else null,
            standby = optInt(o, "standby"),
            trim = optInt(o, "trim"),
            voice = voice,
            eqCtr = floats(o, "eqCtr"),
            eqSub = floats(o, "eqSub"),
        ))
    }
}
