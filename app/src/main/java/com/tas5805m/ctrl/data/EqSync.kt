package com.tas5805m.ctrl.data

import kotlin.math.roundToInt

/**
 * Rencana "Sync L→R": salin gain EQ kanal kiri ke kanal kanan lewat `eqband r <band> <gain>`.
 * - Firmware memakai sscanf("%d") → gain dibulatkan ke integer −15..15 (sama dengan SppClient.setEqBand).
 * - Band yang dikunci crossover (0..lockedBands-1) dilewati: firmware menjawab `ERR eqband` untuk band < XOVER_RESERVED_BANDS.
 * - Band yang sudah sama dilewati supaya link SPP/A2DP tidak dibebani perintah sia-sia.
 */
object EqSync {
    const val BAND_COUNT = 15
    const val LOCKED_BANDS = 3   // band 0–2 (sama dengan `i <= 2` di EqScreen saat xoverLocksEq)

    fun plan(left: List<Float>, right: List<Float>, lockedBands: Int = 0): List<Pair<Int, Int>> =
        (lockedBands.coerceAtLeast(0) until BAND_COUNT).mapNotNull { i ->
            val l = left.getOrNull(i) ?: return@mapNotNull null
            val target = l.roundToInt().coerceIn(-15, 15)
            val current = right.getOrNull(i)?.roundToInt()?.coerceIn(-15, 15)
            if (current == target) null else i to target
        }
}
