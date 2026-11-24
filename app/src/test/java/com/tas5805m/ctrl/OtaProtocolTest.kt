package com.tas5805m.ctrl

import com.tas5805m.ctrl.bluetooth.OtaProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaProtocolTest {
    @Test fun parseInfo_lengkap() {
        val i = OtaProtocol.parseInfo("OTA INFO run=app0 next=app1 max=2031616 pw=default active=0 build=Nov 20 2026 12:00:00")!!
        assertEquals("app0", i.run); assertEquals("app1", i.next); assertEquals(2031616L, i.max)
        assertTrue(i.pwDefault); assertEquals("Nov 20 2026 12:00:00", i.build); assertTrue(i.supported)
    }

    @Test fun parseInfo_tanpa_slot_ota() {
        val i = OtaProtocol.parseInfo("OTA INFO run=factory next=- max=0 pw=custom active=0 build=x")!!
        assertFalse(i.supported); assertFalse(i.pwDefault)
    }

    @Test fun parseInfo_bukan_info() = assertNull(OtaProtocol.parseInfo("OK ota_info"))

    @Test fun parseAck() {
        assertEquals(4096L, OtaProtocol.parseAck("OTA ACK 4096"))
        assertNull(OtaProtocol.parseAck("OTA READY"))
    }

    @Test fun isOtaLine_memisahkan_balasan_ota_dari_status() {
        for (l in listOf("OTA READY", "OTA ACK 2048", "ERR ota_hash", "OK ota_done", "OK ota_pass")) assertTrue(l, OtaProtocol.isOtaLine(l))
        for (l in listOf("OK modset", "ERR modset bw_not_allowed", "MOD mode=bd freq=384k bd=80k", "OK status")) assertFalse(l, OtaProtocol.isOtaLine(l))
    }

    @Test fun errorText_ramah() {
        assertTrue(OtaProtocol.errorText("ERR ota_auth").contains("Sandi"))
        assertTrue(OtaProtocol.errorText("ERR ota_no_slot").contains("partisi"))
        assertTrue(OtaProtocol.errorText(null).contains("waktu habis"))
    }
}
