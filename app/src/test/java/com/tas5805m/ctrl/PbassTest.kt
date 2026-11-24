package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.LineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Psychoacoustic bass (firmware 2.9.26, CAPS pbass, semua mode). */
class PbassTest {

    @Test fun capsToken_enablesPbass() {
        assertFalse(AmpState().hasPbass)
        assertTrue(LineParser.apply(AmpState(), "CAPS eqband tway subloud pbass").hasPbass)
    }

    @Test fun pbassLine_parsesAllFields() {
        val s = LineParser.apply(AmpState(), "PBASS on=1 amt=65 fc=90 act=1")
        assertTrue(s.pbass)
        assertEquals(65, s.pbassAmt)
        assertEquals(90, s.pbassFc)
        assertTrue(s.pbassActive)
    }

    @Test fun pbassLine_off_keepsConfig() {
        val on = LineParser.apply(AmpState(), "PBASS on=1 amt=65 fc=90 act=1")
        val off = LineParser.apply(on, "PBASS on=0 amt=65 fc=90 act=0")
        assertFalse(off.pbass)
        assertFalse(off.pbassActive)
        assertEquals(65, off.pbassAmt)
        assertEquals(90, off.pbassFc)
    }

    @Test fun pbassLine_clampsOutOfRange() {
        val s = LineParser.apply(AmpState(), "PBASS on=1 amt=500 fc=10 act=1")
        assertEquals(100, s.pbassAmt)
        assertEquals(40, s.pbassFc)
    }

    @Test fun pbassLine_isQuiet_notLogged() {
        val s = LineParser.apply(AmpState(), "PBASS on=1 amt=40 fc=80 act=0")
        assertTrue(s.lastLines.none { it.startsWith("PBASS") })
    }

    @Test fun pbassLine_worksInEveryMode() {
        for (m in 0..4) {
            val withMode = LineParser.apply(AmpState(), "MODE $m xover=100 master=90 sat=90 ctr=85 sub=90")
            val s = LineParser.apply(withMode, "PBASS on=1 amt=40 fc=80 act=1")
            assertEquals(m, s.mode)
            assertTrue(s.pbass)
        }
    }
}

/** Loudness sub ikut volume ponsel (firmware 2.9.26). */
class SubloudPhoneTest {
    @Test fun subloudLine_parsesPhoneFields() {
        val s = LineParser.apply(AmpState(), "SUBLOUD on=1 db10=60 lo=30 hi=70 cur=25 ph=1 pv=64 eff=54")
        assertTrue(s.subloudPhone); assertEquals(64, s.phoneVol); assertEquals(54, s.subloudEff)
    }
    @Test fun oldFirmware_keepsDefaults() {
        val s = LineParser.apply(AmpState(), "SUBLOUD on=1 db10=60 lo=30 hi=70 cur=0")
        assertEquals(-1, s.subloudEff); assertEquals(-1, s.phoneVol)
    }
}
