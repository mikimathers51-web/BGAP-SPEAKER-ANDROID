package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.MonitorLayout
import com.tas5805m.ctrl.data.PeerDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorLayoutTest {
    private fun st(mode: Int, vararg caps: String) = AmpState(mode = mode, caps = caps.toSet())

    @Test fun label_utama_per_mode() {
        assertEquals("L" to "R", MonitorLayout.main(0).let { it.first.label to it.second.label })
        assertEquals("SAT" to "SUB", MonitorLayout.main(2).let { it.first.label to it.second.label })
        assertEquals("TW" to "WF", MonitorLayout.main(6).let { it.first.label to it.second.label })
        assertEquals("TW L" to "TW R", MonitorLayout.main(5).let { it.first.label to it.second.label })
        assertEquals("SAT L" to "SAT R", MonitorLayout.main(3).let { it.first.label to it.second.label })
    }

    @Test fun kanal_tambahan_per_mode() {
        assertTrue(MonitorLayout.extra(st(0, "lvl", "lvl2")).isEmpty())
        assertTrue(MonitorLayout.extra(st(2, "lvl")).isEmpty())
        assertTrue(MonitorLayout.extra(st(6, "lvl")).isEmpty())
        assertEquals(listOf("Sub"), MonitorLayout.extra(st(1, "lvl", "lvl2")).map { it.label })
        assertEquals(listOf("Center", "Sub"), MonitorLayout.extra(st(3, "lvl", "lvl2")).map { it.label })
        assertEquals(listOf("WF L", "WF R"), MonitorLayout.extra(st(5, "lvl", "lvl2")).map { it.label })
    }

    @Test fun tanpa_lvl2_tidak_ada_kanal_chip2() {
        assertTrue(MonitorLayout.extra(st(1, "lvl")).isEmpty())
        assertTrue(MonitorLayout.extra(st(3, "lvl")).isEmpty())
    }

    @Test fun lvl2_hanya_dibaca_di_mode_2_chip() {
        assertEquals(listOf(1, 3, 5), (0..6).filter { MonitorLayout.needsLvl2(it) })
    }

    @Test fun baca_nilai_chip() {
        val s = AmpState(mode = 3, caps = setOf("lvl", "lvl2"), lvlL = -100, lvlR = -200, lvl2L = -300, lvl2R = -50)
        assertEquals(-100, MonitorLayout.read(s, "c1l"))
        assertEquals(-200, MonitorLayout.read(s, "c1r"))
        assertEquals(-300, MonitorLayout.read(s, "c2l"))
        assertEquals(-50, MonitorLayout.read(s, "c2r"))
        assertEquals(-50, MonitorLayout.read(s, "c2m"))
        assertNull(MonitorLayout.read(s, "zzz"))
    }

    @Test fun peer_nirkabel_na_dan_batas_empat() {
        val peers = listOf(
            PeerDetail(mac = "A", slot = 0, lvlDeciDb = -250),
            PeerDetail(mac = "B", slot = 1, lvlDeciDb = null),
            PeerDetail(mac = "C", slot = -1, lvlDeciDb = -10),
            PeerDetail(mac = "D"), PeerDetail(mac = "E"),
        )
        val s = AmpState(mode = 4, caps = setOf("wsub", "wlvl"), peerDetails = peers)
        val ex = MonitorLayout.extra(s)
        assertEquals(4, ex.size)
        assertEquals(listOf("Sub 1", "Sub 2", "Sub 3", "Sub 4"), ex.map { it.label })
        assertEquals(-250, MonitorLayout.read(s, "p0"))
        assertNull(MonitorLayout.read(s, "p1"))          // WLVL s1=na
        assertEquals(-10, MonitorLayout.read(s, "p2"))
        assertNull(MonitorLayout.read(s, "p9"))          // di luar daftar
    }
}
