package com.tas5805m.ctrl

import com.tas5805m.ctrl.data.BuiltinPresets
import com.tas5805m.ctrl.data.Preset
import com.tas5805m.ctrl.data.PresetJson
import com.tas5805m.ctrl.data.PresetTools
import com.tas5805m.ctrl.data.VoiceParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetToolsTest {
    private fun eqPreset(name: String, vararg g: Int) =
        Preset(name = name, kind = "eq", eqLeft = g.map { it.toFloat() }, eqRight = g.map { it.toFloat() })

    @Test
    fun builtinPresetsAreValid() {
        val all = BuiltinPresets.all
        assertEquals("id harus unik", all.size, all.map { it.id }.toSet().size)
        assertEquals("nama harus unik", all.size, all.map { it.name }.toSet().size)
        for (p in all) {
            assertEquals(p.name, 15, p.eqLeft.size)
            assertEquals(p.name, 15, p.eqRight.size)
            assertTrue(p.name, p.eqLeft.all { it in -15f..15f && it == Math.round(it).toFloat() })
            assertTrue(p.name, p.builtin)
            p.voice?.let { v ->
                assertTrue(p.name, v.hpf in 20..120 && v.pkHz in 30..150 && v.pkDb10 in -60..90 && v.pkQ10 in 5..30)
                // tanpa limiter: preset bawaan tidak boleh melewati batas aman +4 dB kecuali 'Pesta' (+3,5 dB tetap ≤ 4)
                assertTrue("${p.name}: peak > +4 dB", v.pkDb10 <= 40)
            }
            if (p.kind == "scene") {
                assertNotNull(p.name, p.voice)
                assertTrue("${p.name}: scene harus bekerja di mode crossover (band 0..2 = 0)", (0..2).all { p.eqLeft[it] == 0f })
                assertFalse(p.name, PresetTools.hasSkippedEffect(p, 4))
            }
        }
        // Preset EQ baru (bukan 5 klasik) juga tidak boleh bergantung pada band yang dilewati
        for (n in listOf("Satelit kecil", "Suara / Podcast", "Hangat", "Anti-desis", "Game",
            "Akustik", "Jazz", "Rock", "Elektronik", "Klasik", "Berita / Siaran", "Speaker meja", "Anti-gemuruh")) {
            val p = all.first { it.name == n }
            assertFalse(n, PresetTools.hasSkippedEffect(p, 4))
        }
    }

    @Test
    fun sanitizeClampsEverything() {
        val raw = Preset(
            name = "  " + "x".repeat(100) + "  ", kind = "aneh", mode = 9, xover = 5, master = 999, gain = 99,
            eqLeft = List(15) { 99f }.toMutableList().also { it[1] = Float.NaN }, eqRight = List(3) { -99f },
            ll = Float.NaN, lr = 50f,
            clip = listOf(100, 999, -5), standby = 9999, trim = -500,
            voice = VoiceParams(on = true, hpf = 1, pkHz = 999, pkDb10 = 999, pkQ10 = 0, harm = 500, limit = 1),
        )
        val p = PresetTools.sanitize(raw)
        assertEquals(PresetTools.MAX_NAME, p.name.length)
        assertEquals("full", p.kind)
        assertEquals(6, p.mode); assertEquals(60, p.xover); assertEquals(100, p.master); assertEquals(31, p.gain)
        assertEquals(15f, p.eqLeft[0]); assertEquals(0f, p.eqLeft[1]); assertEquals(15, p.eqRight.size)
        assertEquals(-15f, p.eqRight[0]); assertEquals(0f, p.eqRight[10])
        assertEquals(1f, p.ll); assertEquals(2f, p.lr)
        assertEquals(listOf(0, 300, 0), p.clip)
        assertEquals(240, p.standby); assertEquals(-60, p.trim)
        val v = p.voice!!
        assertEquals(20, v.hpf); assertEquals(150, v.pkHz); assertEquals(90, v.pkDb10)
        assertEquals(5, v.pkQ10); assertEquals(0, v.harm); assertEquals(100, v.limit)
        assertEquals("wsub_voice 1 20 150 90 5 0 100", v.command())
    }

    @Test
    fun jsonRoundTripKeepsIdAndContent() {
        val a = Preset(name = "A", kind = "scene", eqLeft = List(15) { it - 7f }, eqRight = List(15) { it - 7f },
            voice = VoiceParams(on = true, hpf = 40, pkHz = 55, pkDb10 = -30, pkQ10 = 12))
        val back = PresetJson.fromJson(PresetJson.toJson(listOf(a)))
        assertEquals(1, back.size)
        assertEquals(a.id, back[0].id)
        assertEquals("scene", back[0].kind)
        assertEquals(a.eqLeft, back[0].eqLeft)
        assertEquals(a.voice, back[0].voice)
    }

    @Test
    fun legacyPresetsGetStableDistinctIds() {
        val json = """[{"name":"Lama","created":"t","eqLeft":[1,2,3],"eqRight":[1,2,3]},{"name":"Lama","created":"t"}]"""
        val a = PresetJson.fromJson(json)
        val b = PresetJson.fromJson(json)
        assertEquals(2, a.size)
        assertEquals(a.map { it.id }, b.map { it.id })          // deterministik antar pembacaan
        assertNotEquals(a[0].id, a[1].id)                       // nama+created sama tapi indeks beda
        assertEquals(15, a[0].eqLeft.size)
    }

    @Test
    fun invalidJsonGivesEmptyList() {
        assertTrue(PresetJson.fromJson("bukan json").isEmpty())
        assertTrue(PresetJson.fromJson("{}").isEmpty())
        assertTrue(PresetJson.fromJson("[1,2]").isEmpty())
    }

    @Test
    fun mergeSkipsDuplicatesAndRenamesSameName() {
        val existing = listOf(eqPreset("Satu", *IntArray(15) { 1 }))
        val dup = existing[0].copy(id = "lain")
        val sameNameOtherContent = eqPreset("Satu", *IntArray(15) { 2 })
        val fresh = eqPreset("Baru", *IntArray(15) { 3 })
        val (list, added) = PresetTools.merge(existing, listOf(dup, sameNameOtherContent, fresh))
        assertEquals(2, added)
        assertEquals(3, list.size)
        assertTrue(list.any { it.name == "Satu (2)" })
        assertTrue(list.any { it.name == "Baru" })
        assertEquals(list.size, list.map { it.id }.toSet().size)
        assertTrue(list.none { it.builtin })
    }

    @Test
    fun mergeRespectsLimits() {
        val many = (1..80).map { eqPreset("P$it", *IntArray(15) { i -> (it + i) % 10 }) }
        val (list, added) = PresetTools.merge(emptyList(), many)
        assertEquals(PresetTools.MAX_IMPORT, added)
        assertEquals(PresetTools.MAX_IMPORT, list.size)
        val full = (1..PresetTools.MAX_PRESETS).map { eqPreset("E$it", *IntArray(15) { i -> (it * 7 + i) % 13 - 6 }) }
        val (_, added2) = PresetTools.merge(full, listOf(eqPreset("Tambahan", *IntArray(15) { 4 })))
        assertEquals(0, added2)
    }

    @Test
    fun warningsAreModeAware() {
        val bass = BuiltinPresets.all.first { it.name == "Bass Boost" }
        assertTrue(PresetTools.hasSkippedEffect(bass, 4))
        assertFalse(PresetTools.hasSkippedEffect(bass, 0))
        assertNotNull(PresetTools.warning(bass, 4))
        val flat = BuiltinPresets.all.first { it.name == "Flat" }
        assertNull(PresetTools.warning(flat, 4))
        val treble = BuiltinPresets.all.first { it.name == "Treble Boost" }
        assertTrue(PresetTools.maxBoostDb(treble, 4) >= 4)
        assertTrue(PresetTools.warning(treble, 4)!!.contains("Master"))
        val hot = Preset(name = "Panas", kind = "voice", voice = VoiceParams(on = true, pkDb10 = 60))
        assertTrue(PresetTools.warning(hot, 4)!!.contains("limiter"))
    }
}
