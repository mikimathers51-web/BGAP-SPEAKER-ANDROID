package com.tas5805m.ctrl.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Level { ERR, WARN, INFO, OK }

data class Finding(
    val level: Level,
    val title: String,
    val detail: String = "",
    val action: String = "",
)

/**
 * Mesin diagnosa: ubah state hasil parse (AmpState) menjadi daftar temuan berbahasa manusia.
 * Murni (tanpa Android) → bisa diuji unit. Aturan wireless mengikuti tabel "Membaca diagnostik link" di README
 * firmware, dengan satu koreksi: laju paket audio normal dihitung dari `pkt=` slave
 * (sample rate / 16 / sampel-per-paket), bukan konstanta 86/dtk (default master kini 64 sampel/paket ≈ 43/dtk).
 */
object Diagnosis {
    /** Slave dengan patch ≥ ini memproses voicing di chip (HPF biquad + peaking), bukan di ESP32. */
    const val SLAVE_VOICE_PATCH = 19
    /** Slave ≥ 2.9.20: peaking bebas (Hz/Q/0,1 dB) via biquad; < 2.9.20: band 80/125 Hz, gain bulat, Q tetap. */
    const val SLAVE_FREE_PEAK_PATCH = 20
    private const val DEFAULT_PKT = 64

    val FAULT_INFO: Map<String, Triple<Level, String, String>> = mapOf(
        "oc_r" to Triple(Level.ERR, "Overcurrent kanal kanan (OC-R)",
            "Cek hubung singkat kabel/terminal speaker kanan; impedansi terlalu rendah; wiring PBTL salah."),
        "oc_l" to Triple(Level.ERR, "Overcurrent kanal kiri (OC-L)",
            "Cek hubung singkat kabel/terminal speaker kiri; impedansi terlalu rendah; wiring PBTL salah."),
        "dc_r" to Triple(Level.ERR, "DC fault kanal kanan (DC-R)",
            "Ada tegangan DC di output kanan. Turunkan volume, periksa solder/kabel dan speaker."),
        "dc_l" to Triple(Level.ERR, "DC fault kanal kiri (DC-L)",
            "Ada tegangan DC di output kiri. Turunkan volume, periksa solder/kabel dan speaker."),
        "uv" to Triple(Level.ERR, "PVDD undervoltage (UV)",
            "Catu daya turun saat beban. Pakai adaptor berarus lebih besar / kabel daya lebih pendek."),
        "ov" to Triple(Level.ERR, "PVDD overvoltage (OV)",
            "Tegangan catu melebihi batas chip. Periksa adaptor."),
        "clk" to Triple(Level.WARN, "Clock fault (I2S)",
            "BCK/LRCK terganggu. Sesaat saat ganti lagu/sample rate itu wajar; bila terus-menerus cek wiring I2S."),
        "bq" to Triple(Level.ERR, "Biquad/DSP RAM error (BQ)",
            "Penulisan koefisien DSP bermasalah. Kirim ulang EQ/xover atau reboot ESP32."),
        "crc" to Triple(Level.ERR, "CRC konfigurasi error (CRC)",
            "Konfigurasi chip korup. Reboot ESP32 lalu terapkan ulang setelan."),
        "ot_sd" to Triple(Level.ERR, "Over-temperature shutdown (OT-SD)",
            "Chip mati karena panas. Perbaiki ventilasi/heatsink dan turunkan volume."),
        "ot_w" to Triple(Level.WARN, "Peringatan suhu tinggi (OT-W)",
            "Mendekati batas termal. Kurangi volume atau tambah pendinginan."),
    )

    fun faultLabel(key: String): String = key.uppercase(Locale.US).replace('_', '-')

    /** Counter kumulatif yang dipakai untuk delta sejak "mulai ukur". */
    fun counters(s: AmpState): Map<String, Long> {
        val m = HashMap<String, Long>()
        fun put(k: String, v: String?) {
            val n = v?.toLongOrNull()
            if (n != null) m[k] = n
        }
        val w = s.tags["WSUB"]
        put("w.nomem", w?.get("nomem")); put("w.ackfail", w?.get("ackfail"))
        put("w.ackok", w?.get("ackok")); put("w.txf", w?.get("txf"))
        val b = s.tags["BTD"]
        put("b.g100", b?.get("g100")); put("b.g200", b?.get("g200")); put("b.g500", b?.get("g500"))
        for (p in s.peerDetails) {
            val k = p.mac.uppercase()
            m["s.$k.und"] = p.und.toLong()
            m["s.$k.gap"] = p.gap.toLong()
            m["s.$k.stx"] = p.stx.toLong()
            m["s.$k.rej"] = p.rej.toLong()
        }
        return m
    }

