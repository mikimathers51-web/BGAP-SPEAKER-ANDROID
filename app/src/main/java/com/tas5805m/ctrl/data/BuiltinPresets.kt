package com.tas5805m.ctrl.data

/**
 * Preset bawaan. kind="scene" = EQ + voicing sub (tanpa mode/xover/volume). kind="eq" hanya mengubah EQ (+EQ on) — tidak menyentuh volume/mixer/xover.
 * kind="voice" hanya mengubah voicing sub (+trim bila ada). Band 0..2 (20/32/50 Hz) otomatis dilewati
 * saat crossover aktif (mode 1..4) karena dicadangkan untuk filter crossover.
 * Urutan band: 20, 32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000, 3150, 5000, 8000, 16000 Hz.
 */
object BuiltinPresets {
    private fun eq(name: String, note: String, g: List<Int>) = Preset(
        id = "builtin:$name", name = name, kind = "eq", builtin = true, note = note,
        eqLeft = g.map { it.toFloat() }, eqRight = g.map { it.toFloat() },
    )

    private fun voice(name: String, note: String, v: VoiceParams) = Preset(
        id = "builtin:$name", name = name, kind = "voice", builtin = true, note = note, voice = v,
    )

    /** EQ + voicing sub, tanpa mode/xover/volume. Band 0..2 selalu 0 → bekerja penuh di mode nirkabel / 2.1. */
    private fun scene(name: String, note: String, g: List<Int>, v: VoiceParams) = Preset(
        id = "builtin:$name", name = name, kind = "scene", builtin = true, note = note,
        eqLeft = g.map { it.toFloat() }, eqRight = g.map { it.toFloat() }, voice = v,
    )

