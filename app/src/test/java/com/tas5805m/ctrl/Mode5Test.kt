package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.LineParser
import com.tas5805m.ctrl.data.Preset
import com.tas5805m.ctrl.data.PresetTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mode 5 = 2 chip stereo 2-way (chip 1 = 2 tweeter, chip 2 = 2 woofer), firmware 2.9.26. */
class Mode5Test {
    private fun inMode5(): AmpState {
        val caps = LineParser.apply(AmpState(), "CAPS eqband tway ctr xover2 subloud pbass")
        val m = LineParser.apply(caps, "MODE 5 xover=150 master=90 sat=90 ctr=85 sub=90")
        return LineParser.apply(m, "TWAY on=1 hz=2500 min=1000 max=5000 fs=44100")
    }

    @Test fun modeAllowed_5_needsChip2() {
        assertTrue(LineParser.apply(AmpState(), "CAPS eqband ctr").modeAllowed(5))
        assertFalse(LineParser.apply(AmpState(), "CAPS eqband").modeAllowed(5))             // 1 chip: tanpa CAPS ctr
        assertFalse(LineParser.apply(AmpState(), "CAPS eqband wsub ctr").modeAllowed(5))    // master nirkabel: hanya mode 4
    }

    @Test fun mode5_state_flags() {
        val s = inMode5()
        assertEquals(5, s.mode)
        assertTrue(s.isStereo2Way)
        assertTrue(s.twayActive)          // 2-way selalu aktif → kartu Crossover tabel disembunyikan
        assertTrue(s.mixerLocked)
        assertTrue(s.xoverLocksEq)
        assertEquals("2-way Stereo", s.modeName())
        assertEquals(2500, s.twayHz)
    }

    @Test fun mode2_unaffected_byMode5Changes() {
        val caps = LineParser.apply(AmpState(), "CAPS eqband tway xext")
        val m2 = LineParser.apply(caps, "MODE 2 xover=100 master=90 sat=90 ctr=85 sub=90")
        assertFalse(m2.isStereo2Way)
        assertFalse(m2.twayActive)        // tway belum nyala
        assertTrue(m2.mixerLocked)
        assertTrue(LineParser.apply(m2, "TWAY on=1 hz=2500 min=1000 max=5000 fs=44100").twayActive)
    }

    @Test fun presetSanitize_keepsMode5() {
        assertEquals(5, PresetTools.sanitize(Preset(name = "x", mode = 5)).mode)
        assertEquals(6, PresetTools.sanitize(Preset(name = "x", mode = 9)).mode)   // dijepit ke batas atas (mode 6)
    }

    // ---- mode 6: 1 chip mono 2-way ----
    @Test fun mode6_allowed_withoutChip2_andFlags() {
        val caps = LineParser.apply(AmpState(), "CAPS eqband tway")            // env 1 chip: tanpa CAPS ctr
        assertTrue(caps.modeAllowed(6))
        assertFalse(caps.modeAllowed(5))
        assertFalse(LineParser.apply(AmpState(), "CAPS eqband wsub").modeAllowed(6))   // master nirkabel: hanya mode 4
        val m6 = LineParser.apply(LineParser.apply(caps, "MODE 6 xover=150 master=90 sat=90 ctr=85 sub=90"),
            "TWAY on=1 hz=2500 min=1000 max=5000 fs=44100")
        assertTrue(m6.isMono2Way); assertFalse(m6.isStereo2Way)
        assertTrue(m6.twayActive); assertTrue(m6.mixerLocked)
        assertEquals("2-way 1 Chip", m6.modeName())
        assertEquals(6, PresetTools.sanitize(Preset(name = "x", mode = 6)).mode)
    }
}