    fun diagnose(s: AmpState): List<Finding> {
        if (!s.connected) {
            return listOf(Finding(Level.ERR, "Tidak terhubung", "Hubungkan ke perangkat di tab Koneksi."))
        }
        val out = ArrayList<Finding>()
        val cur = counters(s)
        val d: (String) -> Long = { key ->
            val c = cur[key] ?: 0L
            val b = s.baseline[key] ?: 0L
            if (c >= b) c - b else c        // counter di-reset firmware (reboot / wsub_diag_reset)
        }

        if (s.caps.isEmpty()) {
            out += Finding(Level.WARN, "CAPS belum diterima", "Firmware belum menjawab `caps`.", "Tekan Refresh semua.")
        }
        chipFaults(s, out)
        chipState(s, out)
        levels(s, out)
        if (s.hasWsub || s.isWireless) wireless(s, d, out)
        voice(s)?.let { out += Finding(Level.WARN, "Voicing sub", it) }

        if (out.none { it.level == Level.ERR || it.level == Level.WARN }) {
            val win = if (s.baselineAtMs > 0) {
                val sec = ((System.currentTimeMillis() - s.baselineAtMs) / 1000).coerceAtLeast(0)
                "Jendela ukur: $sec dtk."
            } else ""
            out += Finding(Level.OK, "Tidak ada masalah terdeteksi", win)
        }
        return out.sortedBy { it.level.ordinal }
    }

    // ------------------------------------------------------------------ chip / audio

    private fun chipFaults(s: AmpState, out: MutableList<Finding>) {
        val kv2 = s.tags["FAULT2"]
        for (chip in 1..2) {
            val active = if (chip == 1) FAULT_KEYS.filter { (s.faults[it] ?: 0) == 1 }
            else FAULT_KEYS.filter { kv2?.get(it) == "1" }
            for (f in active) {
                val info = FAULT_INFO[f] ?: continue
                out += Finding(info.first, "Chip $chip: ${info.second}", "Fault aktif sekarang.", info.third)
            }
            val counts = LinkedHashMap<String, Int>()
            for (e in s.faultHistory) {
                if (e.chip != chip) continue
                for (f in e.flags) if (f !in active) counts[f] = (counts[f] ?: 0) + 1
            }
            if (counts.isNotEmpty()) {
                out += Finding(
                    Level.WARN, "Chip $chip pernah fault (sesi ini)",
                    counts.entries.joinToString(", ") { "${faultLabel(it.key)} ×${it.value}" },
                    "Fault dibaca lalu dihapus oleh firmware; bila berulang, cari penyebabnya (lihat daftar fault)."
                )
            }
        }
    }

    private fun chipState(s: AmpState, out: MutableList<Finding>) {
        val amp = s.ampstate.lowercase(Locale.US)
        val info = s.tags["INFO"]
        val pwr = info?.get("pwr") ?: ""
        if (amp == "play" && pwr.isNotEmpty() && !pwr.equals("PLAY", ignoreCase = true)) {
            out += Finding(Level.WARN, "Amp diminta PLAY, chip melapor $pwr",
                "State chip tidak sesuai permintaan.", "Kirim ampstate play; periksa fault.")
        } else if (amp != "play") {
            out += Finding(Level.INFO, "Amp dalam state ${s.ampstate}", "Tidak ada suara dari amp pada state ini.", "Ubah ke PLAY di tab Home.")
        }
        if (s.inStandby) {
            out += Finding(Level.INFO, "Auto-standby aktif", "Amp Hi-Z karena tidak ada audio A2DP.", "Putar audio untuk membangunkan.")
        }
        if (info?.get("mute_r") == "1" && info["mute_l"] == "1") {
            out += Finding(Level.INFO, "Automute chip aktif", "Tidak ada sinyal digital masuk ke chip.")
        }
    }

    private fun levels(s: AmpState, out: MutableList<Finding>) {
        if (s.hasLvl) {
            val peak = maxOf(s.lvlL, s.lvlR)
            if (peak >= -10) {
                out += Finding(Level.WARN, "Level puncak ≈ 0 dBFS", "L=${s.lvlL / 10f} dB, R=${s.lvlR / 10f} dB (risiko clipping).",
                    "Turunkan master atau boost EQ.")
            }
        }
        val boost = ((s.eqLeft + s.eqRight).maxOrNull() ?: 0f)
        if (s.eqOn && boost >= 6f) {
            out += Finding(Level.WARN, "EQ boost +${boost.roundToInt()} dB",
                "Boost besar tanpa kompensasi menggerus headroom.", "Turunkan master sekitar ${boost.roundToInt()} dB atau kurangi boost.")
        }
        if (s.ll > 1.3f || s.rr > 1.3f) {
            out += Finding(Level.INFO, "Pelebaran stereo kuat (LL=${"%.2f".format(Locale.US, s.ll)})",
                "Sinyal satu sisi bisa naik hingga ≈ +3,5 dB.", "Pantau level agar tidak clipping.")
        }
    }

