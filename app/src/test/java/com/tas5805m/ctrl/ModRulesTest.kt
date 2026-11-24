package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.ModRules
import org.junit.Assert.assertEquals
import org.junit.Test

class ModRulesTest {
    @Test fun bd_384_boleh_sampai_120() =
        assertEquals(listOf("80k", "100k", "120k"), ModRules.allowedBw("bd", "384k"))

    @Test fun hybrid_384_hanya_80() =
        assertEquals(listOf("80k"), ModRules.allowedBw("hybrid", "384k"))

    @Test fun spw_480_sampai_100() =
        assertEquals(listOf("80k", "100k"), ModRules.allowedBw("1spw", "480k"))

    @Test fun semua_di_768() {
        for (m in listOf("bd", "1spw", "hybrid")) assertEquals(ModRules.allBw, ModRules.allowedBw(m, "768k"))
    }

    @Test fun clamp_turun_ke_terbesar_yang_boleh() {
        assertEquals("120k", ModRules.clampBw("bd", "384k", "175k"))
        assertEquals("80k", ModRules.clampBw("hybrid", "384k", "120k"))
        assertEquals("100k", ModRules.clampBw("bd", "384k", "100k"))
    }
}
