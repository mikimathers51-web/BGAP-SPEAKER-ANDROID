package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.MeterChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterChannelTest {
    @Test fun nilai_konstan_rata_rata_sama() {
        val c = MeterChannel()
        repeat(10) { c.push(-300, it * 300L) }
        val s = c.snapshot()
        assertEquals(-300, s.now); assertEquals(-300, s.avg); assertEquals(-300, s.peakHold)
    }

    @Test fun peak_hold_bertahan_lalu_turun() {
        val c = MeterChannel(holdMs = 1500)
        c.push(-100, 0); c.push(-600, 300)
        assertEquals(-100, c.snapshot().peakHold)          // masih ditahan
        c.push(-600, 2000)                                  // lewat 1,5 dtk
        assertEquals(-600, c.snapshot().peakHold)
        assertEquals(-100, c.snapshot().sessionMax)         // puncak sesi tidak turun
    }

    @Test fun reset_peaks() {
        val c = MeterChannel(); c.push(-50, 0); c.push(-800, 100)
        c.resetPeaks()
        val s = c.snapshot()
        assertEquals(MeterChannel.FLOOR, s.sessionMax); assertEquals(-800, s.peakHold)
    }

    @Test fun rata_rata_daya_didominasi_puncak() {
        val c = MeterChannel()
        c.push(0, 0); c.push(-1200, 1)   // 0 dB dan lantai → rata-rata daya ≈ −3 dB
        val avg = c.snapshot().avg
        assertTrue("avg=$avg", avg in -32..-29)   // deci-dB: −3,0 dB = −30
    }

    @Test fun nilai_di_luar_rentang_dijepit() {
        val c = MeterChannel(); c.push(500, 0); c.push(-5000, 1)
        val s = c.snapshot()
        assertEquals(MeterChannel.FLOOR, s.now); assertEquals(0, s.sessionMax)
    }

    @Test fun kosong_adalah_lantai() = assertEquals(MeterChannel.FLOOR, MeterChannel().snapshot().avg)
}