    // ------------------------------------------------------------------ wireless

    private fun wireless(s: AmpState, d: (String) -> Long, out: MutableList<Finding>) {
        val w = s.tags["WSUB"]
        if (s.peerDetails.isEmpty()) {
            out += Finding(Level.WARN, "Belum ada sub terpasang", "Daftar peer kosong.", "Tab Wireless → Scan → Pair.")
        }
        if (s.wsubFb == 1) {
            out += Finding(Level.ERR, "Fallback stereo aktif",
                "Sub hilang ≥ 6 dtk; satelit sementara FLAT (tanpa HPF).", "Pulihkan link sub; HPF dipasang lagi setelah link stabil 2 dtk.")
        }

        // ---- sisi TX master
        val nomem = d("w.nomem")
        if (w?.get("err") == "NO_MEM" || nomem > 0) {
            out += Finding(Level.ERR, "Antrean TX ESP-NOW penuh (NO_MEM ×$nomem)",
                "Radio tersita A2DP; paket audio sub terbuang.",
                "Pastikan -D WSUB_FAST_RATE=1 (default), coba -D WSUB_COEX_PREF=2, jauhkan dari router 2.4 GHz. WSUB_PKT_TX sudah di batas 64.")
        }
        val ok = d("w.ackok")
        val fail = d("w.ackfail")
        val tot = ok + fail
        if (tot >= 20 && fail > 0) {
            val pct = (fail * 100 / tot).toInt()
            if (pct >= 2) {
                out += Finding(
                    if (pct >= 10) Level.ERR else Level.WARN,
                    "ACK MAC gagal $pct% ($fail dari $tot)",
                    if (nomem == 0L) "Paket keluar tetapi slave tidak meng-ACK." else "Bersamaan dengan NO_MEM.",
                    "Cek ch master = ch slave (default 6), dekatkan slave, kurangi gangguan Wi-Fi."
                )
            }
        }
        val txf = d("w.txf")
        if (txf > 0) {
            out += Finding(Level.WARN, "esp_now_send gagal ×$txf", "Pengiriman ditolak stack radio.", "Lihat err= di baris WSUB.")
        }
        w?.get("ch")?.let { out += Finding(Level.INFO, "Channel radio master: $it", "Slave harus memakai channel yang sama (WSUB_CHANNEL).") }
        val heapMin = w?.get("heapmin")?.toLongOrNull()
        if (heapMin != null) {
            if (heapMin < 10_000) out += Finding(Level.ERR, "RAM internal master hampir habis (min ${heapMin / 1024} KB)",
                "Risiko crash 'Out of memory'.", "Kurangi fitur/log; pastikan master memakai ESP32-WROVER (PSRAM).")
            else if (heapMin < 20_000) out += Finding(Level.WARN, "RAM internal master mepet (min ${heapMin / 1024} KB)",
                "A2DP + SPP + ESP-NOW memakan heap internal.", "Pantau; hindari menambah fitur.")
        }
        val c = s.tags["WSUBC"]
        val mc = maxOf(c?.get("cpu0")?.toIntOrNull() ?: -1, c?.get("cpu1")?.toIntOrNull() ?: -1)
        if (mc >= 90) out += Finding(Level.WARN, "CPU master tinggi ($mc%)", "Audio/radio bisa tersendat.")

        // ---- per slave
        s.peerDetails.forEachIndexed { i, p ->
            val n = "Sub #${i + 1}"
            val key = p.mac.uppercase()
            val dg = s.slaveDiag[key]
            if (!p.link) {
                out += Finding(Level.ERR, "$n offline (${p.mac})", "Master tidak menerima STATUS dari slave.",
                    "Cek daya & jarak; ch master = ch slave; jika slave pernah di-pair master lain: tahan BOOT 3 dtk lalu pair ulang.")
                return@forEachIndexed
            }
            val before = out.size
            if (p.flt > 0) {
                out += Finding(Level.ERR, "$n fault amp: ${faultNames(p.flt).joinToString("/")}",
                    "Bitmask fault slave = ${p.flt}.", "Periksa speaker/catu daya sub.")
            } else if (p.fcnt > 0) {
                out += Finding(Level.WARN, "$n pernah fault (${p.fcnt}× sejak boot)", "Saat ini bersih.")
            }
            val patch = dg?.get("fw")?.substringAfterLast('.')?.toIntOrNull()
            if (patch != null && patch < SLAVE_VOICE_PATCH) {
                out += Finding(Level.INFO, "$n firmware 2.9.$patch (< 2.9.$SLAVE_VOICE_PATCH)",
                    "Voicing diproses di ESP32 (dengan limiter/harmonik), bukan di chip.", "Flash ulang slave dari proyek 2.9.19+ agar sesuai UI.")
            }
            val pkt = dg?.get("pkt")?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_PKT
            val rate = if (p.rate > 0) p.rate else 44100
            val expected = rate / 16.0 / pkt
            val dUnd = d("s.$key.und")
            val dGap = d("s.$key.gap")
            val jitter = if (dUnd > 0 || dGap > 0) " (und +$dUnd, gap +$dGap sejak ukur)" else ""
            if (p.rxa >= 0) {
                if (p.rxa <= 2 && d("w.ackok") > 0) {
                    out += Finding(Level.ERR, "$n tidak menerima audio (rxa=${p.rxa}/s)",
                        "Master mengirim & di-ACK, tetapi slave menolak paket (rej=${p.rej}).",
                        "Unpair slave (BOOT 3 dtk) lalu pair ulang; pastikan MAC master cocok.")
                } else if (p.rxa < expected * 0.7) {
                    out += Finding(Level.WARN, "$n penerimaan audio rendah (rxa=${p.rxa}/s, normal ≈ ${expected.roundToInt()}/s)$jitter",
                        "Paket hilang di udara.", "Dekatkan slave, ganti channel sepi (1/6/11), kurangi gangguan.")
                } else if (dUnd > 0 || dGap > 0) {
                    out += Finding(Level.WARN, "$n link baik tapi buffer kurang$jitter",
                        "Jitter buffer slave tidak cukup.", "Naikkan trim (tab Wireless) atau WSUB_SLAVE_DMA_COUNT 3→4.")
                }
            } else if (dUnd > 0 || dGap > 0) {
                out += Finding(Level.WARN, "$n underrun/gap$jitter", "Slave lama tanpa statistik rxa.", "Naikkan trim.")
            }
            val dRej = d("s.$key.rej")
            if (dRej > 0) out += Finding(Level.WARN, "$n menolak paket ×$dRej", "MAC pengirim bukan master yang dipasangkan.", "Unpair lalu pair ulang.")
            val dStx = d("s.$key.stx")
            if (dStx >= 10) out += Finding(Level.WARN, "$n STATUS slave→master gagal ×$dStx", "Jalur balik bermasalah.", "Jarak/antena slave; cek ch.")
            if (p.rssi != 0 && p.rssi < -80) {
                out += Finding(Level.WARN, "$n sinyal lemah (${p.rssi} dBm)", "Link rentan putus.", "Dekatkan slave ke master.")
            }
            val sc = maxOf(dg?.get("cpu0")?.toIntOrNull() ?: -1, dg?.get("cpu1")?.toIntOrNull() ?: -1)
            if (sc >= 90) out += Finding(Level.WARN, "$n CPU tinggi ($sc%)", "Slave kewalahan.")
            if (p.upSec in 0..20) out += Finding(Level.INFO, "$n baru reboot (${p.upSec} dtk lalu)", "Counter slave mulai dari nol.")
            if (out.size == before) {
                out += Finding(Level.OK, "$n normal", "link=1  rxa=${p.rxa}/s (≈${expected.roundToInt()})  fill=${p.fillMs} ms")
            }
        }

        // ---- Bluetooth (A2DP)
        val b = s.tags["BTD"]
        if (b != null) {
            val g100 = d("b.g100"); val g200 = d("b.g200"); val g500 = d("b.g500")
            if (g200 + g500 > 0) {
                out += Finding(Level.WARN, "Stall A2DP: ≥200 ms ×$g200, ≥500 ms ×$g500",
                    "Audio Bluetooth sempat putus-putus (≥100 ms ×$g100).",
                    "Coba -D WSUB_COEX_PREF=2 (prioritas radio ke Bluetooth), jauhkan HP/router, kurangi beban ESP-NOW.")
            } else if (g100 > 0) {
                out += Finding(Level.INFO, "Gap A2DP ≥100 ms ×$g100", "Ringan; pantau.")
            }
            val rssi = b["rssi"]?.toIntOrNull()
            val age = b["rssi_age"]?.toIntOrNull() ?: -1
            if (rssi != null && rssi != 0 && age >= 0 && rssi < -80) {
                out += Finding(Level.WARN, "Sinyal Bluetooth HP lemah ($rssi dBm)", "A2DP rentan putus.", "Dekatkan HP.")
            }
        }
    }

