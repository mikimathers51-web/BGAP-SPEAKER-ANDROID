package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.bluetooth.SppClient
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassBadge
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.ui.components.GlassGap
import com.tas5805m.ctrl.ui.components.PollWhileStarted
import com.tas5805m.ctrl.ui.components.SendSlider
import com.tas5805m.ctrl.ui.components.SendSliderInt
import com.tas5805m.ctrl.ui.components.SwitchRow
import com.tas5805m.ctrl.ui.theme.BadRed
import com.tas5805m.ctrl.ui.theme.OkGreen
import com.tas5805m.ctrl.ui.theme.WarnAmber
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Psychoacoustic Bass Enhancement (CAPS `pbass`, SEMUA mode 0–4): firmware menambahkan harmonik 2f/3f dari bass ke L+R sebelum I2S,
 * sehingga speaker kecil / sub yang tidak turun dalam tetap "terdengar" berbass (missing fundamental).
 * FC = frekuensi terendah yang masih dimainkan speaker (harmonik mulai dari sini). Mode 4: harmonik ikut ke satelit; sub nirkabel menerima audio asli.
 */
@Composable
private fun PbassCard(state: AmpState, spp: SppClient) {
    GlassCard {
        CardTitle("Psychoacoustic Bass", trailing = {
            GlassBadge(if (!state.pbass) "OFF" else if (state.pbassActive) "ON · ${state.pbassAmt} %" else "ON")
        })
        SwitchRow(
            title = "Bass virtual (harmonik)",
            sub = "Menambah harmonik bass supaya terdengar lebih dalam di speaker kecil / sub yang terbatas. Berlaku di semua mode.",
            checked = state.pbass,
            onChange = { spp.setPbass(it) },
        )
        if (state.pbass) {
            val amt = state.pbassAmt
            val fc = state.pbassFc
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("JUMLAH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$amt %", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            SendSlider(
                value = amt.toFloat(),
                onSend = { v -> spp.setPbassCfg(((v / 5f).roundToInt() * 5).coerceIn(0, 100), fc) },
                valueRange = 0f..100f,
                steps = 19,
                live = false,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("FC (HARMONIK MULAI DI)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$fc Hz", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            SendSlider(
                value = fc.toFloat(),
                onSend = { v -> spp.setPbassCfg(amt, ((v / 10f).roundToInt() * 10).coerceIn(40, 150)) },
                valueRange = 40f..150f,
                steps = 10,
                live = false,
            )
            Text(
                "Setel FC mendekati frekuensi terendah yang masih dimainkan speaker (mis. 60–100 Hz). Harmonik ditambahkan ke sinyal: " +
                    "volume efektif naik dan ada limiter lunak, tetapi bass keras tetap bisa membuat driver over-excursion — uji dengan volume kecil.",
                style = MaterialTheme.typography.bodySmall,
                color = WarnAmber,
            )
        }
    }
}

/**
 * Loudness sub (firmware ≥ 2.9.25, CAPS `subloud`, mode 1–4; mode 0 tanpa sub): gain sub naik otomatis saat master volume turun,
 * supaya bass tetap terdengar pelan-pelan. Boost 0 dB di master ≥ "mulai di", naik linear (dB) sampai boost maks di master ≤ "penuh di".
 * Boost = gain digital lebih besar → bass sangat keras bisa clipping; batas keras +6 dB.
 */
@Composable
private fun SubLoudCard(state: AmpState, spp: SppClient) {
    GlassCard {
        CardTitle("Loudness sub", trailing = {
            GlassBadge(if (state.subloud) "+%.1f dB".format(Locale.US, state.subloudCur / 10f) else "OFF")
        })
        SwitchRow(
            title = "Sub otomatis lebih keras saat volume pelan",
            sub = "Telinga kurang peka pada bass di volume kecil. Boost naik saat master turun, hilang di volume tinggi.",
            checked = state.subloud,
            onChange = { spp.setSubloud(it) },
        )
        if (state.subloud) {
            val db10 = state.subloudDb10
            val lo = state.subloudLo
            if (state.subloudEff >= 0) {   // firmware ≥ 2.9.26 melaporkan ph/pv/eff
                SwitchRow(
                    title = "Ikut volume ponsel",
                    sub = if (state.subloudPhone)
                        "Volume efektif ${state.subloudEff} % (master ${state.master}%, ponsel " +
                            (if (state.phoneVol >= 0) "${(state.phoneVol * 100 / 127)} %" else "belum terbaca") + ")."
                    else "Hanya volume master yang dipakai; geser volume ponsel tidak mengubah boost.",
                    checked = state.subloudPhone,
                    onChange = { spp.setSubloudPhone(it) },
                )
            }
            val hi = state.subloudHi
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("BOOST MAKS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+%.1f dB".format(Locale.US, db10 / 10f), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            SendSlider(
                value = db10 / 10f,
                onSend = { v -> spp.setSubloudCfg(((v * 2f).roundToInt() * 5).coerceIn(0, 60), lo, hi) },   // langkah 0,5 dB
                valueRange = 0f..6f,
                steps = 11,
                live = false,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("MULAI DI MASTER ≤", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$hi %", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            SendSlider(
                value = hi.toFloat(),
                onSend = { v -> spp.setSubloudCfg(db10, lo, ((v / 5f).roundToInt() * 5).coerceIn(lo + 5, 100)) },
                valueRange = 20f..100f,
                steps = 15,
                live = false,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("BOOST PENUH DI MASTER ≤", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$lo %", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            SendSlider(
                value = lo.toFloat(),
                onSend = { v -> spp.setSubloudCfg(db10, ((v / 5f).roundToInt() * 5).coerceIn(0, hi - 5), hi) },
                valueRange = 0f..95f,
                steps = 18,
                live = false,
            )
            Text(
                "Boost = gain digital lebih besar: bass sangat keras bisa clipping. Uji dengan sweep bass di volume pelan.",
                style = MaterialTheme.typography.bodySmall,
                color = WarnAmber,
            )
        }
    }
}

/**
 * 2-way aktif mono (firmware ≥ 2.9.23, CAPS `tway`, hanya mode 2): kanal L = tweeter (HPF), kanal R = woofer (LPF),
 * Linkwitz-Riley orde-4 di satu frekuensi. Batas bawah (`twayMin`) melindungi tweeter karena firmware tidak punya limiter.
 * Tiap perubahan frekuensi menulis ulang biquad DSP (I2C) → kirim hanya saat slider dilepas.
 */
@Composable
private fun TwayCard(state: AmpState, spp: SppClient) {
    GlassCard {
        val m5 = state.isStereo2Way || state.isMono2Way   // mode 5 (2 chip stereo) / 6 (1 chip mono): 2-way selalu aktif (tanpa sakelar)
        CardTitle(if (state.isStereo2Way) "2-way aktif (stereo, 2 chip)" else if (state.isMono2Way) "2-way aktif (mono, 1 chip)" else "2-way aktif (mono)", trailing = { GlassBadge(if (m5 || state.tway) "${state.twayHz} Hz · LR4" else "OFF") })
        if (!m5) {
            SwitchRow(
                title = "Aktifkan 2-way",
                sub = "Kanal L = tweeter (HPF), kanal R = woofer (LPF), mixer mono. Pastikan tweeter ke OUT_L dan woofer ke OUT_R sebelum mengaktifkan.",
                checked = state.tway,
                onChange = { spp.setTway(it) },
            )
        } else {
            Text(
                if (state.isStereo2Way)
                    "Chip 1 = dua tweeter (OUT_L kiri, OUT_R kanan, HPF), chip 2 = dua woofer (OUT_L kiri, OUT_R kanan, LPF); sinyal tetap stereo. " +
                        "Pastikan wiring sesuai sebelum memutar musik."
                else
                    "Satu chip: OUT_L = tweeter (HPF), OUT_R = woofer (LPF), sinyal mono (L+R)/2. Pastikan tweeter ke OUT_L dan woofer ke OUT_R sebelum memutar musik.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (m5 || state.tway) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CROSSOVER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.twayHz} Hz", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            SendSlider(
                value = state.twayHz.toFloat(),
                onSend = { v -> spp.setTwayHz(((v / 50f).roundToInt() * 50).coerceIn(state.twayMin, state.twayMax)) },
                valueRange = state.twayMin.toFloat()..state.twayMax.toFloat(),
                live = false,
                snap = { v -> (v / 50f).roundToInt() * 50f },
            )
            Text(
                "HPF tweeter dibatasi ≥ ${state.twayMin} Hz. Tanpa limiter: turunkan volume dan periksa dengan sweep sebelum musik. " +
                    "Slider Tweeter/Woofer mengatur level masing-masing; tombol fase di Woofer membalik polaritas kedua woofer.",
                style = MaterialTheme.typography.bodySmall,
                color = WarnAmber,
            )
        }
    }
}

/**
 * Crossover. Firmware ≥ 2.9.22 (CAPS `xover2`): HPF satelit dan LPF sub terpisah, dengan sakelar "kunci bersama"
 * (aktif = satu `xover` untuk keduanya, seperti sebelumnya). Firmware lama: satu slider.
 * Tabel profil HF_/LF_ hanya 60–150 Hz per 10 Hz; tiap perubahan menyetel ulang filter DSP (I2C) → kirim saat dilepas.
 */
@Composable
private fun CrossoverCard(state: AmpState, spp: SppClient) {
    val xmax = state.xoverMax
    val snap = { v: Float -> ((v / 10f).roundToInt() * 10).coerceIn(60, xmax) }
    val xsteps = (xmax - 60) / 10 - 1
    val split = state.hasXover2
    var linked by remember { mutableStateOf(state.xover == state.xoverLp) }

    GlassCard {
        CardTitle("Crossover", trailing = { GlassBadge("60–$xmax Hz · 10 Hz") })
        if (!split) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("LPF SUB  ⇄  HPF SAT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.xover} Hz", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            SendSlider(
                value = state.xover.toFloat(),
                onSend = { spp.setXover(snap(it)) },
                valueRange = 60f..xmax.toFloat(),
                steps = xsteps,
                live = false,
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (linked) "HPF SAT  ⇄  LPF SUB" else "HPF SAT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.xover} Hz", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            SendSlider(
                value = state.xover.toFloat(),
                onSend = { v -> if (linked) spp.setXover(snap(v)) else spp.setXoverHp(snap(v)) },
                valueRange = 60f..xmax.toFloat(),
                steps = xsteps,
                live = false,
            )
            if (!linked) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("LPF SUB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.xoverLp} Hz", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                SendSlider(
                    value = state.xoverLp.toFloat(),
                    onSend = { v -> spp.setXoverLp(snap(v)) },
                    valueRange = 60f..xmax.toFloat(),
                    steps = xsteps,
                    live = false,
                )
                val hp = state.xover
                val lp = state.xoverLp
                if (hp != lp) {
                    Text(
                        if (hp < lp) "Overlap $hp–$lp Hz: satelit dan sub sama-sama memainkan rentang ini (bass bisa menebal)."
                        else "Celah $lp–$hp Hz: tidak ada yang memainkan rentang ini (bass bisa melubang).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (xmax > 150 && (state.xover > 150 || state.xoverLp > 150)) {
                Text(
                    "Di atas 150 Hz firmware memakai filter Linkwitz-Riley orde-4 hitungan (bukan tabel). Pastikan driver sub sanggup memainkan rentang ini dan ukur dengan sweep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SwitchRow(
                title = "Kunci bersama",
                sub = "HPF satelit = LPF sub (satu nilai). Matikan untuk mengatur terpisah; filter dirancang untuk frekuensi sama, periksa respons dengan sweep.",
                checked = linked,
                onChange = { on ->
                    linked = on
                    // Menyalakan kunci menyamakan LPF ke HPF (satu perintah `xover`)
                    if (on && state.xover != state.xoverLp) spp.setXover(state.xover)
                },
            )
        }
    }
}

/**
 * Home / Kontrol — gaya Glass (sama dengan prototipe), hanya berisi kontrol yang benar-benar ada di firmware:
 * master, mix kanal (sat/sub/ctr), polaritas sub, crossover, gain analog, mode, mute/amp state, auto-standby, level.
 * (Loudness, Night Mode, sumber input, Sat L/R terpisah, PVDD/suhu TIDAK ada di firmware → sengaja tidak ditampilkan.)
 */
@Composable
fun ControlScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp

    if (!state.connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BluetoothDisabled, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Text("Hubungkan perangkat di tab Koneksi", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    // Level meter hidup: poll `lvl`/`lvl2` ±2×/dtk hanya selama layar Home tampil (baris LVL tidak masuk log).
    PollWhileStarted {
        while (true) {
            val s = vm.state.value
            if (s.hasLvl) spp.requestLvl()
            if (s.hasLvl2) spp.requestLvl2()
            delay(500)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // —— Master volume: angka besar + slider ——
        GlassCard {
            CardTitle("Master Volume", trailing = { GlassBadge(state.modeName()) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text(
                    buildAnnotatedString {
                        append("${state.master}")
                        withStyle(SpanStyle(fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("%") }
                    },
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            GlassGap(8)
            SendSliderInt(
                value = state.master,
                onSend = { spp.setMaster(it) },
                valueRange = 0..100,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // —— Channel mix: sat / sub / ctr + polaritas sub. Mode 0: firmware hanya memakai MASTER. ——
        if (state.mode != 0) {
            GlassCard {
                CardTitle("Channel Mix")
                ChannelRow(if (state.isStereo2Way) "Woofer (2×)" else if (state.twayActive) "Woofer (R)" else "Subwoofer", state.sub, trailing = {
                    if (state.hasSubInv && state.hasSubPath) {
                        OutlinedButton(
                            onClick = { spp.setSubInv(!state.subInv) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (state.subInv) 0.8f else 0.3f)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp),
                        ) { Text(if (state.subInv) "180°" else "0°", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    }
                }) { spp.setSub(it) }
                GlassGap()
                ChannelRow(if (state.isStereo2Way) "Tweeter (2×)" else if (state.twayActive) "Tweeter (L)" else "Satellite", state.sat) { spp.setSat(it) }
                if (state.is31) {
                    GlassGap()
                    ChannelRow("Center", state.ctr) { spp.setCtr(it) }
                }
            }
        }

        // —— Crossover: HPF satelit & LPF sub (terpisah bila firmware ≥ 2.9.22 / CAPS xover2) ——
        // 2-way aktif mono (mode 2, CAPS tway): L = tweeter HPF, R = woofer LPF (LR4); menggantikan crossover tabel saat aktif
        if (state.hasTway && (state.mode == 2 || state.mode == 5 || state.mode == 6)) TwayCard(state, spp)
        // Psychoacoustic bass (CAPS pbass): semua mode 0–4
        if (state.hasPbass) PbassCard(state, spp)
        // Loudness sub (mode 1–4, CAPS subloud; mode 0 tanpa sub): sub dinaikkan otomatis saat master volume turun
        if (state.hasSubloud && state.mode != 0 && state.mode != 5) SubLoudCard(state, spp)
        if (!state.twayActive) CrossoverCard(state, spp)

        // —— Gain analog (0..31, −0,5 dB per langkah) ——
        GlassCard {
            CardTitle("Analog Gain", trailing = { GlassBadge("${"%.1f".format(-state.gain * 0.5)} dB") })
            SendSliderInt(
                value = state.gain,
                onSend = { spp.setGain(it) },
                valueRange = 0..31,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // —— Mode (hanya yang diterima firmware menurut CAPS) ——
        GlassCard {
            CardTitle("Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Stereo", 1 to "2.1 D", 2 to "2.1 S", 3 to "3.1", 4 to "W.Sub", 5 to "2W 2c", 6 to "2W 1c")
                    .filter { (m, _) -> state.modeAllowed(m) || state.mode == m }
                    .forEach { (m, label) ->
                        FilterChip(
                            selected = state.mode == m,
                            enabled = state.modeAllowed(m),
                            onClick = { if (state.mode != m) spp.setMode(m) },
                            label = { Text(label, fontSize = 11.sp) },
                        )
                    }
            }
        }

        // —— Mute + amp state + auto-standby ——
        GlassCard {
            CardTitle("Amplifier", trailing = {
                if (state.inStandby) GlassBadge("STANDBY", color = WarnAmber)
            })
            SwitchRow(
                title = "Mute",
                sub = "Bisukan output (amp state: mute)",
                checked = state.ampstate == "mute",
                onChange = { spp.setAmp(if (it) "mute" else "play") },
            )
            GlassGap(8)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("play", "hiz", "sleep").forEach { s ->
                    FilterChip(
                        selected = state.ampstate == s,
                        onClick = { spp.setAmp(s) },
                        label = { Text(s.uppercase(), fontSize = 11.sp) },
                    )
                }
            }
            if (state.hasStandby) {
                GlassGap(12)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Auto-standby", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        if (state.standbyMin == 0) "OFF" else "${state.standbyMin} menit",
                        fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary,
                    )
                }
                SendSliderInt(
                    value = state.standbyMin,
                    onSend = { spp.setStandby((it / 5) * 5) },
                    valueRange = 0..120,
                    steps = 23,
                    live = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // —— Level meter ——
        if (state.hasLvl) {
            GlassCard {
                CardTitle("Level", trailing = {
                    TextButton(onClick = {
                        spp.requestLvl()
                        if (state.hasLvl2) spp.requestLvl2()
                    }) { Text("↻", fontSize = 14.sp) }
                })
                LevelBar("L", state.lvlL)
                LevelBar("R", state.lvlR)
                if (state.hasLvl2) {
                    LevelBar("L2", state.lvl2L)
                    LevelBar("R2", state.lvl2R)
                }
            }
        }

        if (state.hasSave) {
            Button(
                onClick = { spp.save() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Save to AMP")
            }
        }
    }
}

/** Satu baris kanal: label + nilai (%) mono + slider; [trailing] untuk tombol kecil (mis. fase). */
@Composable
private fun ChannelRow(
    label: String,
    value: Int,
    trailing: @Composable () -> Unit = {},
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            trailing()
            Spacer(Modifier.weight(1f))
            Text("$value%", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        SendSliderInt(
            value = value,
            onSend = onChange,
            valueRange = 0..100,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun LevelBar(label: String, deciDb: Int) {
    val norm = ((deciDb + 1200) / 1200f).coerceIn(0f, 1f)
    val db = deciDb / 10f
    val color = when {
        norm > 0.9f -> BadRed
        norm > 0.7f -> WarnAmber
        else -> OkGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(
            progress = { norm },
            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        )
        Text("${db.roundToInt()}", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}
