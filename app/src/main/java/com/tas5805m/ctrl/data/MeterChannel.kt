package com.tas5805m.ctrl.data

import kotlin.math.log10
import kotlin.math.pow

/** Hasil satu kanal untuk ditampilkan. Semua nilai deci-dB (−1200 = lantai/sunyi, 0 = penuh). */
data class MeterSnapshot(val now: Int, val peakHold: Int, val avg: Int, val sessionMax: Int)

/**
 * Penjejak level satu kanal dari sampel `lvl`/`wlvl` (±3×/dtk, deci-dB).
 * - peakHold: puncak ditahan [holdMs] lalu turun ke nilai sekarang (penanda di meter).
 * - avg: rata-rata DAYA atas [window] sampel terakhir (≈ rata-rata level, BUKAN RMS bentuk gelombang — firmware hanya mengirim level cuplikan).
 * - sessionMax: puncak tertinggi sejak [resetPeaks].
 */
class MeterChannel(private val window: Int = 24, private val holdMs: Long = 1500L) {
    private val recent = ArrayDeque<Int>()
    private var now = FLOOR
    private var hold = FLOOR
    private var holdAt = 0L
    private var session = FLOOR

    fun push(deciDb: Int, tMs: Long) {
        val v = deciDb.coerceIn(FLOOR, 0)
        now = v
        recent.addLast(v)
        while (recent.size > window) recent.removeFirst()
        if (v >= hold || tMs - holdAt > holdMs) { hold = v; holdAt = tMs }
        if (v > session) session = v
    }

    fun resetPeaks() { session = FLOOR; hold = now }

    fun snapshot(): MeterSnapshot {
        val avg = if (recent.isEmpty()) FLOOR else {
            val mean = recent.sumOf { 10.0.pow(it / 100.0) } / recent.size   // deci-dB → daya linear (10^(dB/10) = 10^(deci/100))
            (100.0 * log10(mean)).toInt().coerceIn(FLOOR, 0)
        }
        return MeterSnapshot(now, hold, avg, session)
    }

    companion object { const val FLOOR = -1200 }
}
