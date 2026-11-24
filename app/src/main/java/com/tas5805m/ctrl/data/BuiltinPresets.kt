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
        // —— Encore 2 (dari HTML demo PU) — kind=full: set mode + volume + EQ ——
        Preset(
            id = "builtin:Encore 2 Party (2.1)",
            name = "Encore 2 Party (2.1)",
            kind = "full", builtin = true,
            note = "Mode 2 (1-chip 2.1), master 78, sat 90, sub 100. EQ satelit + sub boost 80–125 Hz.",
            mode = 2, master = 78, sat = 90, sub = 100, xover = 120,
            eqLeft = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            eqRight = listOf(0f, 0f, 0f, 3f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        ),
        Preset(
            id = "builtin:Encore 2 Pelan (2.1)",
            name = "Encore 2 Pelan (2.1)",
            kind = "full", builtin = true,
            note = "Mode 2, master 55 (pelan). EQ satelit sedikit treble, sub boost 80–125 Hz.",
            mode = 2, master = 55, sat = 90, sub = 100, xover = 120,
            eqLeft = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f),
            eqRight = listOf(0f, 0f, 0f, 3f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        ),
        Preset(
            id = "builtin:Encore 2 Party (Stereo)",
            name = "Encore 2 Party (Stereo)",
            kind = "full", builtin = true,
            note = "Mode 0 stereo, master 75. EQ V-ish + treble cerah.",
            mode = 0, master = 75, sat = 100, sub = 100,
            eqLeft = listOf(3f, 3f, 2f, 2f, 2f, 0f, -1f, -1f, 0f, 1f, 2f, 2f, 2f, 2f, 1f),
            eqRight = listOf(3f, 3f, 2f, 2f, 2f, 0f, -1f, -1f, 0f, 1f, 2f, 2f, 2f, 2f, 1f),
        ),
        Preset(
            id = "builtin:Encore 2 Party (2chip 2.1)",
            name = "Encore 2 Party (2chip 2.1)",
            kind = "full", builtin = true,
            note = "Mode 1 (2-chip 2.1), master 78, sat 90, sub 100.",
            mode = 1, master = 78, sat = 90, sub = 100, xover = 120,
            eqLeft = List(15) { 0f }, eqRight = List(15) { 0f },
        ),
        Preset(
            id = "builtin:Encore 2 Party (2chip 3.1)",
            name = "Encore 2 Party (2chip 3.1)",
            kind = "full", builtin = true,
            note = "Mode 3 (2-chip 3.1), master 78, sat 90, sub 100, ctr 100.",
            mode = 3, master = 78, sat = 90, sub = 100, ctr = 100, xover = 120,
            eqLeft = List(15) { 0f }, eqRight = List(15) { 0f },
        ),
        Preset(
            id = "builtin:Encore 2 Party (nirkabel)",
            name = "Encore 2 Party (nirkabel)",
            kind = "full", builtin = true,
            note = "Mode 4 wireless sub, master 78, sat 90, sub 100. Pair slave dulu di tab Wireless.",
            mode = 4, master = 78, sat = 90, sub = 100, xover = 120,
            eqLeft = List(15) { 0f }, eqRight = List(15) { 0f },
        ),
        Preset(
            id = "builtin:Encore 2 Party (2chip 2-way)",
            name = "Encore 2 Party (2chip 2-way)",
            kind = "full", builtin = true,
            note = "Mode 5 (2-chip stereo 2-way), master 78.",
            mode = 5, master = 78, sat = 90, sub = 100,
            eqLeft = List(15) { 0f }, eqRight = List(15) { 0f },
        ),
        Preset(
            id = "builtin:Encore 2 Party (1chip 2-way)",
            name = "Encore 2 Party (1chip 2-way)",
            kind = "full", builtin = true,
            note = "Mode 6 (1-chip mono 2-way), master 78, sat=tweeter, sub=woofer.",
            mode = 6, master = 78, sat = 90, sub = 100,
            eqLeft = List(15) { 0f },
            eqRight = listOf(0f, 0f, 0f, 3f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        ),

        // —— EQ genre tambahan ——
        eq("Jazz club", "Warm mid, soft treble. Cocok instrumen akustik.",
            listOf(2, 2, 1, 1, 0, 0, 1, 2, 1, 0, 0, -1, -1, -1, -2)),
        eq("Hard rock", "Kick & snare menonjol, gitar mid naik.",
            listOf(4, 3, 2, 1, 0, 0, 1, 2, 1, 0, 1, 2, 2, 1, 0)),
        eq("Pop", "Sedikit V-shape, vokal di depan.",
            listOf(3, 2, 1, 0, 0, -1, 0, 1, 2, 2, 2, 1, 1, 1, 0)),
        eq("Hip-Hop / R&B", "Bass tebal + presence vokal 2–5 kHz.",
            listOf(5, 4, 3, 2, 1, 0, 0, 0, 1, 2, 3, 2, 1, 0, -1)),
        eq("EDM / Dance", "Sub + kick kuat, hi-hat cerah. Pantau clipping.",
            listOf(6, 5, 3, 1, 0, -1, -2, -1, 0, 1, 2, 3, 4, 4, 3)),
        eq("Orchestra", "Netral-hangat, sedikit air di atas 8 kHz.",
            listOf(1, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 2, 2)),
        eq("Acoustic live", "Body gitar/piano 200–500 Hz, string detail.",
            listOf(0, 0, 1, 2, 1, 1, 2, 1, 0, 0, 1, 2, 1, 1, 0)),
        eq("Bass Reduksi", "Kurangi 20–80 Hz (ruangan kecil / tetangga).",
            listOf(-6, -5, -4, -2, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        eq("Smiley", "Bass + treble kuat, mid rata (smiley curve klasik).",
            listOf(5, 4, 3, 1, 0, -1, -2, -2, -1, 0, 1, 2, 3, 4, 4)),
        eq("Presence", "Angkat 2–5 kHz untuk kejelasan dialog/vokal.",
            listOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 2, 1, 0)),
        eq("Warm", "Redam harsh 4–8 kHz, mid lembut.",
            listOf(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, -1, -2, -2, -1, 0)),
        eq("Bright", "Treble terbuka tanpa boost bass.",
            listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 4, 3)),

        // —— Voicing sub tambahan ——
        voice(
            "Sub 10–12\"",
            "HPF 28 Hz, peaking +3 dB di 45 Hz (Q 0,9). Untuk driver besar.",
            VoiceParams(on = true, hpf = 28, pkHz = 45, pkDb10 = 30, pkQ10 = 9)
        ),
        voice(
            "Sub sealed mini",
            "HPF 45 Hz, peaking +2 dB di 60 Hz. Lindungi driver sealed kecil.",
            VoiceParams(on = true, hpf = 45, pkHz = 60, pkDb10 = 20, pkQ10 = 12)
        ),
        voice(
            "Sub car / ported",
            "HPF 32 Hz, peaking +4 dB di 50 Hz (Q 1,2). Ported / car audio style.",
            VoiceParams(on = true, hpf = 32, pkHz = 50, pkDb10 = 40, pkQ10 = 12)
        ),
        voice(
            "Sub flat (hanya HPF)",
            "HPF 35 Hz, tanpa peaking — lindungi cone tanpa ubah timbre.",
            VoiceParams(on = true, hpf = 35, pkHz = 55, pkDb10 = 0, pkQ10 = 10)
        ),

        // —— Scene tambahan (EQ + voice, aman volume) ——
        scene(
            "Podcast (scene)",
            "Vokal jelas 1–4 kHz, bass sub dikurangi. Tidak mengubah volume.",
            listOf(0, 0, 0, -2, -1, 0, 0, 1, 2, 3, 3, 2, 1, 0, -1),
            VoiceParams(on = true, hpf = 40, pkHz = 55, pkDb10 = -20, pkQ10 = 12)
        ),
        scene(
            "Nonton TV (scene)",
            "Dialog + sedikit bass film. Tidak mengubah volume.",
            listOf(0, 0, 0, 1, 0, 0, 0, 0, 1, 2, 2, 2, 1, 0, 0),
            VoiceParams(on = true, hpf = 32, pkHz = 45, pkDb10 = 15, pkQ10 = 10)
        ),
        scene(
            "Jazz live (scene)",
            "Warm acoustic + sub halus. Tidak mengubah volume.",
            listOf(0, 0, 0, 1, 0, 0, 1, 2, 1, 0, 0, -1, -1, -1, -1),
            VoiceParams(on = true, hpf = 35, pkHz = 50, pkDb10 = 10, pkQ10 = 10)
        ),
        scene(
            "Rock live (scene)",
            "Kick/snare + gitar mid, bass +2,5 dB di 55 Hz. Tidak mengubah volume.",
            listOf(0, 0, 0, 1, 0, 0, 1, 2, 1, 0, 1, 2, 2, 1, 0),
            VoiceParams(on = true, hpf = 32, pkHz = 55, pkDb10 = 25, pkQ10 = 11)
        ),
        scene(
            "EDM club (scene)",
            "Sub + kick tebal, hi-hat cerah. Pantau volume: tanpa limiter.",
            listOf(0, 0, 0, 2, 1, 0, -1, -1, 0, 1, 2, 3, 4, 4, 3),
            VoiceParams(on = true, hpf = 30, pkHz = 50, pkDb10 = 40, pkQ10 = 12)
        ),
        scene(
            "Tenang malam (scene)",
            "Semua band redam, HPF 45 Hz. Tetangga-friendly.",
            listOf(0, 0, 0, -1, -1, 0, 0, 0, 0, 0, 0, -1, -2, -2, -2),
            VoiceParams(on = true, hpf = 45, pkHz = 55, pkDb10 = -25, pkQ10 = 12)
        ),
        scene(
            "Gaming FPS (scene)",
            "Footstep/detail 2–8 kHz + bass ledakan +2 dB. Tidak mengubah volume.",
            listOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 2, 1, 0),
            VoiceParams(on = true, hpf = 35, pkHz = 50, pkDb10 = 20, pkQ10 = 10)
        ),
        scene(
            "Karaoke (scene)",
            "Vokal depan 1–3 kHz, bass musik dikurangi sedikit.",
            listOf(0, 0, 0, -1, 0, 0, 0, 1, 2, 3, 3, 2, 1, 0, 0),
            VoiceParams(on = true, hpf = 40, pkHz = 55, pkDb10 = -10, pkQ10 = 10)
        ),

        // —— Full praktis (mode + volume + EQ) ——
        Preset(
            id = "builtin:Harian Stereo",
            name = "Harian Stereo",
            kind = "full", builtin = true,
            note = "Mode 0, master 70, EQ netral-hangat untuk mendengarkan harian.",
            mode = 0, master = 70, sat = 100, sub = 100,
            eqLeft = listOf(1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 0f),
            eqRight = listOf(1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 0f),
        ),
        Preset(
            id = "builtin:Harian 2.1",
            name = "Harian 2.1",
            kind = "full", builtin = true,
            note = "Mode 2 (1-chip 2.1), master 70, xover 120, EQ flat satelit.",
            mode = 2, master = 70, sat = 92, sub = 95, xover = 120,
            eqLeft = List(15) { 0f },
            eqRight = listOf(0f, 0f, 0f, 2f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        ),
        Preset(
            id = "builtin:Film 2.1",
            name = "Film 2.1",
            kind = "full", builtin = true,
            note = "Mode 2, master 75, dialog jelas + bass dalam.",
            mode = 2, master = 75, sat = 90, sub = 100, xover = 100,
            eqLeft = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 2f, 2f, 1f, 0f, 0f),
            eqRight = listOf(0f, 0f, 0f, 3f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        ),
        Preset(
            id = "builtin:Pesta 2.1",
            name = "Pesta 2.1",
            kind = "full", builtin = true,
            note = "Mode 2, master 85, V-shape + sub boost. Pantau clipping.",
            mode = 2, master = 85, sat = 95, sub = 100, xover = 120,
            eqLeft = listOf(0f, 0f, 0f, 1f, 0f, -1f, -1f, 0f, 0f, 1f, 2f, 2f, 2f, 1f, 0f),
            eqRight = listOf(0f, 0f, 0f, 4f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        ),
        Preset(
            id = "builtin:Malam pelan 2.1",
            name = "Malam pelan 2.1",
            kind = "full", builtin = true,
            note = "Mode 2, master 45, bass/treble redam — tetangga-friendly.",
            mode = 2, master = 45, sat = 90, sub = 85, xover = 130,
            eqLeft = listOf(0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -1f, -2f, -2f),
            eqRight = listOf(0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        ),
        Preset(
            id = "builtin:Podcast Stereo",
            name = "Podcast Stereo",
            kind = "full", builtin = true,
            note = "Mode 0, master 65, fokus presence, bass redam.",
            mode = 0, master = 65, sat = 100, sub = 100,
            eqLeft = listOf(-3f, -2f, -1f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 3f, 2f, 1f, 0f, -1f),
            eqRight = listOf(-3f, -2f, -1f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 3f, 2f, 1f, 0f, -1f),
        ),
        Preset(
            id = "builtin:Nirkabel harian",
            name = "Nirkabel harian",
            kind = "full", builtin = true,
            note = "Mode 4, master 72. Pair slave dulu di tab Wireless.",
            mode = 4, master = 72, sat = 92, sub = 95, xover = 120,
            eqLeft = List(15) { 0f }, eqRight = List(15) { 0f },
        ),
        Preset(
            id = "builtin:3.1 Film",
            name = "3.1 Film",
            kind = "full", builtin = true,
            note = "Mode 3 (sat + center + sub), master 75, dialog di center.",
            mode = 3, master = 75, sat = 88, sub = 100, ctr = 95, xover = 100,
            eqLeft = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 2f, 2f, 1f, 0f, 0f),
            eqRight = List(15) { 0f },
        ),
    )
}