    val all: List<Preset> = listOf(
        eq("Flat", "Semua band 0 dB.", List(15) { 0 }),
        eq("Bass Boost", "Bass lembut (+6 dB di 20 Hz). Hanya mode stereo; di 2.1/nirkabel pakai preset voicing sub atau scene.", listOf(6, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        eq("Vokal Jernih", "Kurangi gemuruh, angkat 1–3 kHz.", listOf(-6, -5, -3, -2, -1, 0, 0, 1, 2, 3, 3, 2, 1, 0, -1)),
        eq("Treble Boost", "Angkat 1,2–16 kHz.", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 5)),
        eq("Loudness", "Bass dan treble naik untuk volume rendah (bass ≤ 50 Hz hanya mode stereo).", listOf(6, 5, 3, 1, 0, 0, -1, -1, 0, 0, 1, 2, 3, 4, 3)),
        eq("Film", "Bass dalam + dialog jelas.", listOf(5, 4, 3, 2, 0, 0, 0, 0, 1, 2, 2, 2, 2, 1, 0)),
        eq("V-Shape", "Bass dan treble menonjol, mid turun.", listOf(4, 3, 2, 1, 0, -1, -2, -2, -1, 0, 1, 2, 3, 3, 2)),
        eq("Malam", "Bass dan treble diredam untuk tetangga.", listOf(-3, -3, -2, -1, 0, 0, 0, 0, 0, 0, 0, 0, -1, -2, -3)),
        eq("Satelit kecil", "Isi 200–500 Hz di atas crossover (maks +2 dB).", listOf(0, 0, 0, 0, 0, 1, 2, 1, 0, 0, 0, 0, 0, 0, 0)),
        eq("Suara / Podcast", "Angkat 0,8–3 kHz, redam 200–315 Hz.", listOf(0, 0, 0, 0, 0, -1, -1, 0, 1, 2, 3, 2, 1, 0, 0)),
        eq("Hangat", "Tambah 200–500 Hz, lembutkan 5–16 kHz.", listOf(0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, -1, -2, -2)),
        eq("Anti-desis", "Redam 3–16 kHz untuk rekaman yang tajam.", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -2, -2, -1)),
        eq("Game", "Angkat 1,2–8 kHz (langkah kaki, detail).", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 1, 0)),
        eq("Akustik", "Hangat di 125–200 Hz, kehadiran dan udara 3–16 kHz untuk gitar/vokal.", listOf(0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 2, 2, 1, 1)),
        eq("Jazz", "Bass bulat, mid netral, sedikit kilau di atas.", listOf(0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 1, 2, 1, 1)),
        eq("Rock", "Hentakan 80–125 Hz dan gitar tajam 1,2–5 kHz, mid rendah sedikit turun.", listOf(0, 0, 0, 2, 1, 0, -1, -1, 0, 1, 2, 3, 3, 2, 1)),
        eq("Elektronik", "Kick 80–125 Hz kuat, hi-hat dan synth cerah 3–8 kHz.", listOf(0, 0, 0, 2, 1, 0, 0, -1, -1, 0, 1, 2, 3, 3, 2)),
        eq("Klasik", "Hampir datar, sedikit udara di 5–16 kHz.", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 2, 2)),
        eq("Berita / Siaran", "Kurangi bass, tegaskan 800 Hz–3 kHz agar ucapan jelas.", listOf(0, 0, 0, -2, -2, -1, 0, 1, 2, 3, 3, 2, 0, -1, -2)),
        eq("Speaker meja", "Isi 80–200 Hz untuk speaker kecil jarak dekat (maks +2 dB).", listOf(0, 0, 0, 2, 2, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1)),
        eq("Anti-gemuruh", "Kurangi 80–315 Hz saat ruangan bergema atau dinding dekat.", listOf(0, 0, 0, -2, -3, -2, -1, 0, 0, 0, 0, 0, 0, 0, 0)),
        voice(
            "Sub kecil (3–5\")", "HPF 45 Hz, peaking +3 dB di 60 Hz (slave ≥ 2.9.20; di 2.9.19 jatuh ke band 80 Hz).",
            VoiceParams(on = true, hpf = 45, pkHz = 60, pkDb10 = 30)
        ),
        voice(
            "Sub besar (8–12\")", "HPF 28 Hz, peaking +2 dB di 45 Hz (slave ≥ 2.9.20; di 2.9.19 jatuh ke band 80 Hz).",
            VoiceParams(on = true, hpf = 28, pkHz = 45, pkDb10 = 20)
        ),
        voice(
            "Sub sealed kecil", "HPF 40 Hz, peaking +2 dB di 60 Hz (Q 1,2). Bass lebih berisi, tetap terlindungi.",
            VoiceParams(on = true, hpf = 40, pkHz = 60, pkDb10 = 20, pkQ10 = 12)
        ),
        voice(
            "Sub ported", "HPF 35 Hz tepat di bawah tuning port, tanpa peaking. Cegah excursion di bawah tuning.",
            VoiceParams(on = true, hpf = 35, pkHz = 50, pkDb10 = 0)
        ),
        voice(
            "Ruangan kecil", "HPF 35 Hz, peaking −2 dB di 55 Hz (Q 1,5): kurangi bass menggelegar di apartemen.",
            VoiceParams(on = true, hpf = 35, pkHz = 55, pkDb10 = -20, pkQ10 = 15)
        ),
        voice(
            "Sub aman", "HPF 40 Hz, tanpa peaking — pilihan paling aman karena firmware tidak punya limiter.",
            VoiceParams(on = true, hpf = 40, pkHz = 60, pkDb10 = 0)
        ),
        voice(
            "Sub sedang (6–8\")", "HPF 35 Hz, peaking +2,5 dB di 55 Hz (Q 1,0). Kompromi aman untuk sub ukuran menengah.",
            VoiceParams(on = true, hpf = 35, pkHz = 55, pkDb10 = 25, pkQ10 = 10)
        ),
        voice(
            "Ruangan besar", "HPF 25 Hz, peaking +1 dB di 40 Hz. Bass dalam tanpa menambah gemuruh di ruang luas.",
            VoiceParams(on = true, hpf = 25, pkHz = 40, pkDb10 = 10, pkQ10 = 10)
        ),
        voice("Voicing OFF", "Matikan HPF subsonic dan peaking.", VoiceParams(on = false)),
        scene(
            "Malam (scene)", "EQ lembut + bass sub dikurangi 3 dB di 55 Hz. Tidak mengubah volume.",
            listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -2, -3),
            VoiceParams(on = true, hpf = 40, pkHz = 55, pkDb10 = -30, pkQ10 = 12)
        ),
        scene(
            "Film (scene)", "Dialog jelas + bass dalam (+3 dB di 45 Hz). Tidak mengubah volume.",
            listOf(0, 0, 0, 2, 0, 0, 0, 0, 1, 2, 2, 2, 2, 1, 0),
            VoiceParams(on = true, hpf = 30, pkHz = 45, pkDb10 = 30, pkQ10 = 10)
        ),
        scene(
            "Musik (scene)", "Sedikit V-shape + bass +2 dB di 50 Hz. Tidak mengubah volume.",
            listOf(0, 0, 0, 0, 0, 0, -1, -1, 0, 0, 1, 1, 2, 2, 1),
            VoiceParams(on = true, hpf = 32, pkHz = 50, pkDb10 = 20, pkQ10 = 10)
        ),
        scene(
            "Pesta (scene)", "Bass tebal (+3,5 dB di 55 Hz) dan treble cerah. Pantau volume: tanpa limiter.",
            listOf(0, 0, 0, 1, 1, 0, -1, -1, 0, 0, 1, 2, 2, 2, 1),
            VoiceParams(on = true, hpf = 35, pkHz = 55, pkDb10 = 35, pkQ10 = 12)
        ),
        scene(
            "Game (scene)", "Detail 1,2–8 kHz + bass +3 dB di 50 Hz. Tidak mengubah volume.",
            listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 1, 0),
            VoiceParams(on = true, hpf = 35, pkHz = 50, pkDb10 = 30, pkQ10 = 10)
        ),
        scene(
            "Elektronik (scene)", "Kick dan synth cerah + bass +3,5 dB di 50 Hz (Q 1,2). Pantau volume: tanpa limiter.",
            listOf(0, 0, 0, 2, 1, 0, 0, -1, -1, 0, 1, 2, 3, 3, 2),
            VoiceParams(on = true, hpf = 30, pkHz = 50, pkDb10 = 35, pkQ10 = 12)
        ),
    )
}
