package com.tas5805m.ctrl.data

/**
 * Pasangan modulasi / frekuensi switching / bandwidth loop class-D TAS5805M yang diizinkan datasheet (Tabel 7-3).
 * Harus sama dengan `mod_rules.h` di firmware (firmware menolak sisanya dengan `ERR modset bw_not_allowed`).
 *
 *             384k     480k      576k      768k
 *  BD         80–120   80–120    80–175    80–175
 *  1SPW/Hyb   80       80–100    80–120    80–175
 */
object ModRules {
    val allBw = listOf("80k", "100k", "120k", "175k")

    private fun k(s: String) = s.removeSuffix("k").toIntOrNull()

    fun maxBwK(mode: String, freq: String): Int {
        val f = k(freq) ?: return 80
        return if (mode == "bd") (if (f <= 480) 120 else 175)
        else when (f) { 384 -> 80; 480 -> 100; 576 -> 120; else -> 175 }
    }

    /** Bandwidth yang boleh dipakai untuk [mode] + [freq], urut naik. */
    fun allowedBw(mode: String, freq: String): List<String> {
        val max = maxBwK(mode, freq)
        return allBw.filter { (k(it) ?: Int.MAX_VALUE) <= max }
    }

    /** [bd] bila masih boleh; jika tidak, bandwidth terbesar yang masih diizinkan. */
    fun clampBw(mode: String, freq: String, bd: String): String {
        val ok = allowedBw(mode, freq)
        return if (bd in ok) bd else ok.last()
    }
}
