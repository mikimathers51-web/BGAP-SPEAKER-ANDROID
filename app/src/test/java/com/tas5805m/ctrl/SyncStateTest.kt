package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.LineParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStateTest {
    private val fresh = AmpState(connected = true)

    @Test fun baru_tersambung_belum_siap() {
        assertFalse(fresh.synced)
        assertFalse(fresh.ready)
    }

    @Test fun ok_status_saja_belum_menandai_sinkron() {
        assertFalse(LineParser.apply(fresh, "OK status").synced)
    }

    @Test fun ok_dump_menandai_sinkron() {
        val s = LineParser.apply(fresh, "OK dump")
        assertTrue(s.synced)
        assertTrue(s.ready)
    }

    @Test fun tidak_siap_jika_terputus() {
        assertFalse(LineParser.apply(AmpState(connected = false), "OK dump").ready)
    }
}
