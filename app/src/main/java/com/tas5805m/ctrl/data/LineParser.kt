package com.tas5805m.ctrl.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parser murni (tanpa Android): baris firmware -> AmpState baru. Dipisah dari SppClient agar bisa diuji unit di JVM.
 */
object LineParser {
    const val LOG_MAX = 200

    /** Baris yang di-poll terus (tidak masuk log; tetap diparse & disimpan di rawByHead). */
    val QUIET_HEADS = setOf(
        "WLVL", "LVL", "LVL2", "WSUB", "WSUBS", "WSUBD", "WSUBT", "WSUBC", "WSUBV", "BTD", "BTEV",
        "MODE", "STATE", "FX", "VER", "TWAY", "SUBLOUD", "PBASS",
    )

    /** `OK <perintah>` ini tidak menghapus pesan ERR terakhir (hanya balasan sinkron/poll). */
    private val SYNC_OK = setOf("status", "dump", "eqdump", "fault", "caps", "info", "saved", "modget", "clipget", "dacget")

    fun apply(s: AmpState, line: String, nowMs: Long = System.currentTimeMillis()): AmpState {
        val head = line.substringBefore(' ')
        var ns = s.copy(lastRxMs = nowMs, rawByHead = s.rawByHead + (head to line))
        if (head !in QUIET_HEADS) {
            ns = ns.copy(lastLines = (listOf(line) + ns.lastLines).take(LOG_MAX))
        }
        when (head) {
            "MODE" -> ns = parseMode(ns, line)
            "STATE" -> ns = parseState(ns, line)
            "TWAY" -> ns = parseTway(ns, line)
            "SUBLOUD" -> ns = parseSubloud(ns, line)
            "PBASS" -> ns = parsePbass(ns, line)
            "FAULT" -> ns = addFaultEvent(parseFault(ns, line), 1, line, nowMs)
            "FAULT2" -> ns = addFaultEvent(
                ns.copy(fault2Raw = line, tags = ns.tags + ("FAULT2" to kvOf(line))), 2, line, nowMs
            )
            "EQ" -> ns = parseEq(ns, line)
            "EQC" -> ns = ns.copy(eqCtr = parseBands(line, ns.eqCtr))
            "EQS" -> ns = ns.copy(eqSub = parseBands(line, ns.eqSub))
            "EQM" -> ns = ns.copy(eqm = line.split(" ").lastOrNull()?.toIntOrNull() ?: ns.eqm)
            "EQP" -> ns = parseEqp(ns, line)
            "LVL" -> ns = parseLvl(ns, line, false)
            "LVL2" -> ns = parseLvl(ns, line, true)
            "CAPS" -> ns = ns.copy(caps = line.removePrefix("CAPS ").trim().split(Regex("\\s+")).toSet())
            "FX" -> ns = parseFx(ns, line)
            "VER" -> ns = parseVer(ns, line).let { it.copy(tags = it.tags + ("VER" to kvOf(line))) }
            "WSUB" -> ns = parseWsub(ns, line).let { it.copy(tags = it.tags + ("WSUB" to kvOf(line))) }
            "WSUBS" -> ns = parseWsubSlot(ns, line)
            "WSUBV" -> ns = parseVoice(ns, line)
            "WLVL" -> ns = parseWlvl(ns, line)
            "WSUBD" -> ns = parseWsubDiag(ns, line)
            "WSUBT", "WSUBC", "BTD", "INFO", "CLIP", "MOD", "DAC", "DAC2", "EQP2" ->
                ns = ns.copy(tags = ns.tags + (head to kvOf(line)))
            "BTEV" -> ns = ns.copy(
                btEvents = (ns.btEvents + (stamp(nowMs) + " " + line.removePrefix("BTEV ").trim())).takeLast(40)
            )
            "SAVED" -> ns = ns.copy(saved = line.trim().endsWith("1"))
            "ERR" -> ns = ns.copy(lastError = line, errCount = ns.errCount + 1)
            "OK" -> {
                val what = line.removePrefix("OK").trim()
                if (what !in SYNC_OK && ns.lastError.isNotEmpty()) ns = ns.copy(lastError = "")
            }
        }
        return ns
    }