    /** Peringatan khusus voicing (dipakai juga oleh kartu Voicing di tab Wireless). Null = tidak ada. */
    fun voiceWarning(s: AmpState): String? = voice(s)

    /** True bila SEMUA slave yang terpasang melapor firmware ≥ 2.9.20 (versi tak diketahui dianggap belum). */
    fun voiceFreePeak(s: AmpState): Boolean {
        if (s.peerDetails.isEmpty()) return false
        return s.peerDetails.all { p ->
            val patch = s.slaveDiag[p.mac.uppercase()]?.get("fw")?.substringAfterLast('.')?.toIntOrNull()
            patch != null && patch >= SLAVE_FREE_PEAK_PATCH
        }
    }

    private fun voice(s: AmpState): String? {
        if (!(s.hasWsub || s.isWireless)) return null
        val msgs = ArrayList<String>()
        for ((i, p) in s.peerDetails.withIndex()) {
            val patch = s.slaveDiag[p.mac.uppercase()]?.get("fw")?.substringAfterLast('.')?.toIntOrNull()
            if (patch != null && patch < SLAVE_VOICE_PATCH) {
                msgs += "Sub #${i + 1} berfirmware 2.9.$patch (< 2.9.$SLAVE_VOICE_PATCH): voicing masih di ESP32 (limiter/harmonik), UI ini mengasumsikan di chip."
            }
        }
        val v = s.voice
        if (v.on) {
            if (v.pkDb10 > 40) msgs += "Peak gain +${v.pkDb10 / 10f} dB tanpa limiter: batasi ≤ +4 dB dan volume agar driver tidak over-excursion."
        }
        return if (msgs.isEmpty()) null else msgs.joinToString("\n")
    }

