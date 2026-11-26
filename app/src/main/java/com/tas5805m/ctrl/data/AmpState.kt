package com.tas5805m.ctrl.data

data class AmpState(
    val connected: Boolean = false,
    /** true setelah firmware membalas `dump` pertama (data asli sudah masuk). Sebelumnya semua angka hanyalah nilai bawaan. */
    val synced: Boolean = false,
    /** Sinkronisasi tidak selesai dalam batas waktu (mis. firmware lama tanpa `dump`): sebagian nilai mungkin masih bawaan. */
    val syncPartial: Boolean = false,
    val deviceName: String = "",
    val deviceAddress: String = "",

    // Core
    val mode: Int = 0,
    /** HPF satelit (kunci lama `xover=`; firmware lama: HPF = LPF). */
    val xover: Int = 100,
    /** LPF sub (`xlp=`, firmware ≥ 2.9.22 dengan CAPS `xover2`); firmware lama: sama dengan [xover]. */
    val xoverLp: Int = 100,
    /** 2-way aktif mono (mode 2, firmware ≥ 2.9.23, CAPS `tway`): baris `TWAY on= hz= min= max= fs=`. */
    val tway: Boolean = false,
    val twayHz: Int = 2500,
    val twayMin: Int = 1000,
    val twayMax: Int = 5000,
    /** Loudness sub (mode 1–4, firmware ≥ 2.9.25, CAPS `subloud`): baris `SUBLOUD on= db10= lo= hi= cur=`. */
    val subloud: Boolean = false,
    val subloudDb10: Int = 60,   // boost maks, dB×10 (0..60)
    val subloudLo: Int = 30,     // master ≤ lo → boost penuh
    val subloudHi: Int = 70,     // master ≥ hi → 0 dB
    val subloudCur: Int = 0,     // boost yang sedang diterapkan, dB×10
    val subloudPhone: Boolean = true, // firmware ≥ 2.9.26: loudness ikut volume ponsel (AVRCP), `ph=`
    val phoneVol: Int = -1,           // volume ponsel 0..127 (`pv=`), -1 = belum diterima
    val subloudEff: Int = -1,         // volume efektif % yang dipakai kurva (`eff=`), -1 = firmware lama
    /** Psychoacoustic bass (semua mode, firmware dengan CAPS `pbass`): baris `PBASS on= amt= fc= act=`. */
    val pbass: Boolean = false,
    val pbassAmt: Int = 40,      // 0..100
    val pbassFc: Int = 80,       // 40..150 Hz: titik awal harmonik
    val pbassActive: Boolean = false,   // efek sedang memproses (gain > 0)
    val master: Int = 80,
    val sat: Int = 100,
    val sub: Int = 100,
    val ctr: Int = 100,
    val gain: Int = 18,
    val ampstate: String = "play",
    val eqOn: Boolean = true,

    // Mixer
    val ll: Float = 1f, val lr: Float = 0f,
    val rl: Float = 0f, val rr: Float = 1f,

    // EQ 15 bands
    val eqLeft: List<Float> = List(15) { 0f },
    val eqRight: List<Float> = List(15) { 0f },
    val eqm: Int = 6,
    val eqpLeft: Int = 0,
    val eqpRight: Int = 0,

    // Level meters (deci-dB)
    val lvlL: Int = -1200,
    val lvlR: Int = -1200,
    val lvl2L: Int = -1200,
    val lvl2R: Int = -1200,

    // Fault
    val faultRaw: String = "",
    val faults: Map<String, Int> = emptyMap(),

    // CAPS
    val caps: Set<String> = emptySet(),

    // Wireless Sub (legacy single + multi)
    val wsubPeer: String = "none",
    val wsubLink: Int = 0,
    val wsubFillMs: Int = 0,
    val wsubUnd: Int = 0,
    val wsubGap: Int = 0,
    val wsubRate: Int = 0,
    val wsubHiz: Int = 0,
    val wsubScan: Int = 0,
    val wsubFound: String = "none",
    val wsubFb: Int = 0,
    val wsubPeers: List<String> = emptyList(),
    val wsubLinks: List<Int> = emptyList(),
    // Daftar per-peer (urutan sama dengan peers=)
    val wsubFills: List<Int> = emptyList(),
    val wsubLat: Int = 70,
    val wsubTrim: Int = 0,
    val wsubMax: Int = 4,

    // Per-peer detailed status (multi-sub)
    val peerDetails: List<PeerDetail> = emptyList(),

    // Rolling history for real-time charts (last ~60 samples)
    val fillHistory: List<FillSample> = emptyList(),
    val undHistory: List<Int> = emptyList(),
    val gapHistory: List<Int> = emptyList(),

    // FX (firmware >= 2.8): polaritas sub + auto-standby
    val subInv: Boolean = false,
    val standbyMin: Int = 0,
    val inStandby: Boolean = false,

    // VER fw=... role=... (firmware >= 2.9.x wireless master)
    val fwVersion: String = "",
    val fwRole: String = "",

    // Voicing sub wireless (WSUBV)
    val voice: VoiceParams = VoiceParams(),
    val voiceKnown: Boolean = false,

    // PEQ sub wireless, 7 band di chip slave (WSUBE; firmware >= 2.9.27)
    val peq: PeqParams = PeqParams(),
    val peqKnown: Boolean = false,

    // Fault chip 2 (FAULT2, env 2-chip)
    val fault2Raw: String = "",

    // EQ center / sub (mode 3): EQC / EQS
    val eqCtr: List<Float> = List(15) { 0f },
    val eqSub: List<Float> = List(15) { 0f },

    // Diagnosa (3.11): kv terakhir tiap baris diag (WSUB, WSUBT, WSUBC, BTD, INFO, CLIP, MOD, DAC, DAC2, EQP2)
    val tags: Map<String, Map<String, String>> = emptyMap(),
    val rawByHead: Map<String, String> = emptyMap(),         // baris mentah terakhir per head (untuk laporan)
    val slaveDiag: Map<String, Map<String, String>> = emptyMap(),   // WSUBD per MAC (HURUF BESAR)
    val btEvents: List<String> = emptyList(),
    val faultHistory: List<FaultEvent> = emptyList(),
    val baseline: Map<String, Long> = emptyMap(),             // counter saat "mulai ukur"
    val baselineAtMs: Long = 0L,
    /** Sesi ukur diagnosa: Start / Pause / Stop. */
    val measureRunning: Boolean = false,
    val measurePaused: Boolean = false,
    /** Waktu terakumulasi (ms) dari segmen yang sudah selesai (pause/stop). */
    val measureAccumMs: Long = 0L,
    /** Awal segmen berjalan saat ini; 0 bila tidak running atau sedang pause. */
    val measureSegmentStartMs: Long = 0L,
    val lastRxMs: Long = 0L,
    val saved: Boolean? = null,

    val lastLines: List<String> = emptyList(),
    /**
     * Log khusus sesi ukur (Start→Stop). Hanya baris non-quiet sejak Start;
     * dikosongkan tiap Start. Dipakai laporan sesi & kartu log di Diagnosa.
     */
    val sessionLines: List<String> = emptyList(),
    val lastError: String = "",
    /** Jumlah baris `ERR` sejak koneksi (naik terus; dipakai untuk mendeteksi perintah yang ditolak saat apply preset). */
    val errCount: Int = 0,
) {
    /** Durasi jendela ukur aktif (tidak menghitung waktu pause), dalam ms. */
    fun measureElapsedMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (baselineAtMs <= 0L && !measureRunning && measureAccumMs == 0L) return 0L
        val live = if (measureRunning && !measurePaused && measureSegmentStartMs > 0L) {
            (nowMs - measureSegmentStartMs).coerceAtLeast(0L)
        } else 0L
        return measureAccumMs + live
    }

    fun measureElapsedSec(nowMs: Long = System.currentTimeMillis()): Long =
        measureElapsedMs(nowMs) / 1000L
    fun tag(t: String, k: String): String? = tags[t]?.get(k)
    fun tagInt(t: String, k: String): Int? = tags[t]?.get(k)?.toIntOrNull()
    /** HPF satelit dan LPF sub bisa diatur terpisah (`xover_hp` / `xover_lp`). */
    val hasXover2 get() = "xover2" in caps
    /** Crossover diperluas sampai 300 Hz (firmware CAPS `xext`, hanya berlaku di mode 2 = 1 chip 2.1). */
    val hasXext get() = "xext" in caps
    /** Batas atas slider crossover untuk mode saat ini: 300 Hz di mode 2 bila firmware mendukung, selain itu 150 Hz (tabel). */
    val xoverMax get() = if (hasXext && mode == 2) 300 else 150
    /** 2-way aktif mono tersedia (hanya bermakna di mode 2). */
    val hasTway get() = "tway" in caps
    /** Psychoacoustic bass tersedia (semua mode). */
    val hasPbass get() = "pbass" in caps
    /** Loudness sub tersedia (bermakna di mode 1–4; mode 0 tanpa sub). */
    val hasSubloud get() = "subloud" in caps
    /** 2-way sedang aktif: filter tabel xover tidak dipakai (mode 2 bila `tway` nyala; mode 5 = 2 chip stereo 2-way, selalu aktif). */
    val twayActive get() = hasTway && ((mode == 2 && tway) || mode == 5 || mode == 6)
    /** Mode 5: 2 chip stereo 2-way — chip 1 = 2 tweeter (HPF), chip 2 = 2 woofer (LPF), crossover = `tway_hz`. */
    val isStereo2Way get() = mode == 5
    /** Mode 6: 1 chip mono 2-way — OUT_L = tweeter (HPF), OUT_R = woofer (LPF), `tway_hz`; selalu aktif. */
    val isMono2Way get() = mode == 6
    val hasClip get() = "clip" in caps
    /** Terhubung DAN data pertama sudah diterima — dipakai UI sebagai syarat menampilkan/menyimpan nilai. */
    val ready get() = connected && synced
    /** Firmware mendukung `clipoff` (kembalikan clipper ke default chip). */
    val hasClipOff get() = "clipoff" in caps
    /** Dari `CLIP … en=0|1`; null di firmware lama (tidak ada field `en`). */
    val clipEnabled: Boolean? get() = tag("CLIP", "en")?.let { it == "1" }
    val hasMod get() = "mod" in caps
    val hasDac get() = "dac" in caps
    val hasEqp get() = "eqp" in caps
    val hasMixmode get() = "mixmode" in caps
    val hasInfo get() = "info" in caps

    val hasEqband get() = "eqband" in caps
    val hasLvl get() = "lvl" in caps
    val hasLvl2 get() = "lvl2" in caps
    val hasV3 get() = "v3" in caps
    val hasWsub get() = "wsub" in caps
    val hasWsubTune get() = "wsub_tune" in caps
    /** Nilai aktif `wsub_extra` dari baris WSUBT (`extra_max`); null bila belum ada. */
    val wsubExtra: Int? get() = tagInt("WSUBT", "extra_max")
    val hasCtr get() = "ctr" in caps
    val hasSave get() = "save" in caps
    val hasSubInv get() = "subinv" in caps
    val hasStandby get() = "standby" in caps
    val hasEqc get() = "eqc" in caps
    val hasWlvl get() = "wlvl" in caps
    val hasVoice get() = "wvoice" in caps || "voice_basic" in caps || voiceKnown
    val hasPeq get() = "wsub_peq" in caps || peqKnown
    val is21 get() = mode == 1 || mode == 2
    val is31 get() = mode == 3
    val isWireless get() = mode == 4

    /** Firmware: is21() == (mode != 0) -> band EQ 0..2 dicadangkan untuk crossover & `eq off` ditolak (mode 1,2,3,4). */
    val xoverLocksEq get() = mode != 0
    /** Mode 2 (1-chip 2.1) dan mode 5 (stereo 2-way) mengunci mixer (`ERR mixer locked_in_1chip_21` / `locked_in_2way`). */
    val mixerLocked get() = mode == 2 || mode == 5 || mode == 6
    /** Mode yang diterima firmware (cermin modeSupported()): master nirkabel hanya mode 4; mode 1/3/5 butuh chip 2 (CAPS ctr). */
    fun modeAllowed(m: Int): Boolean = when {
        caps.isEmpty() -> true
        hasWsub -> m == 4
        m == 4 -> false
        m == 1 || m == 3 || m == 5 -> hasCtr
        else -> m in 0..2 || m == 6   // mode 6 (1 chip 2-way) tidak butuh chip 2
    }

    /** Mode 0 tidak punya sub -> subinv ditolak (`ERR subinv no_sub`). */
    val hasSubPath get() = mode != 0

    fun modeName() = when (mode) {
        0 -> "Stereo"
        1 -> "2.1 Dual-chip"
        2 -> "2.1 Single-chip"
        3 -> "3.1"
        4 -> "Wireless Sub"
        5 -> "2-way Stereo"
        6 -> "2-way 1 Chip"
        else -> "Mode $mode"
    }

    companion object {
        const val HISTORY_MAX = 48
        val BAND_FREQS = listOf(20, 32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000, 3150, 5000, 8000, 16000)
    }
}

