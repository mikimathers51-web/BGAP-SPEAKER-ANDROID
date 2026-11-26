package com.tas5805m.ctrl.data

import kotlin.math.roundToInt

/**
 * Utilitas murni (tanpa Android) untuk preset: validasi/clamp, deteksi band EQ yang dilewati saat crossover aktif,
 * perkiraan headroom, dan penggabungan hasil impor. Bisa diuji di JVM (lihat PresetToolsTest).
 */
object PresetTools {
    const val MAX_PRESETS = 100
    const val MAX_IMPORT = 50
    const val MAX_IMPORT_CHARS = 200_000
    const val MAX_NAME = 40
    const val MAX_NOTE = 200

    /** Band 0..2 (20/32/50 Hz) dicadangkan untuk filter crossover pada mode 1..4. */
    const val RESERVED_BANDS = 3

    private fun gain(v: Float): Float = if (v.isFinite()) v.roundToInt().coerceIn(-15, 15).toFloat() else 0f

    private fun mix(v: Float, def: Float): Float = if (v.isFinite()) v.coerceIn(-1f, 2f) else def

    private fun eq15(l: List<Float>): List<Float> = List(15) { gain(l.getOrElse(it) { 0f }) }

    private fun clampVoice(v: VoiceParams) = v.copy(
        hpf = v.hpf.coerceIn(20, 120),
        pkHz = v.pkHz.coerceIn(30, 150),
        pkDb10 = v.pkDb10.coerceIn(-60, 90),
        pkQ10 = v.pkQ10.coerceIn(5, 30),
        // Firmware (slave >= 2.9.19) menolak harm != 0 atau limit != 100 (ERR wsub_voice harm_limit_unsupported):
        // paksa ke nilai nonaktif agar preset impor lama tidak ditolak.
        harm = 0,
        limit = 100,
    )

    /** Samakan semua nilai dengan rentang yang diterima firmware; JSON hasil impor tidak dipercaya apa adanya. */
    fun sanitize(p: Preset): Preset = p.copy(
        name = p.name.trim().take(MAX_NAME),
        note = p.note.take(MAX_NOTE),
        kind = if (p.kind in setOf("full", "eq", "voice", "scene")) p.kind else "full",
        mode = p.mode.coerceIn(0, 6),
        // Mode 2 (firmware dengan CAPS xext) boleh sampai 300 Hz; mode lain dan firmware lama menolak/menjepit > 150 sendiri.
        xover = p.xover.coerceIn(60, if (p.mode == 2) 300 else 150),
        xoverLp = p.xoverLp?.coerceIn(60, if (p.mode == 2) 300 else 150),
        master = p.master.coerceIn(0, 100),
        sat = p.sat.coerceIn(0, 100),
        sub = p.sub.coerceIn(0, 100),
        ctr = p.ctr.coerceIn(0, 100),
        gain = p.gain.coerceIn(0, 31),
        ll = mix(p.ll, 1f), lr = mix(p.lr, 0f), rl = mix(p.rl, 0f), rr = mix(p.rr, 1f),
        eqLeft = eq15(p.eqLeft), eqRight = eq15(p.eqRight),
        clip = p.clip?.takeIf { it.size >= 3 }?.let { listOf(it[0].coerceIn(-300, 0), it[1].coerceIn(0, 300), it[2].coerceIn(0, 300)) },
        standby = p.standby?.coerceIn(0, 240),
        trim = p.trim?.coerceIn(-60, 100),
        voice = p.voice?.let { clampVoice(it) },
        eqCtr = p.eqCtr?.let { eq15(it) },
        eqSub = p.eqSub?.let { eq15(it) },
        peq = p.peq?.sanitized(),
    )

    /** Band yang benar-benar dikirim untuk `mode` (mode ≠ 0 melewati band 0..2). */
    fun activeBands(mode: Int): IntRange = if (mode != 0) RESERVED_BANDS..14 else 0..14

    /** True bila preset punya nilai ≠ 0 di band yang akan dilewati pada `mode` → sebagian efeknya hilang. */
    fun hasSkippedEffect(p: Preset, mode: Int): Boolean {
        if (p.kind == "voice" || mode == 0) return false
        return (0 until RESERVED_BANDS).any { p.eqLeft.getOrElse(it) { 0f } != 0f || p.eqRight.getOrElse(it) { 0f } != 0f }
    }

    /** Boost terbesar (dB, ≥ 0) di band yang benar-benar diterapkan pada `mode`. */
    fun maxBoostDb(p: Preset, mode: Int): Int {
        if (p.kind == "voice") return 0
        val r = activeBands(mode)
        return r.maxOf { i -> maxOf(p.eqLeft.getOrElse(i) { 0f }, p.eqRight.getOrElse(i) { 0f }).roundToInt() }.coerceAtLeast(0)
    }

    /** Peringatan singkat untuk ditampilkan di baris preset / setelah diterapkan; null bila tidak ada. */
    fun warning(p: Preset, mode: Int): String? {
        val parts = ArrayList<String>()
        if (hasSkippedEffect(p, mode)) parts += "Band 20/32/50 Hz dilewati saat crossover aktif — sebagian efek bass tidak diterapkan (atur bass lewat voicing sub)."
        val boost = maxBoostDb(p, mode)
        if (boost >= 4) parts += "Boost maks +$boost dB: bila terdengar pecah, turunkan Master ±$boost dB (EQ chip tanpa preamp)."
        val v = p.voice
        if (v != null && v.on && v.pkDb10 > 40) parts += "Peak sub +${v.pkDb10 / 10f} dB tanpa limiter: batasi ≤ +4 dB."
        p.peq?.let { q -> PeqMath.headroomWarning(q, v)?.let { parts += it } }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    /** Kunci isi preset (tanpa id/created/builtin) untuk mendeteksi duplikat saat impor. */
    fun contentKey(p: Preset): String =
        PresetJson.toObj(p.copy(id = "", created = "", builtin = false)).toString()

    /**
     * Gabungkan `incoming` ke `existing`: lewati duplikat persis, beri id baru bila bentrok, dan beri akhiran " (2)"
     * pada nama yang sama tetapi isi berbeda. Hasil dipotong sampai MAX_PRESETS (preset lama dipertahankan).
     */
    fun merge(existing: List<Preset>, incoming: List<Preset>): Pair<List<Preset>, Int> {
        val keys = existing.mapTo(HashSet()) { contentKey(it) }
        val ids = existing.mapTo(HashSet()) { it.id }
        val names = existing.mapTo(HashSet()) { it.name.lowercase() }
        val added = ArrayList<Preset>()
        for (raw in incoming.take(MAX_IMPORT)) {
            val p = sanitize(raw.copy(builtin = false)).takeIf { it.name.isNotBlank() } ?: continue
            val key = contentKey(p)
            if (!keys.add(key)) continue
            var name = p.name
            var n = 2
            while (name.lowercase() in names) { name = "${p.name.take(MAX_NAME - 5)} ($n)"; n++ }
            names += name.lowercase()
            val id = if (p.id.isBlank() || p.id in ids) newPresetId() else p.id
            ids += id
            added += p.copy(name = name, id = id)
        }
        val room = (MAX_PRESETS - existing.size).coerceAtLeast(0)
        val kept = added.take(room)
        return Pair(kept + existing, kept.size)
    }
}
