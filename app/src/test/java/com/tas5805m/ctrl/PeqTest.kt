package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.Diagnosis
import com.tas5805m.ctrl.data.LineParser
import com.tas5805m.ctrl.data.PEQ_BANDS
import com.tas5805m.ctrl.data.PeerDetail
import com.tas5805m.ctrl.data.PeqBand
import com.tas5805m.ctrl.data.PeqMath
import com.tas5805m.ctrl.data.PeqParams
import com.tas5805m.ctrl.data.PeqType
import com.tas5805m.ctrl.data.Preset
import com.tas5805m.ctrl.data.PresetJson
import com.tas5805m.ctrl.data.PresetTools
import com.tas5805m.ctrl.data.VoiceParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nilai acuan (golden) berasal dari implementasi firmware (wsub_peq.h, fs = 48 kHz) yang diuji di host:
 * app dan firmware harus menggambar kurva yang sama.
 */
class PeqTest {
    private val mac = "AA:BB:CC:DD:EE:01"

    private fun near(want: Double, got: Double, tol: Double = 0.001, msg: String = "") =
        assertTrue("$msg want=$want got=$got", kotlin.math.abs(want - got) <= tol)

    @Test
    fun peakMatchesFirmwareGolden() {
        val b = PeqBand(PeqType.PEAK, 60, 8, 10)   // +4 dB, Q 1.0
        val c = PeqMath.coeffs(b)
        near(1.001818777, c[0], 1e-6); near(-1.993719331, c[1], 1e-6); near(0.991962047, c[2], 1e-6)
        near(-1.993719331, c[3], 1e-6); near(0.993780824, c[4], 1e-6)
        near(0.504622, PeqMath.bandDb(b, 20.0), msg = "20 Hz")
        near(4.0, PeqMath.bandDb(b, 60.0), msg = "60 Hz")
        near(2.971428, PeqMath.bandDb(b, 80.0), msg = "80 Hz")
        near(0.250705, PeqMath.bandDb(b, 250.0), msg = "250 Hz")
    }

    @Test
    fun shelvesMatchFirmwareGolden() {
        val lo = PeqBand(PeqType.LSHELF, 80, 12, 7)   // +6 dB
        near(5.967098, PeqMath.bandDb(lo, 20.0), msg = "lshelf 20")
        near(3.0, PeqMath.bandDb(lo, 80.0), msg = "lshelf fc = setengah gain")
        near(0.079416, PeqMath.bandDb(lo, 250.0), msg = "lshelf 250")
        val hi = PeqBand(PeqType.HSHELF, 100, -12, 7)  // -6 dB
        near(-0.015320, PeqMath.bandDb(hi, 20.0), msg = "hshelf 20")
        near(-3.0, PeqMath.bandDb(hi, 100.0), msg = "hshelf fc")
        near(-5.820894, PeqMath.bandDb(hi, 250.0), msg = "hshelf 250")
    }

    @Test
    fun totalOfThreeBandsMatchesFirmware() {
        val p = PeqParams(on = true, bands = PeqParams().bands.toMutableList().also {
            it[0] = PeqBand(PeqType.PEAK, 60, 8, 10)
            it[1] = PeqBand(PeqType.LSHELF, 80, 12, 7)
            it[2] = PeqBand(PeqType.HSHELF, 100, -12, 7)
        })
        near(6.456400, PeqMath.totalDb(p, 20.0), msg = "total 20")
        near(7.734593, PeqMath.totalDb(p, 60.0), msg = "total 60")
        near(0.677697, PeqMath.totalDb(p, 100.0), msg = "total 100")
        near(-5.490773, PeqMath.totalDb(p, 250.0), msg = "total 250")
        assertEquals("PEQ off → datar", 0.0, PeqMath.totalDb(p.copy(on = false), 60.0), 0.0)
    }

    @Test
    fun inactiveBandsAreUnity() {
        for (b in listOf(PeqBand(PeqType.OFF, 60, 10, 10), PeqBand(PeqType.PEAK, 60, 0, 10), PeqBand(PeqType.HSHELF, 90, 0, 7))) {
            assertFalse(b.active)
            assertEquals(listOf(1.0, 0.0, 0.0, 0.0, 0.0), PeqMath.coeffs(b).toList())
        }
    }

    @Test
    fun sanitizeClampsLikeFirmware() {
        val b = PeqBand(9, 3, 100, 0).sanitized()
        assertEquals(PeqType.OFF, b.type); assertEquals(20, b.hz); assertEquals(18, b.g2); assertEquals(5, b.q10)
        val c = PeqBand(PeqType.PEAK, 999, -100, 999).sanitized()
        assertEquals(250, c.hz); assertEquals(-24, c.g2); assertEquals(100, c.q10)
        assertEquals("wsub_peq_band 3 1 60 8 10", PeqBand(PeqType.PEAK, 60, 8, 10).command(3))
        assertEquals("perintah selalu di-clamp", "wsub_peq_band 0 1 250 -24 100", PeqBand(PeqType.PEAK, 999, -100, 999).command(0))
    }