    // ------------------------------------------------------------------ laporan

    fun report(s: AmpState, findings: List<Finding> = diagnose(s)): String {
        val sb = StringBuilder()
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        sb.append("TAS5805M CTRL — laporan diagnosa\n")
        sb.append("Waktu     : ").append(ts).append('\n')
        sb.append("Perangkat : ").append(s.deviceName).append(' ').append(s.deviceAddress).append('\n')
        sb.append("Mode      : ").append(s.modeName()).append(" (").append(s.mode).append(")\n")
        sb.append("Firmware  : ").append(s.fwVersion.ifEmpty { "-" }).append(' ').append(s.fwRole).append('\n')
        sb.append("CAPS      : ").append(s.caps.joinToString(" ")).append("\n\n")
        sb.append("== HASIL ==\n")
        for (f in findings) {
            sb.append('[').append(f.level.name).append("] ").append(f.title)
            if (f.detail.isNotEmpty()) sb.append(" — ").append(f.detail)
            if (f.action.isNotEmpty()) sb.append("  → ").append(f.action)
            sb.append('\n')
        }
        sb.append("\n== BARIS TERAKHIR PER JENIS ==\n")
        for (k in s.rawByHead.keys.sorted()) {
            if (k == "OK" || k == "ERR") continue
            sb.append(s.rawByHead[k]).append('\n')
        }
        if (s.btEvents.isNotEmpty()) {
            sb.append("\n== EVENT BT ==\n")
            s.btEvents.forEach { sb.append(it).append('\n') }
        }
        if (s.faultHistory.isNotEmpty()) {
            sb.append("\n== RIWAYAT FAULT ==\n")
            val f = SimpleDateFormat("HH:mm:ss", Locale.US)
            s.faultHistory.forEach { e ->
                sb.append(f.format(Date(e.timeMs))).append("  chip").append(e.chip).append("  ")
                    .append(e.flags.joinToString(",") { faultLabel(it) }).append('\n')
            }
        }
        sb.append("\n== LOG (terbaru di bawah) ==\n")
        s.lastLines.take(80).reversed().forEach { sb.append(it).append('\n') }
        return sb.toString()
    }

    /** Dipakai UI: apakah ada nilai absolut mencurigakan di mixer (untuk label). */
    fun mixAbsMax(s: AmpState): Float = maxOf(abs(s.ll), abs(s.lr), abs(s.rl), abs(s.rr))
}
