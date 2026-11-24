package com.tas5805m.ctrl.ui.screens

import com.tas5805m.ctrl.data.ModRules
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.ui.theme.OkGreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.bluetooth.SppClient
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.ui.components.SendSlider
import com.tas5805m.ctrl.ui.components.SendSliderInt
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * DSP lanjutan — semua diproses di chip TAS5805M: mixer 2×2, profil filter (mode 0), clipper,
 * modulasi PWM, topologi DAC (BTL/PBTL), gain analog, standby dan penyimpanan setelan.
 */
@Composable
fun DspScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp

    if (!state.connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BluetoothDisabled, null, tint = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Text("Hubungkan perangkat di tab Koneksi", color = Color.Gray)
            }
        }
        return
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MixerCard(state, spp)
        if (state.mode == 0 && state.hasEqp) EqProfileCard(state, spp)
        if (state.hasClip) ClipCard(state, spp)
        if (state.hasMod) ModCard(state, spp)
        if (state.hasDac) DacCard(state, spp)
        PowerCard(state, spp)
        PersistCard(state, spp)
        Spacer(Modifier.height(16.dp))
    }
}

// ------------------------------------------------------------------ util

@Composable
private fun DspCard(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardTitle(title, Modifier.padding(bottom = 0.dp))
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            content()
        }
    }
}

