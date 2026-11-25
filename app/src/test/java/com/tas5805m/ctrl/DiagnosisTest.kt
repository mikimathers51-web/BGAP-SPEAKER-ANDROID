package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.Diagnosis
import com.tas5805m.ctrl.data.Level
import com.tas5805m.ctrl.data.LineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Format baris sama dengan printf di firmware 2.9.19 (main.cpp / wireless_sub_master.cpp). */
class DiagnosisTest {
    private val mac = "AA:BB:CC:DD:EE:01"

    private fun wsub(err: String = "none", nomem: Int = 0, ackfail: Int = 0, links: Int = 1) =
        "WSUB peers=$mac peer=$mac links=$links link=$links fill=40 und=0 gap=0 rate=44100 hiz=0 txf=0 lat=70 trim=0 " +
            "err=$err ackok=500 ackfail=$ackfail nomem=$nomem ch=6 max=4 scan=0 drop=0 heap=90000 heapmin=60000 heapblk=50000 " +
            "mfw=19 found=none fb=0"

    private fun wsubs(rxa: Int = 43, und: Int = 0, link: Int = 1) =
        "WSUBS slot=0 mac=$mac link=$link rxa=$rxa rxc=10 rej=0 stx=0 fill=40 und=$und gap=0 tak=1 taf=0 tdrop=0 tin=0 " +
            "flt=-1 fcnt=0 up=120 rst=1 rssi=-50"

    private fun wsubd(fw: String = "2.9.19") =
        "WSUBD slot=0 mac=$mac g40=0 g60=0 g80=0 g100=0 g200=0 g500=0 gmax=0 adapt=0 fmin=0 jmax=0 pkt=64 est=43.0 need=40.0 fw=$fw fillmin=40 age=1"

    private fun feed(vararg lines: String): AmpState {
        var s = AmpState(connected = true, caps = setOf("wsub", "wlvl", "lvl", "wvoice", "eqband"))
        for (l in lines) s = LineParser.apply(s, l, 1_000L)
        return s
    }

    @Test
    fun faultChip1IsReported() {
        val s = feed("FAULT err0=0x01 err1=0x00 err2=0x00 ot_warn=0x00 oc_r=1 oc_l=0 dc_r=0 dc_l=0 uv=0 ov=0 clk=0 bq=0 crc=0 ot_sd=0 ot_w=0")
        val f = Diagnosis.diagnose(s)
        assertTrue(f.any { it.level == Level.ERR && it.title.contains("OC-R") })
        assertEquals(1, s.faultHistory.size)
    }

    @Test
    fun faultHistoryDedupesRepeatedReads() {
        val line = "FAULT err0=0x01 err1=0x00 err2=0x00 ot_warn=0x00 oc_r=1 oc_l=0 dc_r=0 dc_l=0 uv=0 ov=0 clk=0 bq=0 crc=0 ot_sd=0 ot_w=0"
        var s = feed(line)
        s = LineParser.apply(s, line, 2_000L)
        assertEquals(1, s.faultHistory.size)
    }

    @Test
    fun nomemIsError() {
        val f = Diagnosis.diagnose(feed(wsub(err = "NO_MEM", nomem = 12), wsubs(), wsubd()))
        assertTrue(f.any { it.level == Level.ERR && it.title.contains("Antrean TX") })
    }

    @Test
    fun healthyLinkHasNoWarnings() {
        val f = Diagnosis.diagnose(feed(wsub(), wsubs(), wsubd()))
        assertFalse(f.any { it.level == Level.ERR || it.level == Level.WARN })
        assertTrue(f.any { it.level == Level.OK })
    }

    @Test
    fun lowPacketRateUsesPktFromSlaveNotConstant86() {
        // 43 paket/dtk adalah normal untuk pkt=64 (bukan 86) → tidak boleh dianggap rendah
        val ok = Diagnosis.diagnose(feed(wsub(), wsubs(rxa = 43), wsubd()))
        assertFalse(ok.any { it.title.contains("penerimaan audio rendah") })
        val low = Diagnosis.diagnose(feed(wsub(), wsubs(rxa = 10), wsubd()))
        assertTrue(low.any { it.level == Level.WARN && it.title.contains("penerimaan audio rendah") })
    }

