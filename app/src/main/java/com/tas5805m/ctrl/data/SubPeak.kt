package com.tas5805m.ctrl.data

/** Hasil pengukuran puncak subwoofer. Semua nilai deci-dB (−1200 = lantai, 0 = penuh). */
data class SubPeakSnapshot(
    val now: Int,
    val hold: Int,        // puncak ditahan [SubPeakTracker.HOLD_MS], lalu turun
    val session: Int,     // puncak tertinggi sejak reset
    val avg: Int,         // rata-rata daya ±24 sampel terakhir (bukan RMS gelombang)
    val nearHits: Int,    // berapa kali level MENYEBERANG ke ≥ −3 dB (mendekati batas)
    val clipHits: Int,    // berapa kali level MENYEBERANG ke ≥ −1 dB (clip)
) {
    /** Sisa ruang sampai 0 dBFS dari puncak sesi. */
    val headroomDb: Float get() = -session / 10f

    /** Selisih puncak-tahan terhadap rata-rata ≈ crest factor (dB). Perkiraan: sampel level, bukan gelombang. */
    val crestDb: Float get() = (hold - avg) / 10f
}

/**
 * Pengukur puncak khusus subwoofer. Menerima level sub TERKERAS dari semua kanal sub per sampel
 * (chip 2 `lvl2`, kanal sub di chip 1, atau slave `wlvl`).
 * Beda dengan [MeterChannel] biasa: hold lebih lama (3 dtk, bass datang dalam semburan) dan ada penghitung
 * kejadian dekat-batas/clip. Penghitung menghitung SEBERANGAN naik, bukan jumlah sampel, jadi bass panjang
 * yang bertahan di atas ambang dihitung satu kali.
 */
class SubPeakTracker(
    private val holdMs: Long = HOLD_MS,
    private val nearDeci: Int = NEAR_DECI,
    private val clipDeci: Int = CLIP_DECI,
) {
    private var ch = MeterChannel(holdMs = holdMs)
    private var any = false
    private var aboveNear = false
    private var aboveClip = false
    var nearHits = 0; private set
    var clipHits = 0; private set

    fun push(deciDb: Int, tMs: Long) {
        val v = deciDb.coerceIn(MeterChannel.FLOOR, 0)
        ch.push(v, tMs)
        any = true
        val n = v >= nearDeci
        if (n && !aboveNear) nearHits++
        aboveNear = n
        val c = v >= clipDeci
        if (c && !aboveClip) clipHits++
        aboveClip = c
    }

    /** Reset puncak sesi + penghitung (tombol RESET). */
    fun resetPeaks() {
        ch.resetPeaks()
        nearHits = 0; clipHits = 0
        aboveNear = false; aboveClip = false
    }

    /** Buang semuanya (ganti mode / kanal berubah). */
    fun clear() {
        ch = MeterChannel(holdMs = holdMs)
        any = false
        resetPeaks()
    }

    /** null = belum ada sampel sub sama sekali. */
    fun snapshot(): SubPeakSnapshot? {
        if (!any) return null
        val s = ch.snapshot()
        return SubPeakSnapshot(s.now, s.peakHold, s.sessionMax, s.avg, nearHits, clipHits)
    }

    companion object {
        const val HOLD_MS = 3000L
        const val NEAR_DECI = -30   // −3 dB
        const val CLIP_DECI = -10   // −1 dB (sama dengan indikator CLIP di Monitor)
    }
}