@Composable
private fun Labeled(label: String, valueText: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Text(valueText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
    content()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    options: List<Pair<String, T>>,
    selected: T?,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (label, v) ->
            FilterChip(
                selected = v == selected,
                onClick = { onSelect(v) },
                label = { Text(label) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

private fun f2(v: Float): String = String.format(Locale.US, "%.2f", v)

// ------------------------------------------------------------------ mixer

@Composable
private fun MixerCard(s: AmpState, spp: SppClient) {
    val locked = s.mixerLocked
    DspCard(
        "Mixer",
        if (locked) "Terkunci di mode 2 (1-chip 2.1)."
        else "Matriks 2×2 di chip: keluaran L = LL·L + LR·R, keluaran R = RL·L + RR·R.",
    ) {
        if (s.hasMixmode) {
            ChoiceChips(
                options = listOf("Stereo" to "stereo", "Inv" to "inv", "Mono" to "mono", "Kiri" to "left", "Kanan" to "right"),
                selected = null,
                enabled = !locked,
            ) { spp.setMixMode(it) }
        }

        // Lebar stereo = LL=RR=1+w, LR=RL=-w
        val w = -s.lr
        val isWidth = w >= -0.001f && abs(s.ll - (1f + w)) < 0.03f && abs(s.rr - (1f + w)) < 0.03f && abs(s.rl - s.lr) < 0.03f
        val widthPct = if (isWidth) (w * 100f).roundToInt().coerceIn(0, 50) else 0
        Labeled("Lebar stereo", if (isWidth) "$widthPct%" else "kustom") {
            SendSliderInt(
                value = widthPct,
                onSend = { pct ->
                    val ww = pct / 100f
                    spp.setMixer("ll", 1f + ww); spp.setMixer("rr", 1f + ww)
                    spp.setMixer("lr", -ww); spp.setMixer("rl", -ww)
                },
                valueRange = 0..50,
                live = false,
                enabled = !locked,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (s.ll > 1.3f || s.rr > 1.3f) {
            Text(
                "Pelebaran kuat: puncak bisa naik ≈ 3,5 dB — turunkan master bila terdengar pecah.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        val rows = listOf(
            Triple("LL (L → kiri)", "ll", s.ll),
            Triple("LR (R → kiri)", "lr", s.lr),
            Triple("RL (L → kanan)", "rl", s.rl),
            Triple("RR (R → kanan)", "rr", s.rr),
        )
        for ((label, ch, v) in rows) {
            Labeled(label, f2(v)) {
                SendSlider(
                    value = v,
                    onSend = { spp.setMixer(ch, it) },
                    valueRange = -1f..2f,
                    live = false,
                    enabled = !locked,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        OutlinedButton(
            enabled = !locked,
            onClick = {
                spp.setMixer("ll", 1f); spp.setMixer("lr", 0f)
                spp.setMixer("rl", 0f); spp.setMixer("rr", 1f)
            },
        ) { Text("Reset ke stereo normal") }
    }
}

// ------------------------------------------------------------------ profil filter (mode 0)

@Composable
private fun EqProfileCard(s: AmpState, spp: SppClient) {
    val idx = s.eqpLeft.coerceIn(0, 20)
    val type = when {
        idx == 0 -> 0
        idx <= 10 -> 1
        else -> 2
    }
    val freq = if (idx == 0) 100 else 60 + ((idx - 1) % 10) * 10
    fun index(t: Int, f: Int): Int = when (t) {
        0 -> 0
        1 -> 1 + (f - 60) / 10
        else -> 11 + (f - 60) / 10
    }
    DspCard("Profil filter (mode stereo)", "LPF/HPF tetap di BQ1–3 chip. Di mode 1–4 diatur otomatis oleh crossover.") {
        ChoiceChips(listOf("Flat" to 0, "LPF" to 1, "HPF" to 2), selected = type) { t ->
            spp.setEqProfile("b", index(t, freq))
        }
        Labeled("Frekuensi", if (type == 0) "—" else "$freq Hz") {
            SendSliderInt(
                value = freq,
                onSend = { f -> spp.setEqProfile("b", index(if (type == 0) 1 else type, f.coerceIn(60, 150) / 10 * 10)) },
                valueRange = 60..150,
                steps = 8,
                live = false,
                enabled = type != 0,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ------------------------------------------------------------------ clipper

@Composable
private fun ClipCard(s: AmpState, spp: SppClient) {
    val g = s.tagInt("CLIP", "gain") ?: 0
    val ml = s.tagInt("CLIP", "ml") ?: 0
    val mr = s.tagInt("CLIP", "mr") ?: 0
    DspCard("Clipper chip", "Pembatas puncak di chip. Satuan 0,1 dB. Salah setel membuat suara terdistorsi.") {
        Labeled("Gain clipper", "${g / 10f} dB") {
            SendSliderInt(g, { spp.setClip(it, ml, mr) }, -300..0, live = false, modifier = Modifier.fillMaxWidth())
        }
        Labeled("Batas kiri (ml)", "$ml") {
            SendSliderInt(ml, { spp.setClip(g, it, mr) }, 0..300, live = false, modifier = Modifier.fillMaxWidth())
        }
        Labeled("Batas kanan (mr)", "$mr") {
            SendSliderInt(mr, { spp.setClip(g, ml, it) }, 0..300, live = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ------------------------------------------------------------------ modulasi

@Composable
private fun ModCard(s: AmpState, spp: SppClient) {
    val mode = s.tag("MOD", "mode") ?: "bd"
    val freq = s.tag("MOD", "freq") ?: "768k"
    val bd = s.tag("MOD", "bd") ?: "80k"
    DspCard("Modulasi PWM", "Ubah hanya bila paham: memengaruhi EMI, efisiensi, dan panas. Default: bd / 768k / 80k. Bandwidth dibatasi otomatis sesuai datasheet (mis. 384k + Hybrid/1SPW hanya 80k).") {
        Text("Mode", style = MaterialTheme.typography.labelMedium)
        ChoiceChips(listOf("BD" to "bd", "1SPW" to "1spw", "Hybrid" to "hybrid"), mode) { spp.setMod(it, freq, ModRules.clampBw(it, freq, bd)) }
        Text("Frekuensi switching", style = MaterialTheme.typography.labelMedium)
        ChoiceChips(listOf("384k" to "384k", "480k" to "480k", "576k" to "576k", "768k" to "768k"), freq) { spp.setMod(mode, it, ModRules.clampBw(mode, it, bd)) }
        Text("Bandwidth loop class-D", style = MaterialTheme.typography.labelMedium)
        ChoiceChips(ModRules.allowedBw(mode, freq).map { it to it }, bd) { spp.setMod(mode, freq, it) }
    }
}

// ------------------------------------------------------------------ DAC

@Composable
private fun DacCard(s: AmpState, spp: SppClient) {
    val mode = s.tag("DAC", "mode") ?: "btl"
    val mode2 = s.tag("DAC2", "mode")
    var pending by remember { mutableStateOf<String?>(null) }
    DspCard("Topologi output (DAC)", "BTL = 2 kanal stereo. PBTL = satu speaker dengan kedua kanal dijembatani paralel.") {
        ChoiceChips(listOf("BTL" to "btl", "PBTL" to "pbtl"), mode) { pick ->
            if (pick == mode) return@ChoiceChips
            if (pick == "pbtl") { pending = pick } else { spp.setDac(pick) }
        }
        if (mode2 != null) {
            Text("Chip 2: $mode2", style = MaterialTheme.typography.bodySmall)
        }
        if (s.mode == 1) {
            Text("Mode 1 (2-chip): perubahan hanya berlaku untuk chip 2 (sub).", style = MaterialTheme.typography.bodySmall)
        }
    }
    val p = pending
    if (p != null) {
        ConfirmDialog(
            title = "Ganti ke PBTL?",
            text = "Pastikan wiring speaker sudah PBTL (output kiri & kanan dijembatani ke satu speaker). Wiring salah dapat merusak amp atau speaker.",
            confirmLabel = "Ganti ke PBTL",
            onConfirm = { spp.setDac(p) },
            onDismiss = { pending = null },
        )
    }
}

// ------------------------------------------------------------------ daya

@Composable
private fun PowerCard(s: AmpState, spp: SppClient) {
    DspCard("Gain analog & daya") {
        Labeled("Gain analog (0 = 0 dB, 31 = −15,5 dB)", "${String.format(Locale.US, "%.1f", -0.5f * s.gain)} dB") {
            SendSliderInt(s.gain, { spp.setGain(it) }, 0..31, steps = 30, live = false, modifier = Modifier.fillMaxWidth())
        }
        if (s.hasStandby) {
            Labeled("Auto-standby", if (s.standbyMin == 0) "nonaktif" else "${s.standbyMin} menit") {
                SendSliderInt(s.standbyMin, { spp.setStandby(it) }, 0..240, live = false, modifier = Modifier.fillMaxWidth())
            }
        }
        if (s.hasSubInv && s.hasSubPath) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Polaritas sub dibalik (180°)", modifier = Modifier.weight(1f))
                Switch(checked = s.subInv, onCheckedChange = { spp.setSubInv(it) })
            }
        }
    }
}

// ------------------------------------------------------------------ simpan / reboot

@Composable
private fun PersistCard(s: AmpState, spp: SppClient) {
    var confirmErase by remember { mutableStateOf(false) }
    var confirmReboot by remember { mutableStateOf(false) }
    DspCard("Penyimpanan perangkat", "Setelan disimpan di flash (NVS) ESP32 dan dipulihkan saat boot.") {
        if (s.hasSave) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { spp.save() }) { Text("Simpan ke perangkat") }
                OutlinedButton(onClick = { confirmErase = true }) { Text("Hapus simpanan") }
            }
        }
        when (s.saved) {
            true -> Text("Setelan tersimpan.", style = MaterialTheme.typography.bodySmall, color = OkGreen)
            false -> Text("Simpanan dihapus (default saat boot berikutnya).", style = MaterialTheme.typography.bodySmall)
            null -> {}
        }
        OutlinedButton(onClick = { confirmReboot = true }) { Text("Reboot ESP32") }
    }
    if (confirmErase) {
        ConfirmDialog(
            "Hapus simpanan?", "Setelan di flash dihapus; perangkat memakai default pada boot berikutnya.",
            "Hapus", { spp.erase() }, { confirmErase = false },
        )
    }
    if (confirmReboot) {
        ConfirmDialog(
            "Reboot ESP32?", "Koneksi Bluetooth akan terputus dan audio berhenti sejenak.",
            "Reboot", { spp.reboot() }, { confirmReboot = false },
        )
    }
}