    @Test
    fun offlineSlaveIsError() {
        val f = Diagnosis.diagnose(feed(wsub(links = 0), wsubs(link = 0), wsubd()))
        assertTrue(f.any { it.level == Level.ERR && it.title.contains("offline") })
    }

    @Test
    fun oldSlaveFirmwareIsFlagged() {
        val f = Diagnosis.diagnose(feed(wsub(), wsubs(), wsubd(fw = "2.9.17")))
        assertTrue(f.any { it.level == Level.INFO && it.title.contains("2.9.17") })
    }

    @Test
    fun underrunDeltaIsMeasuredFromBaseline() {
        val s1 = feed(wsub(), wsubs(und = 5), wsubd())
        val s1b = s1.copy(baseline = Diagnosis.counters(s1))
        // tanpa perubahan sejak baseline → bersih
        assertFalse(Diagnosis.diagnose(s1b).any { it.title.contains("buffer kurang") })
        val s2 = LineParser.apply(s1b, wsubs(und = 9), 2_000L)
        assertTrue(Diagnosis.diagnose(s2).any { it.level == Level.WARN && it.title.contains("buffer kurang") })
    }

    @Test
    fun pollLinesStayOutOfLog() {
        var s = feed()
        s = LineParser.apply(s, "LVL l=-250 r=-310", 1L)
        s = LineParser.apply(s, "WLVL s0=-250", 1L)
        s = LineParser.apply(s, "OK status", 1L)
        assertEquals(listOf("OK status"), s.lastLines)
        assertEquals("LVL l=-250 r=-310", s.rawByHead["LVL"])
        assertEquals(-250, s.lvlL)
    }

    @Test
    fun voiceGainWarningWithoutLimiter() {
        val s = feed("WSUBV on=1 hpf=32 pk=55 db=70 q=10 harm=0 lim=100")
        assertTrue(Diagnosis.voiceWarning(s)?.contains("tanpa limiter") == true)
    }

    @Test
    fun freePeakingNeedsSlave2920OrNewer() {
        assertFalse(Diagnosis.voiceFreePeak(feed(wsub(), wsubs(), wsubd(fw = "2.9.19"))))
        assertTrue(Diagnosis.voiceFreePeak(feed(wsub(), wsubs(), wsubd(fw = "2.9.20"))))
        assertFalse(Diagnosis.voiceFreePeak(feed(wsub(), wsubs())))   // versi slave belum diketahui → konservatif
    }

    @Test
    fun peakAboveFourDbWarnsWithoutLimiter() {
        val base = feed(wsub(), wsubs(), wsubd(fw = "2.9.20"))
        val hot = base.copy(voice = base.voice.copy(on = true, pkDb10 = 45))
        assertTrue(Diagnosis.voiceWarning(hot)!!.contains("limiter"))
        val ok = base.copy(voice = base.voice.copy(on = true, pkDb10 = 40))
        assertTrue(Diagnosis.voiceWarning(ok) == null)
    }

    // ---- kasus nyata dari laporan diagnosa 2026-11-26 (mode 4, master 2.9.26)
    private fun wsubReal(nomem: Int = 1, drop: Int = 0, heapmin: Int = 3636) =
        "WSUB peers=$mac peer=$mac links=1 link=1 fill=97 und=3 gap=0 rate=44100 hiz=0 txf=0 lat=70 trim=0 err=none " +
            "ackok=6184 ackfail=52 nomem=$nomem ch=6 max=4 scan=0 drop=$drop heap=6636 heapmin=$heapmin heapblk=3316 mfw=26 found=none fb=0"

    private fun verLine(psram: Int) = "VER fw=2.9.26 role=master heap=6304 psram=$psram"

    @Test
    fun singleRecoveredNomemIsWarnNotError() {
        val f = Diagnosis.diagnose(feed(wsubReal(), wsubs(), wsubd()))
        assertTrue(f.any { it.level == Level.WARN && it.title.contains("Antrean TX") })
        assertFalse(f.any { it.level == Level.ERR && it.title.contains("Antrean TX") })
    }

