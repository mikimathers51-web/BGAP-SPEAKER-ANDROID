package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.ClipRules
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipRulesTest {
    @Test fun makeup_maks_mengikuti_gain() {
        assertEquals(300, ClipRules.maxMakeup(0))
        assertEquals(200, ClipRules.maxMakeup(-100))
        assertEquals(0, ClipRules.maxMakeup(-300))
    }

    @Test fun gain_di_luar_rentang_dipotong() {
        assertEquals(300, ClipRules.maxMakeup(50))
        assertEquals(0, ClipRules.maxMakeup(-999))
    }

    @Test fun format_db() {
        assertEquals("+3.0 dB", ClipRules.fmtDb(30))
        assertEquals("-3.0 dB", ClipRules.fmtDb(-30))
        assertEquals("+0.0 dB", ClipRules.fmtDb(0))
    }
}
