package com.tas5805m.ctrl.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PEQ sub wireless (firmware ≥ 2.9.27, CAPS `wsub_peq`): 7 band parametrik yang dijalankan di DSP chip slave.
 * Global — semua slave menerima set yang sama. Perintah: `wsub_peq 0|1`, `wsub_peq_band i type hz g2 q10`, `wsub_peq_reset`.
 * Rumus koefisien identik dengan `wsub_peq.h` di firmware (RBJ cookbook).
 */
object PeqType {
    const val OFF = 0
    const val PEAK = 1
    const val LSHELF = 2
    const val HSHELF = 3
}

const val PEQ_BANDS = 7
const val PEQ_HZ_MIN = 20
const val PEQ_HZ_MAX = 250
const val PEQ_G2_MIN = -24   // −12,0 dB
const val PEQ_G2_MAX = 18    // +9,0 dB
const val PEQ_Q_MIN = 5      // Q 0,5
const val PEQ_Q_MAX = 100    // Q 10,0

/** Frekuensi awal tiap band (tersebar di sekitar rentang sub). */
val PEQ_DEFAULT_HZ = listOf(25, 35, 50, 63, 80, 100, 125)

/** Satu band. `type` = [PeqType]; `g2` = gain dalam langkah 0,5 dB; `q10` = Q × 10. Band off menyimpan nilai terakhirnya. */
data class PeqBand(
    val type: Int = PeqType.OFF,
    val hz: Int = 60,
    val g2: Int = 0,
    val q10: Int = 10,
) {
    /** Band benar-benar mengubah sinyal (sama dengan `ws_peq_active` di firmware). */
    val active: Boolean get() = type in PeqType.PEAK..PeqType.HSHELF && g2 != 0
    val gainDb: Float get() = g2 * 0.5f
    val q: Float get() = q10 / 10f

    fun sanitized() = PeqBand(
        type = if (type in PeqType.OFF..PeqType.HSHELF) type else PeqType.OFF,
        hz = hz.coerceIn(PEQ_HZ_MIN, PEQ_HZ_MAX),
        g2 = g2.coerceIn(PEQ_G2_MIN, PEQ_G2_MAX),
        q10 = q10.coerceIn(PEQ_Q_MIN, PEQ_Q_MAX),
    )

    fun command(index: Int): String {
        val b = sanitized()
        return "wsub_peq_band $index ${b.type} ${b.hz} ${b.g2} ${b.q10}"
    }
}

data class PeqParams(
    val on: Boolean = false,
    val bands: List<PeqBand> = List(PEQ_BANDS) { PeqBand(hz = PEQ_DEFAULT_HZ[it]) },
) {
    fun sanitized() = PeqParams(
        on = on,
        bands = List(PEQ_BANDS) { i -> (bands.getOrNull(i) ?: PeqBand(hz = PEQ_DEFAULT_HZ[i])).sanitized() },
    )

    fun withBand(index: Int, band: PeqBand): PeqParams =
        if (index !in 0 until PEQ_BANDS) this
        else copy(bands = List(PEQ_BANDS) { i -> if (i == index) band else bands.getOrNull(i) ?: PeqBand(hz = PEQ_DEFAULT_HZ[i]) })

    /** Ada band yang mengubah sinyal. */
    val anyActive: Boolean get() = bands.any { it.active }
}

object PeqMath {
    /** Sample rate untuk kurva tampilan; selisih 44,1 vs 48 kHz tak terlihat di 20–250 Hz. */
    const val FS = 48000.0