    @Test
    fun nomemWithDropsIsError() {
        val f = Diagnosis.diagnose(feed(wsubReal(drop = 5), wsubs(), wsubd()))
        assertTrue(f.any { it.level == Level.ERR && it.title.contains("Antrean TX") })
    }

    @Test
    fun heapAroundFourKbWithPsramIsWarnNotError() {
        val f = Diagnosis.diagnose(feed(verLine(4159208), wsubReal(), wsubs(), wsubd()))
        assertEquals(Level.WARN, f.first { it.title.contains("RAM internal master") }.level)
    }

    @Test
    fun heapUnderTwoKbWithPsramIsErrorAndSuggestsPsramNotWrover() {
        val f = Diagnosis.diagnose(feed(verLine(4159208), wsubReal(heapmin = 1500), wsubs(), wsubd()))
        val h = f.first { it.title.contains("RAM internal master") }
        assertEquals(Level.ERR, h.level)
        assertFalse(h.action.contains("WROVER"))
        assertTrue(h.action.contains("PSRAM"))
        assertFalse(h.action.contains("pindahkan"))
    }

    @Test
    fun lowHeapWithoutPsramSuggestsWrover() {
        val f = Diagnosis.diagnose(feed(verLine(0), wsubReal(), wsubs(), wsubd()))
        assertTrue(f.first { it.title.contains("RAM internal master") }.action.contains("WROVER"))
    }

    @Test
    fun bufferShortShowsMarginAndBtGaps() {
        val s = feed(
            "BTD g40=357 g60=71 g80=8 g100=0 g200=0 g500=0 conn=2 audio=1 srate=44100 rssi=0 rssi_age=-1 evdrop=7 hk=15",
            wsubReal(), wsubs(und = 3),
            "WSUBD slot=0 mac=$mac g40=245 g60=45 g80=5 g100=0 g200=0 g500=0 gmax=0 adapt=242 fmin=247 jmax=360 pkt=64 est=120.8 need=118.1 fw=2.9.26 fillmin=82.7 age=460 cpu0=3 cpu1=8 pk0=3 pk1=11",
        )
        val f = Diagnosis.diagnose(s.copy(baseline = emptyMap()))
        val b = f.first { it.title.contains("buffer kurang") }
        assertTrue(b.detail.contains("2.7"))
        assertTrue(b.detail.contains("×8"))
    }

    private fun wsubWin(tapgap: Int = 50, sndgap: Int = 50, wait: Int = 0, qmax: Int = 1) =
        "WSUB peers=$mac peer=$mac links=1 link=1 fill=97 und=0 gap=0 rate=44100 hiz=0 txf=0 lat=70 trim=0 err=none " +
            "ackok=500 ackfail=0 nomem=0 ch=6 max=4 scan=0 drop=0 heap=90000 heapmin=60000 heapblk=50000 tapavg=1024 tapmax=1024 " +
            "tapgap=$tapgap tapbig=1 sndgap=$sndgap sndbig=1 wait=$wait wait10=0 qmax=$qmax mfw=26 found=none fb=0"

    @Test
    fun mildWindowGapsDoNotWarn() {
        val f = Diagnosis.diagnose(feed(wsubWin(), wsubs(), wsubd()))
        assertFalse(f.any { it.level == Level.WARN || it.level == Level.ERR })
    }

    @Test
    fun a2dpTapStallWarns() {
        val f = Diagnosis.diagnose(feed(wsubWin(tapgap = 250), wsubs(), wsubd()))
        assertTrue(f.any { it.level == Level.WARN && it.title.contains("Stall di jalur A2DP") })
    }

    @Test
    fun slowTxWithHealthyTapWarnsAboutEspNow() {
        val f = Diagnosis.diagnose(feed(wsubWin(sndgap = 150, qmax = 13), wsubs(), wsubd()))
        assertTrue(f.any { it.level == Level.WARN && it.title.contains("ESP-NOW tertahan") })
        assertFalse(f.any { it.title.contains("Stall di jalur A2DP") })
    }

    @Test
    fun gapsBetween80And100AreInfoWithCumulativeHistogram() {
        val bt = "BTD g40=357 g60=71 g80=8 g100=0 g200=0 g500=0 conn=2 audio=1 srate=44100 rssi=0 rssi_age=-1 evdrop=7 hk=15"
        val f = Diagnosis.diagnose(feed(bt, wsubWin(), wsubs(), wsubd()))
        assertTrue(f.any { it.level == Level.INFO && it.title.contains("80–100 ms ×8") })
    }

