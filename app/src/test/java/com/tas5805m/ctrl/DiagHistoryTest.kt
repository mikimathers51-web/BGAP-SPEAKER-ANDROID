package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.DiagHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagHistoryTest {
    @Test
    fun jsonRoundTripKeepsOrderAndContent() {
        val a = DiagHistory.make("Sebelum", "baris1\nbaris2", 1_000L)
        val b = DiagHistory.make("Sesudah", "x", 2_000L)
        val list = DiagHistory.add(DiagHistory.add(emptyList(), a), b)
        assertEquals(listOf("Sesudah", "Sebelum"), list.map { it.title })     // terbaru dulu
        val back = DiagHistory.fromJson(DiagHistory.toJson(list))
        assertEquals(list, back)
    }

    @Test
    fun keepsOnlyNewestTwenty() {
        var list = emptyList<com.tas5805m.ctrl.data.DiagReport>()
        for (i in 1..30) list = DiagHistory.add(list, DiagHistory.make("L$i", "t", i.toLong()))
        assertEquals(DiagHistory.MAX, list.size)
        assertEquals("L30", list.first().title)
        assertEquals("L11", list.last().title)
    }

    @Test
    fun truncatesHugeTextAndBlankTitle() {
        val r = DiagHistory.make("   ", "a".repeat(DiagHistory.MAX_TEXT + 500), 1L)
        assertEquals("Laporan", r.title)
        assertEquals(DiagHistory.MAX_TEXT, r.text.length)
    }

    @Test
    fun invalidOrIncompleteJsonIsSafe() {
        assertTrue(DiagHistory.fromJson("rusak").isEmpty())
        assertTrue(DiagHistory.fromJson("{}").isEmpty())
        assertTrue(DiagHistory.fromJson("""[{"title":"tanpa id"},5]""").isEmpty())
    }

    @Test
    fun fileNameLooksRight() {
        assertTrue(Regex("""diagnosa-\d{8}-\d{6}\.txt""").matches(DiagHistory.fileName(1_700_000_000_000L)))
    }
}
