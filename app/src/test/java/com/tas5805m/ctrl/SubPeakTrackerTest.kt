package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.SubPeakTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubPeakTrackerTest {
    @Test fun belum_ada_sampel_null() {
        assertNull(SubPeakTracker().snapshot())
    }

    @Test fun hold_tiga_detik_lalu_turun() {
        val t = SubPeakTracker()
        t.push(-100, 0); t.push(-600, 300)
        assertEquals(-100, t.snapshot()!!.hold)            // masih ditahan
        t.push(-600, 3500)                                  // lewat 3 dtk
        assertEquals(-600, t.snapshot()!!.hold)
        assertEquals(-100, t.snapshot()!!.session)          // sesi tidak turun
    }

    @Test fun hit_dihitung_per_seberangan_bukan_per_sampel() {
        val t = SubPeakTracker()
        t.push(-200, 0)                 // di bawah −3 dB
        t.push(-20, 300)                // naik ≥ −3 dB (hit 1)
        t.push(-25, 600)                // masih di atas → tidak dihitung lagi
        t.push(-400, 900)               // turun
        t.push(-20, 1200)               // naik lagi (hit 2)
        val s = t.snapshot()!!
        assertEquals(2, s.nearHits)
        assertEquals(0, s.clipHits)     // −2 dB belum ≥ −1 dB
    }

    @Test fun clip_di_minus_satu_db() {
        val t = SubPeakTracker()
        t.push(-5, 0)
        assertEquals(1, t.snapshot()!!.clipHits)
        assertEquals(1, t.snapshot()!!.nearHits)
    }

    @Test fun reset_dan_clear() {
        val t = SubPeakTracker()
        t.push(-5, 0)
        t.resetPeaks()
        assertEquals(0, t.snapshot()!!.clipHits)
        t.clear()
        assertNull(t.snapshot())
    }

    @Test fun headroom_dan_crest() {
        val t = SubPeakTracker()
        repeat(5) { t.push(-300, it * 300L) }
        t.push(-60, 1500)
        val s = t.snapshot()!!
        assertEquals(6.0f, s.headroomDb, 0.001f)            // puncak sesi −6 dB
        assertEquals(true, s.crestDb > 0f)
    }
}