    private fun stamp(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

    private fun addFaultEvent(s: AmpState, chip: Int, line: String, nowMs: Long): AmpState {
        val kv = kvOf(line)
        val flags = FAULT_KEYS.filter { kv[it] == "1" }
        if (flags.isEmpty()) return s
        val last = s.faultHistory.lastOrNull { it.chip == chip }
        if (last != null && last.flags == flags && nowMs - last.timeMs < 5000) return s
        return s.copy(faultHistory = (s.faultHistory + FaultEvent(nowMs, chip, flags, line)).takeLast(50))
    }

    private fun parseWsubDiag(s: AmpState, line: String): AmpState {
        val kv = kvOf(line)
        val mac = kv["mac"]?.uppercase() ?: return s
        return s.copy(slaveDiag = s.slaveDiag + (mac to kv))
    }

    /** "KEY=VAL KEY2=VAL2 …" -> map (token tanpa '=' diabaikan). */
    fun kvOf(line: String): Map<String, String> {
        val m = HashMap<String, String>()
        for (tok in line.split(Regex("\\s+")).drop(1)) {
            val i = tok.indexOf('=')
            if (i > 0) m[tok.substring(0, i)] = tok.substring(i + 1)
        }
        return m
    }

    fun intList(v: String?): List<Int>? =
        v?.split(",")?.map { it.trim().toIntOrNull() ?: 0 }

    private fun parseMode(s: AmpState, line: String): AmpState {
        val parts = line.split(Regex("\\s+"))
        var mode = s.mode
        var xover = s.xover; var xoverLp = s.xoverLp; var sawLp = false
        var master = s.master; var sat = s.sat; var sub = s.sub; var ctr = s.ctr
        if (parts.size >= 2) mode = parts[1].toIntOrNull() ?: mode
        for (p in parts.drop(2)) {
            val kv = p.split("=")
            if (kv.size != 2) continue
            val v = kv[1].toIntOrNull() ?: continue
            when (kv[0]) {
                "xover", "xhp" -> xover = v
                "xlp" -> { xoverLp = v; sawLp = true }
                "master" -> master = v
                "sat" -> sat = v
                "sub" -> sub = v
                "ctr" -> ctr = v
            }
        }
        // Firmware lama tidak mengirim xlp: LPF = HPF (satu nilai xover untuk keduanya)
        if (!sawLp) xoverLp = xover
        return s.copy(mode = mode, xover = xover, xoverLp = xoverLp, master = master, sat = sat, sub = sub, ctr = ctr)
    }

    private fun parseState(s: AmpState, line: String): AmpState {
        var gain = s.gain; var amp = s.ampstate; var eq = s.eqOn
        var ll = s.ll; var lr = s.lr; var rl = s.rl; var rr = s.rr
        for ((k, v) in kvOf(line)) {
            when (k) {
                "gain" -> gain = v.toIntOrNull() ?: gain
                "amp" -> amp = v
                "eq" -> eq = v == "1" || v == "on"
                "ll" -> ll = v.toFloatOrNull() ?: ll
                "lr" -> lr = v.toFloatOrNull() ?: lr
                "rl" -> rl = v.toFloatOrNull() ?: rl
                "rr" -> rr = v.toFloatOrNull() ?: rr
            }
        }
        return s.copy(gain = gain, ampstate = amp, eqOn = eq, ll = ll, lr = lr, rl = rl, rr = rr)
    }

    private fun parseFx(s: AmpState, line: String): AmpState {
        val kv = kvOf(line)
        return s.copy(
            subInv = kv["inv"]?.let { it == "1" } ?: s.subInv,
            standbyMin = kv["stby"]?.toIntOrNull() ?: s.standbyMin,
            inStandby = kv["sb"]?.let { it == "1" } ?: s.inStandby,
        )
    }

    private fun parseVer(s: AmpState, line: String): AmpState {
        val kv = kvOf(line)
        return s.copy(fwVersion = kv["fw"] ?: s.fwVersion, fwRole = kv["role"] ?: s.fwRole)
    }

    private fun parseFault(s: AmpState, line: String): AmpState {
        val map = mutableMapOf<String, Int>()
        for ((k, v) in kvOf(line)) {
            map[k] = if (v.startsWith("0x")) v.removePrefix("0x").toIntOrNull(16) ?: 0 else v.toIntOrNull() ?: 0
        }
        return s.copy(faultRaw = line, faults = map)
    }

    /** "EQC 0,1,2,…" / "EQS …" -> 15 nilai. */
    private fun parseBands(line: String, old: List<Float>): List<Float> {
        val vals = line.substringAfter(' ').split(",").map { it.trim().toFloatOrNull() ?: 0f }
        return if (vals.size >= 15) vals.take(15) else old
    }

    private fun parseEq(s: AmpState, line: String): AmpState {
        val isLeft = line.startsWith("EQ l=")
        if (!isLeft && !line.startsWith("EQ r=")) return s
        val vals = line.substring(5).split(",").map { it.trim().toFloatOrNull() ?: 0f }
        return if (vals.size >= 15) {
            if (isLeft) s.copy(eqLeft = vals.take(15)) else s.copy(eqRight = vals.take(15))
        } else s
    }

    private fun parseEqp(s: AmpState, line: String): AmpState {
        var l = s.eqpLeft; var r = s.eqpRight
        for ((k, v) in kvOf(line)) {
            val n = v.toIntOrNull() ?: continue
            if (k == "l") l = n
            if (k == "r") r = n
        }
        return s.copy(eqpLeft = l, eqpRight = r)
    }

    private fun parseLvl(s: AmpState, line: String, is2: Boolean): AmpState {
        var l: Int? = null; var r: Int? = null
        for ((k, v) in kvOf(line)) {
            val n = v.toIntOrNull() ?: continue
            if (k == "l") l = n
            if (k == "r") r = n
        }
        return if (is2) s.copy(lvl2L = l ?: s.lvl2L, lvl2R = r ?: s.lvl2R)
        else s.copy(lvlL = l ?: s.lvlL, lvlR = r ?: s.lvlR)
    }

    /** `PBASS on=0|1 amt=<0..100> fc=<40..150> act=0|1` (semua mode, CAPS pbass). */
    private fun parsePbass(s: AmpState, line: String): AmpState {
        val kv = kvOf(line)
        return s.copy(
            pbass = kv["on"]?.let { it == "1" } ?: s.pbass,
            pbassAmt = kv["amt"]?.toIntOrNull()?.coerceIn(0, 100) ?: s.pbassAmt,
            pbassFc = kv["fc"]?.toIntOrNull()?.coerceIn(40, 150) ?: s.pbassFc,
            pbassActive = kv["act"]?.let { it == "1" } ?: s.pbassActive,
        )
    }

    /** `SUBLOUD on=0|1 db10=<0..60> lo=<0..99> hi=<lo+1..100> cur=<dB×10>` (firmware ≥ 2.9.25, mode 1–4). */
    private fun parseSubloud(s: AmpState, line: String): AmpState {
        val kv = kvOf(line)
        return s.copy(
            subloud = kv["on"]?.let { it == "1" } ?: s.subloud,
            subloudDb10 = kv["db10"]?.toIntOrNull() ?: s.subloudDb10,
            subloudLo = kv["lo"]?.toIntOrNull() ?: s.subloudLo,
            subloudHi = kv["hi"]?.toIntOrNull() ?: s.subloudHi,
            subloudCur = kv["cur"]?.toIntOrNull() ?: s.subloudCur,
            subloudPhone = kv["ph"]?.let { it == "1" } ?: s.subloudPhone,
            phoneVol = kv["pv"]?.toIntOrNull() ?: s.phoneVol,
            subloudEff = kv["eff"]?.toIntOrNull() ?: s.subloudEff,
        )
    }

    /** `TWAY on=0|1 hz=<fc> min=<hz> max=<hz> fs=<Hz>` (firmware ≥ 2.9.23, hanya di mode 2). */
    private fun parseTway(s: AmpState, line: String): AmpState {
        val kv = kvOf(line)
        return s.copy(
            tway = kv["on"]?.let { it == "1" } ?: s.tway,
            twayHz = kv["hz"]?.toIntOrNull() ?: s.twayHz,
            twayMin = kv["min"]?.toIntOrNull() ?: s.twayMin,
            twayMax = kv["max"]?.toIntOrNull() ?: s.twayMax,
        )
    }

    private fun parseVoice(s: AmpState, line: String): AmpState {
        val kv = kvOf(line)
        val o = s.voice
        val v = VoiceParams(
            on = kv["on"]?.let { it == "1" } ?: o.on,
            hpf = kv["hpf"]?.toIntOrNull() ?: o.hpf,
            pkHz = kv["pk"]?.toIntOrNull() ?: o.pkHz,
            pkDb10 = kv["db"]?.toIntOrNull() ?: o.pkDb10,
            pkQ10 = kv["q"]?.toIntOrNull() ?: o.pkQ10,
            harm = kv["harm"]?.toIntOrNull() ?: o.harm,
            limit = kv["lim"]?.toIntOrNull() ?: o.limit,
        )
        return s.copy(voice = v, voiceKnown = true)
    }

    /** `WLVL s0=-250 s1=na` -> level per slot (na = offline / slave lama / basi). */
    private fun parseWlvl(s: AmpState, line: String): AmpState {
        val kv = kvOf(line)
        if (s.peerDetails.isEmpty()) return s
        val d = s.peerDetails.mapIndexed { i, p ->
            val key = "s${if (p.slot >= 0) p.slot else i}"
            val raw = kv[key]
            if (raw == null) p else p.copy(lvlDeciDb = raw.toIntOrNull())
        }
        return s.copy(peerDetails = d)
    }

    /** `WSUBS slot= mac= link= rxa= rxc= rej= stx= fill= und= gap= … flt= fcnt= up= rst= rssi=` (satu baris per slave). */
    private fun parseWsubSlot(s: AmpState, line: String): AmpState {
        val kv = kvOf(line)
        val mac = kv["mac"] ?: return s
        val list = s.peerDetails.toMutableList()
        val idx = list.indexOfFirst { it.mac.equals(mac, ignoreCase = true) }
        val base = if (idx >= 0) list[idx] else PeerDetail(mac = mac)
        val up = base.copy(
            slot = kv["slot"]?.toIntOrNull() ?: base.slot,
            link = kv["link"]?.let { it == "1" } ?: base.link,
            fillMs = kv["fill"]?.toIntOrNull() ?: base.fillMs,
            und = kv["und"]?.toIntOrNull() ?: base.und,
            gap = kv["gap"]?.toIntOrNull() ?: base.gap,
            rxa = kv["rxa"]?.toIntOrNull() ?: -1,
            rxc = kv["rxc"]?.toIntOrNull() ?: -1,
            rej = kv["rej"]?.toIntOrNull() ?: 0,
            stx = kv["stx"]?.toIntOrNull() ?: 0,
            flt = kv["flt"]?.toIntOrNull() ?: -1,
            fcnt = kv["fcnt"]?.toIntOrNull() ?: 0,
            upSec = kv["up"]?.toIntOrNull() ?: -1,
            rst = kv["rst"]?.toIntOrNull() ?: -1,
            rssi = kv["rssi"]?.toIntOrNull() ?: 0,
        )
        if (idx >= 0) list[idx] = up else list.add(up)
        return s.copy(peerDetails = list)
    }

    /**
     * `WSUB peers=A,B links=1,0 fill=12,0 und=0,0 gap=0,0 rate=44100,0 hiz=0,0 … ch= lat= trim= max= scan= found= fb=`
     * Dengan multi-sub, fill/und/gap/rate/hiz/links berisi DAFTAR dipisah koma (satu nilai per peer, urutan = peers=).
     */
    private fun parseWsub(s: AmpState, line: String): AmpState {
        val kv = kvOf(line)

        val peers = kv["peers"]?.let { if (it == "none" || it.isEmpty()) emptyList() else it.split(",") } ?: s.wsubPeers
        val peer = kv["peer"] ?: s.wsubPeer
        val links = intList(kv["links"]) ?: s.wsubLinks
        val fills = intList(kv["fill"]) ?: s.wsubFills
        val unds = intList(kv["und"]) ?: emptyList()
        val gaps = intList(kv["gap"]) ?: emptyList()
        val rates = intList(kv["rate"]) ?: emptyList()
        val hizs = intList(kv["hiz"]) ?: emptyList()

        // peer acuan untuk grafik/ringkasan: slave pertama yang link, kalau tidak ada: slave pertama
        val ref = links.indexOf(1).let { if (it < 0) 0 else it }
        val fill = fills.getOrElse(ref) { 0 }
        val und = unds.getOrElse(ref) { 0 }
        val gap = gaps.getOrElse(ref) { 0 }
        val rate = rates.getOrElse(ref) { 0 }
        val hiz = hizs.getOrElse(ref) { 0 }
        val link = kv["link"]?.toIntOrNull() ?: s.wsubLink
        val scan = kv["scan"]?.toIntOrNull() ?: s.wsubScan
        val found = kv["found"] ?: s.wsubFound
        val fb = kv["fb"]?.toIntOrNull() ?: s.wsubFb
        val lat = kv["lat"]?.toIntOrNull() ?: s.wsubLat
        val trim = kv["trim"]?.toIntOrNull() ?: s.wsubTrim
        val max = kv["max"]?.toIntOrNull() ?: s.wsubMax

        // Detail per peer; field tambahan dari WSUBS (rxa/rxc/flt/…) dipertahankan per MAC
        val prev = s.peerDetails.associateBy { it.mac.uppercase() }
        val details = peers.mapIndexed { i, mac ->
            val base = prev[mac.uppercase()] ?: PeerDetail(mac = mac)
            base.copy(
                mac = mac,
                link = links.getOrNull(i) == 1,
                fillMs = fills.getOrElse(i) { 0 },
                und = unds.getOrElse(i) { 0 },
                gap = gaps.getOrElse(i) { 0 },
                rate = rates.getOrElse(i) { 0 },
                hiz = hizs.getOrElse(i) { 0 } == 1,
            )
        }.ifEmpty {
            if (peer != "none" && peer.isNotEmpty()) {
                listOf(PeerDetail(mac = peer, link = link == 1, fillMs = fill, und = und, gap = gap, rate = rate, hiz = hiz == 1))
            } else emptyList()
        }

        // History: hanya tambah bila fill/und/gap berubah (hemat alokasi + recomposition)
        val last = s.fillHistory.lastOrNull()
        val changed = last == null || last.fillMs != fill || last.und != und || last.gap != gap
        val hist: List<FillSample>
        val undH: List<Int>
        val gapH: List<Int>
        if (changed) {
            val sample = FillSample(fillMs = fill, und = und, gap = gap)
            val maxH = AmpState.HISTORY_MAX
            hist = if (s.fillHistory.size >= maxH) s.fillHistory.drop(1) + sample else s.fillHistory + sample
            undH = if (s.undHistory.size >= maxH) s.undHistory.drop(1) + und else s.undHistory + und
            gapH = if (s.gapHistory.size >= maxH) s.gapHistory.drop(1) + gap else s.gapHistory + gap
        } else {
            hist = s.fillHistory
            undH = s.undHistory
            gapH = s.gapHistory
        }
        return s.copy(
            wsubPeer = peer, wsubLink = link, wsubFillMs = fill, wsubUnd = und, wsubGap = gap,
            wsubRate = rate, wsubHiz = hiz, wsubScan = scan, wsubFound = found, wsubFb = fb,
            wsubPeers = peers, wsubLinks = links, wsubFills = fills,
            wsubLat = lat, wsubTrim = trim, wsubMax = max,
            peerDetails = details,
            fillHistory = hist,
            undHistory = undH,
            gapHistory = gapH
        )
    }
}
