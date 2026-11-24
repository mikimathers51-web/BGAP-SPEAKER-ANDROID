package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.EqSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqSyncTest {
    private val flat = List(15) { 0f }

    @Test fun sama_tidak_ada_perintah() = assertTrue(EqSync.plan(flat, flat).isEmpty())

    @Test fun hanya_band_yang_beda_dikirim() {
        val l = flat.toMutableList().also { it[4] = 3f; it[10] = -2f }
        assertEquals(listOf(4 to 3, 10 to -2), EqSync.plan(l, flat))
    }

    @Test fun band_terkunci_dilewati() {
        val l = List(15) { 5f }
        val p = EqSync.plan(l, flat, EqSync.LOCKED_BANDS)
        assertEquals(12, p.size)
        assertEquals(3, p.first().first)
    }

    @Test fun pembulatan_dan_jepit() {
        val l = flat.toMutableList().also { it[5] = 2.6f; it[6] = 40f; it[7] = -99f }
        assertEquals(listOf(5 to 3, 6 to 15, 7 to -15), EqSync.plan(l, flat))
    }

    @Test fun nilai_kanan_beda_pecahan_tapi_sama_integer_dilewati() {
        val l = flat.toMutableList().also { it[2] = 2f }
        val r = flat.toMutableList().also { it[2] = 2.4f }
        assertTrue(EqSync.plan(l, r).isEmpty())
    }

    @Test fun daftar_pendek_aman() {
        assertEquals(listOf(0 to 1), EqSync.plan(listOf(1f), emptyList()))
        assertTrue(EqSync.plan(emptyList(), flat).isEmpty())
    }
}