    @Test
    fun adaptiveBufferAtCeilingWarns() {
        val d = "WSUBD slot=0 mac=$mac g40=0 g60=0 g80=0 g100=0 g200=0 g500=0 gmax=0 adapt=350 fmin=0 jmax=360 pkt=64 est=130.0 need=120.0 fw=2.9.26 fillmin=40 age=1"
        val f = Diagnosis.diagnose(feed(wsubWin(), wsubs(), d))
        assertTrue(f.any { it.level == Level.WARN && it.title.contains("buffer adaptif") })
        val ok = Diagnosis.diagnose(feed(wsubWin(), wsubs(), d.replace("adapt=350", "adapt=242")))
        assertFalse(ok.any { it.title.contains("buffer adaptif") })
    }

    @Test
    fun compactLogDropsSyncRepliesAndCollapsesRepeats() {
        val fault = "FAULT err0=0x00 err1=0x00 err2=0x00 ot=0x00 oc_r=0 oc_l=0 dc_r=0 dc_l=0 uv=0 ov=0 clk=0 bq=0 crc=0 ot_sd=0 ot_w=0"
        val raw = listOf("> caps", fault, "OK status", fault, "OK status", fault, "OK status", "> dump")
        assertEquals(listOf("> caps", "$fault   ×3", "> dump"), Diagnosis.compactLog(raw))
    }

    @Test
    fun btEventTimeUsesAgeNotArrivalTime() {
        val now = 1_700_000_000_000L
        val s = LineParser.apply(AmpState(connected = true), "BTEV age=60000 ev=gap ms=84", now)
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        assertTrue(s.btEvents.single().startsWith(fmt.format(java.util.Date(now - 60_000))))
    }

    @Test
    fun btSummaryCountsEventsAndReportsDrops() {
        var s = AmpState(connected = true)
        s = LineParser.apply(s, "BTD g40=0 g60=0 g80=2 g100=0 g200=0 g500=0 conn=2 audio=1 srate=44100 rssi=0 rssi_age=-1 evdrop=7 hk=1", 1_000L)
        s = LineParser.apply(s, "BTEV age=10 ev=gap ms=84", 1_000L)
        s = LineParser.apply(s, "BTEV age=10 ev=gap ms=94", 1_000L)
        s = LineParser.apply(s, "BTEV age=10 ev=underrun slot=0 n=1 gap=66", 1_000L)
        val r = Diagnosis.btSummary(s)
        assertTrue(r.contains("underrun ×1"))
        assertTrue(r.contains("gap>80ms ×2 (maks 94 ms)"))
        assertTrue(r.contains("terbuang firmware ×7"))
    }

    @Test
    fun pausedMusicDoesNotReportSlaveNotReceivingAudio() {
        val paused = wsubWin().replace("tapavg=1024", "tapavg=0")
        val f = Diagnosis.diagnose(feed(paused, wsubs(rxa = 0), wsubd()))
        assertFalse(f.any { it.level == Level.ERR || it.level == Level.WARN })
        assertTrue(f.any { it.level == Level.INFO && it.title.contains("tidak ada audio mengalir") })
    }

    @Test
    fun streamingWithNoRxStillErrors() {
        val f = Diagnosis.diagnose(feed(wsubWin(), wsubs(rxa = 0), wsubd()))
        assertTrue(f.any { it.level == Level.ERR && it.title.contains("tidak menerima audio") })
    }

    @Test
    fun jitterLabelSaysBootWhenNoBaseline() {
        val f = Diagnosis.diagnose(feed(wsub(), wsubs(und = 3), wsubd()))
        assertTrue(f.any { it.title.contains("sejak boot slave") })
    }

    @Test
    fun reportHeaderHasSummaryAndWindow() {
        val s = feed(wsubReal(), wsubs(), wsubd())
        val r = Diagnosis.report(s)
        assertTrue(r.contains("Ringkasan : "))
        assertTrue(r.contains("Jendela   : counter kumulatif sejak boot"))
    }
}