/** Urutan kunci fault pada baris `FAULT` / `FAULT2` firmware (printFaultLine). */
val FAULT_KEYS = listOf("oc_r", "oc_l", "dc_r", "dc_l", "uv", "ov", "clk", "bq", "crc", "ot_sd", "ot_w")

data class FaultEvent(
    val timeMs: Long,
    val chip: Int,              // 1 = FAULT, 2 = FAULT2
    val flags: List<String>,    // subset FAULT_KEYS yang bernilai 1
    val raw: String = "",
)

data class FillSample(
    val t: Long = System.currentTimeMillis(),
    val fillMs: Int = 0,
    val und: Int = 0,
    val gap: Int = 0,
    val rxa: Int = -1,
)

data class PeerDetail(
    val mac: String,
    val slot: Int = -1,
    val link: Boolean = false,
    val fillMs: Int = 0,
    val und: Int = 0,
    val gap: Int = 0,
    val rate: Int = 0,
    val rxa: Int = -1,       // paket audio diterima slave / detik (-1 = slave lama / putus)
    val rxc: Int = -1,       // paket ctrl / detik
    val rej: Int = 0,        // paket ditolak slave (MAC bukan master)
    val stx: Int = 0,        // STATUS slave gagal terkirim
    val rssi: Int = 0,
    val hiz: Boolean = false,
    val flt: Int = -1,       // bitmask fault amp slave, -1 = tidak diketahui
    val fcnt: Int = 0,       // jumlah kejadian fault sejak boot slave
    val upSec: Int = -1,     // uptime slave (dtk), -1 = slave < 2.9.16
    val rst: Int = -1,       // alasan reset terakhir slave
    val lvlDeciDb: Int? = null, // level sub (WLVL), null = n/a
)