    @Test
    fun parsesWsubeAndKeepsLocalValuesForBlankOffBands() {
        val s0 = AmpState(peq = PeqParams().withBand(2, PeqBand(PeqType.OFF, 48, 6, 12)))
        val line = "WSUBE on=1 n=7 b0=1:60:8:10 b1=2:80:-12:7 b2=0:0:0:0 b3=0:0:0:0 b4=0:0:0:0 b5=0:0:0:0 b6=0:0:0:0"
        val s = LineParser.apply(s0, line)
        assertTrue(s.peqKnown); assertTrue(s.hasPeq); assertTrue(s.peq.on)
        assertEquals(PeqBand(PeqType.PEAK, 60, 8, 10), s.peq.bands[0])
        assertEquals(PeqBand(PeqType.LSHELF, 80, -12, 7), s.peq.bands[1])
        assertEquals("band off kosong mempertahankan nilai lokal", PeqBand(PeqType.OFF, 48, 6, 12), s.peq.bands[2])
        assertEquals(PEQ_BANDS, s.peq.bands.size)
    }

    @Test
    fun parserToleratesGarbage() {
        val s = LineParser.apply(AmpState(), "WSUBE on=1 n=7 b0=x:1 b1=1:60:8 b2=7:60:8:10 b3=1:999:99:999")
        assertEquals(7, s.peq.bands.size)
        assertEquals(PeqType.OFF, s.peq.bands[0].type)        // rusak → nilai lama (default off)
        assertEquals(PeqType.OFF, s.peq.bands[1].type)
        assertEquals(PeqType.OFF, s.peq.bands[2].type)        // type 7 tidak dikenal → off
        assertEquals(18, s.peq.bands[3].g2)                   // di-clamp
    }

    @Test
    fun capsEnablePeq() {
        assertFalse(AmpState().hasPeq)
        assertTrue(AmpState(caps = setOf("wsub", "wsub_peq")).hasPeq)
    }

    @Test
    fun diagnosisFlagsOldSlavesAndHeadroom() {
        fun st(fw: String, peq: PeqParams, voice: VoiceParams = VoiceParams()) = AmpState(
            caps = setOf("wsub", "wsub_peq", "voice_basic"),
            peerDetails = listOf(PeerDetail(mac = mac)),
            slaveDiag = mapOf(mac to mapOf("fw" to fw)),
            peq = peq, voice = voice,
        )
        val safe = PeqParams(on = true).withBand(0, PeqBand(PeqType.PEAK, 60, 4, 10))   // +2 dB
        assertNull(Diagnosis.peqWarning(st("2.9.27", safe)))
        val w = Diagnosis.peqWarning(st("2.9.26", safe))
        assertNotNull(w); assertTrue(w!!, "#1" in w && "2.9.27" in w)
        assertEquals(listOf(1), Diagnosis.peqUnsupported(st("2.9.19", safe)))
        assertTrue("versi tak dikenal tidak diblokir", Diagnosis.peqUnsupported(st("?", safe)).isEmpty())
        val hot = PeqParams(on = true).withBand(0, PeqBand(PeqType.PEAK, 60, 16, 10))   // +8 dB
        assertTrue(Diagnosis.peqWarning(st("2.9.27", hot))!!.contains("clipping"))
        val mid = PeqParams(on = true).withBand(0, PeqBand(PeqType.PEAK, 60, 8, 10))    // +4 dB, aman sendiri
        assertNull(Diagnosis.peqWarning(st("2.9.27", mid)))
        assertNotNull("+4 dB PEQ + 3 dB peaking voicing ≥ 6 dB", Diagnosis.peqWarning(st("2.9.27", mid, VoiceParams(on = true, pkDb10 = 30))))
        assertNull("PEQ off → tidak ada peringatan headroom", Diagnosis.peqWarning(st("2.9.27", hot.copy(on = false))))
    }

    @Test
    fun presetJsonRoundTripAndLegacy() {
        val peq = PeqParams(on = true).withBand(1, PeqBand(PeqType.HSHELF, 120, -8, 7)).withBand(4, PeqBand(PeqType.PEAK, 55, 6, 20))
        val p = Preset(name = "t", kind = "scene", peq = peq)
        val back = PresetJson.fromJson(PresetJson.toJson(listOf(p))).single()
        assertEquals(peq.sanitized(), back.peq)
        // preset lama tanpa field peq tetap terbaca
        val old = PresetJson.fromJson(PresetJson.toJson(listOf(p.copy(peq = null)))).single()
        assertNull(old.peq)
        // impor tidak dipercaya: nilai di-clamp
        val evil = org.json.JSONObject(PresetJson.toObj(p).toString())
        evil.getJSONObject("peq").getJSONArray("bands").getJSONObject(0).put("hz", 9999).put("g2", 500).put("q", -4).put("t", 42)
        val e = PresetJson.fromObj(evil).peq!!.bands[0]
        assertEquals(PeqType.OFF, e.type); assertEquals(250, e.hz); assertEquals(18, e.g2); assertEquals(5, e.q10)
    }

    @Test
    fun presetWarningMentionsHeadroom() {
        val hot = PeqParams(on = true).withBand(0, PeqBand(PeqType.PEAK, 60, 18, 10))
        assertTrue(PresetTools.warning(Preset(name = "x", kind = "scene", peq = hot), 4)!!.contains("PEQ"))
        assertNull(PresetTools.warning(Preset(name = "x", kind = "scene", peq = PeqParams()), 4))
    }
}
