package com.tas5805m.ctrl.data

import kotlin.math.max

/** Satu kanal yang ditampilkan di tab Monitor. [id] dipakai sebagai kunci penjejak level. */
data class MonitorChannel(val id: String, val label: String, val isSub: Boolean = false)

/**
 * Pemetaan kanal Monitor per mode, mengikuti `applyBalance()` firmware (level dalam deci-dB, −1200..0):
 * - mode 0: chip 1 L/R = stereo
 * - mode 1: chip 1 = satelit L/R; chip 2 = sub mono (L = R)
 * - mode 2: chip 1 L = satelit, R = sub (mono dijumlah)
 * - mode 3: chip 1 = satelit L/R; chip 2 L = center, R = sub
 * - mode 4: chip 1 = satelit L/R; sub = slave nirkabel (`wlvl`)
 * - mode 5: chip 1 = tweeter L/R; chip 2 = woofer L/R
 * - mode 6: chip 1 L = tweeter, R = woofer
 */
object MonitorLayout {
    const val MAX_PEERS = 4

    /** Dua kanal utama (chip 1: `lvl l=` / `lvl r=`). */
    fun main(mode: Int): Pair<MonitorChannel, MonitorChannel> = when (mode) {
        0 -> MonitorChannel("c1l", "L") to MonitorChannel("c1r", "R")
        2 -> MonitorChannel("c1l", "SAT") to MonitorChannel("c1r", "SUB", true)
        6 -> MonitorChannel("c1l", "TW") to MonitorChannel("c1r", "WF", true)
        5 -> MonitorChannel("c1l", "TW L") to MonitorChannel("c1r", "TW R")
        else -> MonitorChannel("c1l", "SAT L") to MonitorChannel("c1r", "SAT R")
    }

    /** Kanal tambahan di kartu Channels (chip 2 atau sub nirkabel). Kosong bila mode tidak punya. */
    fun extra(s: AmpState): List<MonitorChannel> = when (s.mode) {
        1 -> if (s.hasLvl2) listOf(MonitorChannel("c2m", "Sub", true)) else emptyList()
        3 -> if (s.hasLvl2) listOf(MonitorChannel("c2l", "Center"), MonitorChannel("c2r", "Sub", true)) else emptyList()
        5 -> if (s.hasLvl2) listOf(MonitorChannel("c2l", "WF L", true), MonitorChannel("c2r", "WF R", true)) else emptyList()
        4 -> s.peerDetails.take(MAX_PEERS).mapIndexed { i, p ->
            MonitorChannel("p$i", "Sub ${(if (p.slot >= 0) p.slot else i) + 1}", true)
        }
        else -> emptyList()
    }

    /** Apakah mode ini membaca chip 2 lewat `lvl2`. */
    fun needsLvl2(mode: Int) = mode == 1 || mode == 3 || mode == 5

    /** Level terbaru (deci-dB) untuk [id]; null = tidak tersedia (mis. slave offline / `WLVL s0=na`). */
    fun read(s: AmpState, id: String): Int? = when (id) {
        "c1l" -> s.lvlL
        "c1r" -> s.lvlR
        "c2l" -> s.lvl2L
        "c2r" -> s.lvl2R
        "c2m" -> max(s.lvl2L, s.lvl2R)
        else -> if (id.startsWith("p")) id.drop(1).toIntOrNull()?.let { s.peerDetails.getOrNull(it)?.lvlDeciDb } else null
    }
}