/** Parameter voicing sub (firmware: wsub_voice on hpf pk_hz db10 q10 harm limit). */
data class VoiceParams(
    val on: Boolean = false,
    val hpf: Int = 32,     // 20..120 Hz (subsonic HPF, orde-4)
    val pkHz: Int = 55,    // 30..150 Hz (peaking)
    val pkDb10: Int = 0,   // -60..90 (dB x10)
    val pkQ10: Int = 10,   // 5..30 (Q x10)
    val harm: Int = 0,     // 0..100 (pembangkit harmonik)
    val limit: Int = 100,  // 50..100 (limiter lunak)
) {
    // harm/limit selalu dikirim nonaktif (0/100): firmware menolak nilai lain.
    fun command() = "wsub_voice ${if (on) 1 else 0} $hpf $pkHz $pkDb10 $pkQ10 0 100"
}

private val FAULT_BITS = listOf("OC-L", "OC-R", "DC-L", "DC-R", "UV", "OV", "CLK", "BQ", "CRC", "OT-SD", "OT-W")

/** Decode bitmask fault slave (bit n = oc_l, oc_r, dc_l, dc_r, uv, ov, clk, bq, crc, ot_sd, ot_w). */
fun faultNames(mask: Int): List<String> =
    if (mask <= 0) emptyList() else FAULT_BITS.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }

/** ID preset unik (UUID). Preset lama tanpa id mendapat id deterministik saat dibaca (lihat PresetJson). */
fun newPresetId(): String = java.util.UUID.randomUUID().toString()

data class Preset(
    val name: String,
    val id: String = newPresetId(),
    val mode: Int = 0,
    val xover: Int = 100,
    /** LPF sub bila berbeda dari [xover] (HPF satelit); null = sama dengan [xover] (preset lama). */
    val xoverLp: Int? = null,
    val master: Int = 80,
    val sat: Int = 100,
    val sub: Int = 100,
    val ctr: Int = 100,
    val gain: Int = 18,
    val eqOn: Boolean = true,
    val ll: Float = 1f, val lr: Float = 0f,
    val rl: Float = 0f, val rr: Float = 1f,
    val eqLeft: List<Float> = List(15) { 0f },
    val eqRight: List<Float> = List(15) { 0f },
    val created: String = "",
    // --- 3.11 ---
    /** "full" = semua; "eq" = hanya EQ (+EQ on/off); "voice" = hanya voicing sub + trim;
     *  "scene" = EQ + voicing sub (tanpa mode/xover/volume → aman di mode 4 dan tidak melonjakkan volume). */
    val kind: String = "full",
    val builtin: Boolean = false,
    val note: String = "",
    val clip: List<Int>? = null,          // [gain, ml, mr] (deci-dB)
    val subInv: Boolean? = null,
    val standby: Int? = null,
    val trim: Int? = null,
    val voice: VoiceParams? = null,
    val eqCtr: List<Float>? = null,
    val eqSub: List<Float>? = null,
    /** PEQ sub wireless (7 band, firmware master dengan CAPS `wsub_peq`); null = preset tidak menyentuh PEQ. */
    val peq: PeqParams? = null,
)
