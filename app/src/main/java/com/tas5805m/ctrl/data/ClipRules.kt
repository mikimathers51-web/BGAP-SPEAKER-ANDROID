package com.tas5805m.ctrl.data

import java.util.Locale

/**
 * Aturan angka clipper TAS5805M (semua dalam deci-dB).
 *
 * Firmware menulis makeup sebagai `makeup − (gain + 240)` ke register Q2.30 (maks ≈ +6 dB),
 * sehingga makeup yang sah hanya sampai `gain + 300`. Di atas itu nilai di-clamp diam-diam di chip.
 */
object ClipRules {
    const val GAIN_MIN = -300
    const val GAIN_MAX = 0
    const val MAKEUP_MAX = 300

    fun maxMakeup(gain: Int): Int = gain.coerceIn(GAIN_MIN, GAIN_MAX) + MAKEUP_MAX

    /** 30 → "+3.0 dB", −30 → "-3.0 dB". */
    fun fmtDb(db10: Int): String = String.format(Locale.US, "%+.1f dB", db10 / 10.0)
}
