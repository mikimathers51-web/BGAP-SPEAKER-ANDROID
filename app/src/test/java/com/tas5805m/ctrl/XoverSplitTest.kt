package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.LineParser
import com.tas5805m.ctrl.data.Preset
import com.tas5805m.ctrl.data.PresetJson
import com.tas5805m.ctrl.data.PresetTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HPF satelit & LPF sub terpisah (firmware 2.9.22, CAPS xover2). */
class XoverSplitTest {

    @Test fun legacyModeLine_withoutXlp_setsLpEqualToHp() {
        val s = LineParser.apply(AmpState(), "MODE 2 xover=120 master=90 sat=90 ctr=85 sub=90")
        assertEquals(120, s.xover)
        assertEquals(120, s.xoverLp)
    }

    @Test fun newModeLine_parsesHpAndLpSeparately() {
        val s = LineParser.apply(AmpState(), "MODE 4 xover=100 master=90 sat=90 ctr=85 sub=90 xhp=100 xlp=70")
        assertEquals(100, s.xover)
        assertEquals(70, s.xoverLp)
    }

    @Test fun legacyLineAfterSplit_resetsLpToHp() {
        // firmware lama tidak pernah mengirim xlp → jangan mempertahankan LPF dari koneksi sebelumnya
        val split = LineParser.apply(AmpState(), "MODE 4 xover=100 master=90 sat=90 ctr=85 sub=90 xhp=100 xlp=70")
        val legacy = LineParser.apply(split, "MODE 4 xover=110 master=90 sat=90 ctr=85 sub=90")
        assertEquals(110, legacy.xover)
        assertEquals(110, legacy.xoverLp)
    }

    @Test fun capsToken_enablesSplit() {
        val off = LineParser.apply(AmpState(), "CAPS eqband eqdump lvl v2 v3")
        assertFalse(off.hasXover2)
        val on = LineParser.apply(AmpState(), "CAPS eqband eqdump lvl v2 v3 xover2 wsub")
        assertTrue(on.hasXover2)
    }

    @Test fun presetJson_roundTripKeepsLp() {
        val p = Preset(name = "Split", xover = 120, xoverLp = 80)
        val back = PresetJson.fromJson(PresetJson.toJson(listOf(p))).single()
        assertEquals(120, back.xover)
        assertEquals(80, back.xoverLp)
    }

    @Test fun presetJson_withoutLp_staysNull() {
        val p = Preset(name = "Lama", xover = 100)
        val back = PresetJson.fromJson(PresetJson.toJson(listOf(p))).single()
        assertNull(back.xoverLp)
    }

    @Test fun sanitize_clampsLp() {
        assertEquals(60, PresetTools.sanitize(Preset(name = "a", xoverLp = 5)).xoverLp)
        assertEquals(150, PresetTools.sanitize(Preset(name = "a", xoverLp = 999)).xoverLp)
        assertNull(PresetTools.sanitize(Preset(name = "a")).xoverLp)
    }

    // ---- 2-way aktif mono (firmware 2.9.23, CAPS tway, mode 2) ----

    @Test fun twayLine_isParsed() {
        val s = LineParser.apply(AmpState(), "TWAY on=1 hz=3000 min=1200 max=4500 fs=44100")
        assertTrue(s.tway)
        assertEquals(3000, s.twayHz)
        assertEquals(1200, s.twayMin)
        assertEquals(4500, s.twayMax)
    }

    @Test fun twayLine_off() {
        val on = LineParser.apply(AmpState(), "TWAY on=1 hz=2500 min=1000 max=5000 fs=48000")
        val off = LineParser.apply(on, "TWAY on=0 hz=2500 min=1000 max=5000 fs=0")
        assertFalse(off.tway)
    }

    @Test fun twayActive_needsCapsAndMode2() {
        val base = AmpState(mode = 2, tway = true, caps = setOf("tway"))
        assertTrue(base.twayActive)
        assertFalse(base.copy(mode = 4).twayActive)              // hanya bermakna di mode 2
        assertFalse(base.copy(caps = emptySet()).twayActive)     // firmware tanpa fitur
        assertFalse(base.copy(tway = false).twayActive)
    }

    // ---- Loudness sub (firmware 2.9.24, CAPS subloud, mode 2) ----

    @Test fun subloudLine_isParsed() {
        val s = LineParser.apply(AmpState(), "SUBLOUD on=1 db10=45 lo=25 hi=60 cur=30")
        assertTrue(s.subloud)
        assertEquals(45, s.subloudDb10)
        assertEquals(25, s.subloudLo)
        assertEquals(60, s.subloudHi)
        assertEquals(30, s.subloudCur)
    }

    @Test fun subloudLine_off_keepsOtherFields() {
        val on = LineParser.apply(AmpState(), "SUBLOUD on=1 db10=60 lo=30 hi=70 cur=60")
        val off = LineParser.apply(on, "SUBLOUD on=0 db10=60 lo=30 hi=70 cur=0")
        assertFalse(off.subloud)
        assertEquals(0, off.subloudCur)
        assertEquals(60, off.subloudDb10)
    }

    @Test fun subloudCaps() {
        assertFalse(AmpState().hasSubloud)
        assertTrue(LineParser.apply(AmpState(), "CAPS eqband tway subloud").hasSubloud)
    }

    // ---- crossover diperluas sampai 300 Hz (firmware CAPS xext, hanya mode 2) ----
    @Test fun xoverMax_is300OnlyInMode2WithXext() {
        val caps = LineParser.apply(AmpState(), "CAPS eqband tway xext xover2")
        assertTrue(caps.hasXext)
        val m2 = LineParser.apply(caps, "MODE 2 xover=200 master=90 sat=90 ctr=85 sub=90 xhp=200 xlp=300")
        assertEquals(300, m2.xoverMax)
        assertEquals(300, m2.xoverLp)
        val m1 = LineParser.apply(caps, "MODE 1 xover=100 master=90 sat=90 ctr=85 sub=90 xhp=100 xlp=100")
        assertEquals(150, m1.xoverMax)
    }

    @Test fun xoverMax_is150WithoutXext() {
        val old = LineParser.apply(LineParser.apply(AmpState(), "CAPS eqband tway xover2"), "MODE 2 xover=100 master=90 sat=90 ctr=85 sub=90")
        assertFalse(old.hasXext)
        assertEquals(150, old.xoverMax)
    }

    @Test fun presetSanitize_allows300OnlyInMode2() {
        val m2 = PresetTools.sanitize(Preset(name = "x", mode = 2, xover = 300, xoverLp = 280))
        assertEquals(300, m2.xover); assertEquals(280, m2.xoverLp)
        val m1 = PresetTools.sanitize(Preset(name = "x", mode = 1, xover = 300, xoverLp = 280))
        assertEquals(150, m1.xover); assertEquals(150, m1.xoverLp)
    }
}