    /** [b0, b1, b2, a1, a2] (a0 = 1). Band tidak aktif → unity. Identik dengan `ws_peq_band_bq` di firmware. */
    fun coeffs(raw: PeqBand, fs: Double = FS): DoubleArray {
        val b = raw.sanitized()
        if (!b.active) return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0)
        val f = b.hz.toDouble()
        val gDb = b.g2 * 0.5
        val q = b.q10 / 10.0
        val a = 10.0.pow(gDb / 40.0)
        val w = 2.0 * PI * f / fs
        val c = cos(w)
        val al = sin(w) / (2.0 * q)
        return when (b.type) {
            PeqType.PEAK -> {
                val a0 = 1.0 + al / a
                doubleArrayOf((1.0 + al * a) / a0, -2.0 * c / a0, (1.0 - al * a) / a0, -2.0 * c / a0, (1.0 - al / a) / a0)
            }
            PeqType.LSHELF -> {
                val t = 2.0 * sqrt(a) * al
                val a0 = (a + 1) + (a - 1) * c + t
                doubleArrayOf(
                    a * ((a + 1) - (a - 1) * c + t) / a0,
                    2.0 * a * ((a - 1) - (a + 1) * c) / a0,
                    a * ((a + 1) - (a - 1) * c - t) / a0,
                    -2.0 * ((a - 1) + (a + 1) * c) / a0,
                    ((a + 1) + (a - 1) * c - t) / a0,
                )
            }
            PeqType.HSHELF -> {
                val t = 2.0 * sqrt(a) * al
                val a0 = (a + 1) - (a - 1) * c + t
                doubleArrayOf(
                    a * ((a + 1) + (a - 1) * c + t) / a0,
                    -2.0 * a * ((a - 1) + (a + 1) * c) / a0,
                    a * ((a + 1) + (a - 1) * c - t) / a0,
                    2.0 * ((a - 1) - (a + 1) * c) / a0,
                    ((a + 1) - (a - 1) * c - t) / a0,
                )
            }
            else -> doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0)
        }
    }

    /** Respons magnitudo (dB) satu set koefisien pada frekuensi [f] Hz. */
    fun magDb(c: DoubleArray, f: Double, fs: Double = FS): Double {
        val w = 2.0 * PI * f / fs
        val cw = cos(w); val sw = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = c[0] + c[1] * cw + c[2] * c2
        val ni = -(c[1] * sw + c[2] * s2)
        val dr = 1.0 + c[3] * cw + c[4] * c2
        val di = -(c[3] * sw + c[4] * s2)
        val num = nr * nr + ni * ni
        val den = dr * dr + di * di
        return 10.0 * log10(num / den)
    }

    fun bandDb(b: PeqBand, f: Double, fs: Double = FS): Double = magDb(coeffs(b, fs), f, fs)

    /** Respons total (dB) semua band pada [f]. [respectOn] = false → hitung band apa adanya (untuk pratinjau saat PEQ off). */
    fun totalDb(p: PeqParams, f: Double, respectOn: Boolean = true, fs: Double = FS): Double {
        if (respectOn && !p.on) return 0.0
        return p.bands.sumOf { bandDb(it, f, fs) }
    }

    /** [n] titik respons (dB) berjarak logaritmis dari 20 sampai 250 Hz. */
    fun curve(p: PeqParams, n: Int = 96, respectOn: Boolean = false): FloatArray {
        val cs = p.bands.map { coeffs(it) }
        val lo = ln(PEQ_HZ_MIN.toDouble()); val hi = ln(PEQ_HZ_MAX.toDouble())
        return FloatArray(n) { i ->
            val f = kotlin.math.exp(lo + (hi - lo) * i / (n - 1))
            if (respectOn && !p.on) 0f else cs.sumOf { magDb(it, f) }.toFloat()
        }
    }

    /** Boost terbesar (dB, ≥ 0) dan frekuensinya pada rentang 20–250 Hz; PEQ off → 0. */
    fun maxBoost(p: PeqParams): Pair<Float, Int> {
        if (!p.on || !p.anyActive) return 0f to 0
        val n = 200
        val lo = ln(PEQ_HZ_MIN.toDouble()); val hi = ln(PEQ_HZ_MAX.toDouble())
        val cs = p.bands.map { coeffs(it) }
        var best = 0.0; var bestF = 0
        for (i in 0 until n) {
            val f = kotlin.math.exp(lo + (hi - lo) * i / (n - 1))
            val db = cs.sumOf { magDb(it, f) }
            if (db > best) { best = db; bestF = f.toInt() }
        }
        return best.toFloat() to bestF
    }

    /** Ambang boost gabungan (PEQ + peaking voicing) yang dianggap berisiko clipping di DSP slave. */
    const val HEADROOM_WARN_DB = 6f

    /**
     * Peringatan headroom: boost PEQ di puncak ditambah peaking voicing (bila aktif dan positif) ≥ [HEADROOM_WARN_DB].
     * Perkiraan konservatif (puncak keduanya dianggap berimpit); null bila aman atau PEQ off.
     */
    fun headroomWarning(p: PeqParams, voice: VoiceParams?): String? {
        val (boost, at) = maxBoost(p)
        if (boost <= 0f) return null
        val vb = if (voice != null && voice.on && voice.pkDb10 > 0) voice.pkDb10 / 10f else 0f
        val total = boost + vb
        if (total < HEADROOM_WARN_DB) return null
        val fmt = { v: Float -> String.format(java.util.Locale.US, "%.1f", v) }
        return if (vb > 0f)
            "Boost PEQ +${fmt(boost)} dB di ±$at Hz + peaking voicing +${fmt(vb)} dB = hingga +${fmt(total)} dB: risiko clipping di DSP sub. Turunkan gain atau Volume sub."
        else
            "Boost PEQ +${fmt(boost)} dB di ±$at Hz: risiko clipping di DSP sub (tanpa limiter). Turunkan gain atau Volume sub."
    }
}
