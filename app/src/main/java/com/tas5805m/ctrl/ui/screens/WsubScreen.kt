package com.tas5805m.ctrl.ui.screens

import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassBadge
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.ui.components.PollWhileStarted
import com.tas5805m.ctrl.ui.theme.BadRed
import com.tas5805m.ctrl.ui.theme.OkGreen
import com.tas5805m.ctrl.ui.theme.WarnAmber
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.Diagnosis
import com.tas5805m.ctrl.data.FillSample
import com.tas5805m.ctrl.data.PeerDetail
import com.tas5805m.ctrl.data.VoiceParams
import com.tas5805m.ctrl.data.faultNames
import com.tas5805m.ctrl.ui.components.SendSlider
import com.tas5805m.ctrl.ui.components.SendSliderInt
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun WsubScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp

    // Poll hanya saat tab ini tampil & tersambung. `status` berat (I2C baca fault + ±1,5 KB teks) → tiap 1,5 dtk;
    // `wlvl` murah (firmware: aman ±6×/dtk) → level sub per slave ±3×/dtk di sela-selanya.
    PollWhileStarted {
        var n = 0
        while (true) {
            delay(250)
            val st = vm.state.value
            if (!st.connected) continue
            if (n % 6 == 0) spp.requestStatus()
            else if (st.hasWlvl && st.peerDetails.isNotEmpty()) spp.requestWlvl()
            n++
        }
    }

    if (!state.connected) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Hubungkan perangkat dulu")
        }
        return
    }

    // Isolate chart data so peer list doesn't recompose on every fill tick unnecessarily
    // (still same state object, but we pass stable primitives to heavy charts)
    val fillMs = state.wsubFillMs
    val targetMs = state.wsubLat
    val fillHistory = state.fillHistory
    val undHistory = state.undHistory
    val gapHistory = state.gapHistory
    val und = state.wsubUnd
    val gap = state.wsubGap

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            HeaderCard(state)
        }

        item(key = "fill_chart") {
            FillChartCard(
                fillMs = fillMs,
                targetMs = targetMs,
                samples = fillHistory
            )
        }

        item(key = "und_gap") {
            UndGapCard(und = und, gap = gap, undHistory = undHistory, gapHistory = gapHistory)
        }

        item(key = "actions") {
            ActionRow(
                scanning = state.wsubScan == 1,
                onScan = { spp.wsubScan() },
                onReset = { spp.wsubDiagReset() },
                onUnpairAll = { spp.wsubUnpairAll() }
            )
        }

        if (state.wsubFound != "none" && state.wsubFound.isNotEmpty()) {
            item(key = "found") {
                FoundCard(
                    found = state.wsubFound,
                    paired = state.peerDetails.map { it.mac } + state.wsubPeers,
                    onPair = { spp.wsubPair(it) }
                )
            }
        }

        item(key = "peer_title") {
            Text(
                "Peer terpasang (${state.peerDetails.size}/${state.wsubMax})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (state.peerDetails.isEmpty()) {
            item(key = "no_peers") {
                GlassCard(contentPadding = 0.dp) {
                    ListItem(
                    colors = com.tas5805m.ctrl.ui.components.glassListItemColors(),
                        headlineContent = { Text("Belum ada peer") },
                        supportingContent = { Text("Tekan Scan Slave lalu Pair MAC yang muncul") }
                    )
                }
            }
        }

        itemsIndexed(
            items = state.peerDetails,
            key = { _, p -> p.mac }
        ) { idx, peer ->
            PeerCardDetailed(
                peer = peer,
                index = idx,
                globalFill = fillMs,
                targetMs = targetMs,
                onUnpair = { spp.wsubUnpair(peer.mac) }
            )
        }

        item(key = "trim") {
            TrimCard(current = state.wsubTrim, onTrim = { spp.wsubTrim(it) })
        }

        if (state.hasWsubTune) {
            item(key = "extra") {
                ExtraCard(
                    extra = state.wsubExtra,
                    latBase = state.tagInt("WSUBT", "latb"),
                    latCmd = state.tagInt("WSUBT", "latc"),
                    onExtra = { spp.wsubExtra(it) },
                )
            }
        }

        if (state.hasVoice) {
            item(key = "voice") {
                VoiceCard(v = state.voice, warn = Diagnosis.voiceWarning(state), free = Diagnosis.voiceFreePeak(state), onChange = { spp.setVoice(it) })
            }
        }

        item(key = "radio") {
            RadioCard(state)
        }

        item(key = "notes") {
            Text(
                "Catatan multi-sub:\n" +
                "• Max 4 peer (firmware ≥ 2.9.3)\n" +
                "• Grafik hanya update jika nilai berubah (hemat CPU)\n" +
                "• Poll status 1,5 dtk · level sub ±3×/dtk · history max ${AmpState.HISTORY_MAX} titik\n" +
                "• Voicing sub (di DSP chip): slave ≥ 2.9.19; peaking bebas (frekuensi/Q/gain 0,5 dB) di slave ≥ 2.9.20. Disimpan otomatis di master\n" +
                "• Unpair di slave: tahan BOOT 3 detik",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun HeaderCard(state: AmpState) {
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SpeakerGroup, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Wireless Sub — Multi-peer", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text("Mode: ${state.modeName()}  ·  Max peers: ${state.wsubMax}")
            Text("Latency target: ${state.wsubLat} ms  ·  Trim: ${state.wsubTrim} ms")
            if (state.fwVersion.isNotEmpty()) {
                Text(
                    "Firmware master: ${state.fwVersion}${if (state.fwRole.isNotEmpty()) " (${state.fwRole})" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            if (state.wsubFb == 1) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = WarnAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Fallback stereo aktif — sub hilang ≥6 dtk",
                        color = WarnAmber,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun FillChartCard(fillMs: Int, targetMs: Int, samples: List<FillSample>) {
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buffer Fill (real-time)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                FillRing(fillMs = fillMs, targetMs = targetMs)
            }
            Text(
                "Target ≈ $targetMs ms  ·  ${samples.size} titik",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            FillGauge(fillMs = fillMs, targetMs = targetMs, modifier = Modifier.fillMaxWidth().height(28.dp))
            Spacer(Modifier.height(12.dp))
            if (samples.isNotEmpty()) {
                Text("Riwayat fill", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                // key by last sample to skip redraw when identical
                key(samples.lastOrNull()?.fillMs, samples.size, targetMs) {
                    FillSparkline(
                        samples = samples,
                        targetMs = targetMs,
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                }
            } else {
                Text(
                    "Menunggu data WSUB…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/** Gauge lingkaran ala preview: busur terisi sesuai fill/target, angka ms di tengah. */
@Composable
private fun FillRing(fillMs: Int, targetMs: Int, modifier: Modifier = Modifier) {
    val progress = if (targetMs > 0) (fillMs.toFloat() / (targetMs * 1.5f)).coerceIn(0f, 1f) else 0f
    val color = when {
        fillMs <= 0 -> BadRed
        fillMs < targetMs * 0.5f -> WarnAmber
        fillMs > targetMs * 1.3f -> Color(0xFF4FC3F7)
        else -> OkGreen
    }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            buildAnnotatedString {
                append("$fillMs")
                withStyle(SpanStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("ms") }
            },
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = color,
        )
    }
}

@Composable
private fun UndGapCard(und: Int, gap: Int, undHistory: List<Int>, gapHistory: List<Int>) {
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            Text("Underrun & Gap", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Underrun: $und", style = MaterialTheme.typography.labelMedium)
                    key(undHistory.lastOrNull(), undHistory.size) {
                        MiniBarHistory(
                            values = undHistory,
                            color = BadRed,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Gap: $gap", style = MaterialTheme.typography.labelMedium)
                    key(gapHistory.lastOrNull(), gapHistory.size) {
                        MiniBarHistory(
                            values = gapHistory,
                            color = WarnAmber,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    scanning: Boolean,
    onScan: () -> Unit,
    onReset: () -> Unit,
    onUnpairAll: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onScan) {
            Icon(Icons.Default.Radar, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (scanning) "Scanning…" else "Scan Slave")
        }
        OutlinedButton(onClick = onReset) {
            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Reset Diag")
        }
        OutlinedButton(onClick = onUnpairAll) {
            Icon(Icons.Default.LinkOff, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Unpair All")
        }
    }
}

@Composable
private fun FoundCard(found: String, paired: List<String>, onPair: (String) -> Unit) {
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            Text("Ditemukan saat scan", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            found.split(",").filter { it.isNotBlank() && it != "none" }.forEach { mac ->
                val already = paired.any { it.equals(mac, true) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(mac, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    if (already) {
                        Text("Sudah paired", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    } else {
                        Button(
                            onClick = { onPair(mac) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AddLink, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pair")
                        }
                    }
                }
            }
        }
    }
}

/** Penyelarasan latensi (firmware ≥ 2.9.21): seberapa jauh sub boleh menunda diri di atas dasar `70 ms + trim`. */
@Composable
private fun ExtraCard(extra: Int?, latBase: Int?, latCmd: Int?, onExtra: (Int) -> Unit) {
    val cur = extra ?: 100
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardTitle("Penyelarasan latensi", Modifier.padding(bottom = 0.dp), trailing = { GlassBadge("maks +$cur ms") })
            Text(
                "Batas seberapa jauh sub boleh menunda diri di atas dasar (70 ms + trim) saat radio tersendat. " +
                    "Kecil = sub tidak makin telat dari satelit, tapi underrun lebih sering. Besar = lebih tahan underrun, sub bisa lebih telat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (latBase != null && latCmd != null) {
                Text(
                    "Dasar $latBase ms → latensi yang diperintahkan ke sub $latCmd ms",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Dikirim saat dilepas; kelipatan 5 ms (rentang firmware 0…200).
            SendSliderInt(
                value = cur,
                onSend = { onExtra((it / 5) * 5) },
                valueRange = 0..200,
                steps = 39,
                live = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrimCard(current: Int, onTrim: (Int) -> Unit) {
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Trim Latency (ms relatif 70 ms)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${if (current > 0) "+" else ""}$current ms", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            // Rentang firmware: −60 … +100 ms (+ = sub lebih lambat). Dikirim saat dilepas.
            SendSliderInt(
                value = current,
                onSend = onTrim,
                valueRange = -60..100,
                live = false,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(-5, -1, 1, 5).forEach { d ->
                    OutlinedButton(
                        onClick = { onTrim((current + d).coerceIn(-60, 100)) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) { Text(if (d > 0) "+$d" else "$d") }
                }
                OutlinedButton(
                    onClick = { onTrim(0) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) { Text("0") }
            }
        }
    }
}

private fun fmt10(v: Int): String {
    val a = abs(v)
    return (if (v < 0) "-" else "") + "${a / 10}.${a % 10}"
}

private fun voiceBandHz(pkHz: Int): Int = if (pkHz < 100) 80 else 125

/**
 * Voicing sub di DSP chip slave. Perintah: `wsub_voice`.
 *  - slave ≥ 2.9.20 ([free] = true): HPF subsonic + peaking bebas (frekuensi 30–150 Hz, Q 0,5–3,0, gain 0,5 dB).
 *  - slave 2.9.19 ([free] = false): HPF subsonic + peaking di band EQ 80 / 125 Hz (Q tetap, gain 1 dB).
 * "fb" = frekuensi resonansi driver sub; slider fb mengisi HPF ≈ 0,7×fb dan peaking di fb sekaligus.
 */
@Composable
private fun VoiceCard(v: VoiceParams, warn: String?, free: Boolean, onChange: (VoiceParams) -> Unit) {
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voicing Sub (bass)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = v.on, onCheckedChange = { onChange(v.copy(on = it)) })
            }
            Text(
                if (free) "Subsonic HPF melindungi driver, peaking menebalkan bass di sekitar resonansi (fb). Diproses di DSP chip slave (≥ 2.9.20): frekuensi, Q, dan gain (0,5 dB) bebas. Limiter dan harmonik tidak tersedia — batasi gain dan volume."
                else "Subsonic HPF melindungi driver, peaking menebalkan bass di sekitar resonansi (fb). Diproses di DSP chip slave (butuh slave ≥ 2.9.19): peaking hanya di band 80 Hz atau 125 Hz (Q tetap), gain per 1 dB. Flash slave 2.9.20 untuk peaking bebas. Limiter dan harmonik tidak tersedia — batasi gain dan volume.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (warn != null) {
                Spacer(Modifier.height(6.dp))
                Text(warn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))

            Text(
                // Gain peaking hanya diisi +4 dB bila sebelumnya ≤ 0 (lihat onSend di bawah); selain itu nilai lama dipertahankan.
                if (free) "Auto dari fb driver: ${v.pkHz} Hz  →  HPF ≈ 0,7×fb, peaking di fb (gain +4 dB bila sebelumnya ≤ 0)"
                else "Auto dari fb driver: ${v.pkHz} Hz  →  HPF ≈ 0,7×fb, peaking band ${voiceBandHz(v.pkHz)} Hz (gain +4 dB bila sebelumnya ≤ 0)",
                style = MaterialTheme.typography.labelMedium
            )
            SendSliderInt(
                value = v.pkHz,
                onSend = { fb ->
                    onChange(
                        v.copy(
                            on = true,
                            pkHz = fb.coerceIn(30, 150),
                            hpf = (fb * 0.7f).roundToInt().coerceIn(20, 120),
                            pkDb10 = if (v.pkDb10 <= 0) 40 else v.pkDb10,
                        )
                    )
                },
                valueRange = 30..150,
                live = false,
                modifier = Modifier.fillMaxWidth()
            )

            VoiceSlider("HPF subsonic", "${v.hpf} Hz", v.hpf, 20..120) { onChange(v.copy(hpf = it)) }
            if (free) {
                VoiceSlider("Peak frekuensi", "${v.pkHz} Hz", v.pkHz, 30..150) { onChange(v.copy(pkHz = it)) }
                // 0,5 dB per langkah: nilai slider = pkDb10 / 5  (−12..18 → −6,0..+9,0 dB)
                val half = (v.pkDb10 / 5).coerceIn(-12, 18)
                val gtxt = String.format(java.util.Locale.US, "%+.1f dB", half * 0.5f)
                VoiceSlider("Peak gain", gtxt, half, -12..18) { onChange(v.copy(pkDb10 = it * 5)) }
                val q = v.pkQ10.coerceIn(5, 30)
                VoiceSlider("Peak Q", String.format(java.util.Locale.US, "%.1f", q / 10f), q, 5..30) { onChange(v.copy(pkQ10 = it)) }
            } else {
                VoiceSlider("Peak frekuensi", "${v.pkHz} Hz → band ${voiceBandHz(v.pkHz)} Hz", v.pkHz, 30..150) { onChange(v.copy(pkHz = it)) }
                val gainDb = (if (v.pkDb10 >= 0) (v.pkDb10 + 5) / 10 else (v.pkDb10 - 5) / 10).coerceIn(-6, 9)
                VoiceSlider("Peak gain", "${if (gainDb > 0) "+" else ""}$gainDb dB", gainDb, -6..9) { onChange(v.copy(pkDb10 = it * 10)) }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { onChange(VoiceParams(on = false)) }) { Text("Reset / OFF") }
        }
    }
}

@Composable
private fun VoiceSlider(label: String, valueText: String, value: Int, range: IntRange, onSend: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Text(valueText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
    // live: bass bisa disetel sambil didengar; throttle 300 ms karena tiap perintah memicu status penuh
    SendSliderInt(
        value = value,
        onSend = onSend,
        valueRange = range,
        throttleMs = 300L,
        modifier = Modifier.fillMaxWidth().height(32.dp)
    )
}

@Composable
private fun RadioCard(state: AmpState) {
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            Text("Radio Counters (per slave)", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (state.peerDetails.isEmpty()) {
                Text("Belum ada peer", style = MaterialTheme.typography.bodySmall)
            }
            state.peerDetails.forEachIndexed { i, p ->
                Text(
                    "#${i + 1} RX a/c: ${p.rxa}/${p.rxc}  ·  Rej: ${p.rej}  ·  STX: ${p.stx}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "   Fill: ${p.fillMs} ms  ·  Und: ${p.und}  ·  Gap: ${p.gap}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "rxa/rxc = -1 → slave lama (< 2.9.x) atau link putus",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

// -------------------- Charts (optimized) --------------------

@Composable
fun FillGauge(fillMs: Int, targetMs: Int, modifier: Modifier = Modifier) {
    val progress = if (targetMs > 0) (fillMs.toFloat() / (targetMs * 1.5f)).coerceIn(0f, 1f) else 0f
    val color = when {
        fillMs <= 0 -> BadRed
        fillMs < targetMs * 0.5f -> WarnAmber
        fillMs > targetMs * 1.3f -> Color(0xFF4FC3F7)
        else -> OkGreen
    }
    // Cache target fraction
    val targetFrac = remember(targetMs) {
        if (targetMs > 0) (targetMs.toFloat() / (targetMs * 1.5f)).coerceIn(0f, 1f) else 0.66f
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * progress
            if (w > 0f) {
                drawRect(color = color, size = Size(w, size.height))
            }
            val tx = size.width * targetFrac
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(tx, 0f),
                end = Offset(tx, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (fillMs <= 0) "EMPTY" else "$fillMs ms",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FillSparkline(
    samples: List<FillSample>,
    targetMs: Int,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    // Precompute maxY outside draw when samples change
    val maxY = remember(samples, targetMs) {
        val m = samples.maxOfOrNull { it.fillMs }?.toFloat() ?: 0f
        max(m, targetMs * 1.2f).coerceAtLeast(1f)
    }
    // Extract primitive array for faster draw loop (avoid object field access in tight loop)
    val fills = remember(samples) { FloatArray(samples.size) { samples[it].fillMs.toFloat() } }

    Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E))) {
        val n = fills.size
        if (n < 2) return@Canvas

        val stepX = size.width / (n - 1)
        val h = size.height
        val stroke = 2.5.dp.toPx()
        val targetY = h - (targetMs / maxY) * h

        // target line
        drawLine(
            Color.White.copy(alpha = 0.35f),
            Offset(0f, targetY),
            Offset(size.width, targetY),
            strokeWidth = 1.dp.toPx()
        )

        // Build path once per frame
        val line = Path()
        val area = Path()
        for (i in 0 until n) {
            val x = i * stepX
            val y = h - (fills[i] / maxY) * h
            if (i == 0) {
                line.moveTo(x, y)
                area.moveTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(size.width, h)
        area.lineTo(0f, h)
        area.close()

        drawPath(area, primary.copy(alpha = 0.2f))
        drawPath(line, primary, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // last point
        val lx = (n - 1) * stepX
        val ly = h - (fills[n - 1] / maxY) * h
        drawCircle(primary, radius = 4.dp.toPx(), center = Offset(lx, ly))
    }
}

@Composable
fun MiniBarHistory(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    val arr = remember(values) { IntArray(values.size) { values[it] } }
    val maxV = remember(values) { (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat() }

    Canvas(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1E1E))) {
        val n = arr.size
        if (n == 0) return@Canvas
        val barW = size.width / n
        val gap = barW * 0.3f
        val body = barW - gap
        for (i in 0 until n) {
            val h = (arr[i] / maxV) * size.height
            if (h > 0.5f) {
                drawRect(
                    color = color.copy(alpha = 0.85f),
                    topLeft = Offset(i * barW, size.height - h),
                    size = Size(body, h)
                )
            }
        }
    }
}

@Composable
private fun PeerCardDetailed(
    peer: PeerDetail,
    index: Int,
    globalFill: Int,
    targetMs: Int,
    onUnpair: () -> Unit
) {
    // Layout mirip HTML peer card: judul + MAC + sinyal, sparkline buffer + ring latency
    val linkColor = if (peer.link) OkGreen else BadRed
    val fill = if (peer.fillMs > 0) peer.fillMs else globalFill
    val title = when {
        peer.slot >= 0 -> "Sub ${peer.slot + 1}"
        else -> "Sub ${index + 1}"
    }
    // RSSI → 0..4 bar (mirip HTML .signal)
    val bars = when {
        !peer.link -> 0
        peer.rssi == 0 -> 3
        peer.rssi >= -50 -> 4
        peer.rssi >= -60 -> 3
        peer.rssi >= -70 -> 2
        else -> 1
    }
    val ringColor = when {
        fill <= 0 -> BadRed
        fill < targetMs * 0.5f -> WarnAmber
        fill > targetMs * 1.3f -> Color(0xFFF87171)
        else -> AccentAmberLike(fill, targetMs)
    }
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        peer.mac.uppercase(),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                SignalBars(bars = bars, good = peer.link)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Buffer sparkline (HTML BUFFER · LAST 30S)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.30f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        "BUFFER",
                        fontSize = 9.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.4.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    // Angka asli dari WSUBS (fill/und/gap per slave). Riwayat per-peer tidak dikirim firmware,
                    // jadi tidak ada grafik di sini; riwayat agregat ada di kartu "Riwayat fill".
                    Text(
                        "$fill / ${targetMs.coerceAtLeast(1)} ms",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF7DD3FC),
                    )
                    Text(
                        "und ${peer.und} · gap ${peer.gap}",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                    )
                }
                // Latency ring
                PeerLatencyRing(fillMs = fill, targetMs = targetMs, color = ringColor)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Und", "${peer.und}")
                StatChip("Gap", "${peer.gap}")
                if (peer.hiz) StatChip("Hi-Z", "ON")
                if (peer.rxa >= 0) StatChip("RX", "${peer.rxa}/${peer.rxc}")
            }
            peer.lvlDeciDb?.let { lv ->
                Spacer(Modifier.height(6.dp))
                LevelBar("SUB", lv)
            }
            if (peer.flt > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "FAULT slave: ${faultNames(peer.flt).joinToString(", ")} (×${peer.fcnt})",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onUnpair,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Unpair")
            }
        }
    }
}

@Composable
private fun SignalBars(bars: Int, good: Boolean) {
    val on = if (good) OkGreen else Color(0xFF475569)
    val off = Color.White.copy(alpha = 0.15f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp),
    ) {
        listOf(0.25f, 0.50f, 0.75f, 1f).forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < bars) on else off)
            )
        }
    }
}

@Composable
private fun PeerLatencyRing(fillMs: Int, targetMs: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = if (targetMs > 0) (fillMs.toFloat() / (targetMs * 1.5f)).coerceIn(0f, 1f) else 0f
    val track = Color.White.copy(alpha = 0.08f)
    Box(modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                color, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$fillMs",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = color,
            )
            Text(
                "ms",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color(0xFF94A3B8),
            )
        }
    }
}

private fun AccentAmberLike(fill: Int, target: Int): Color {
    val r = if (target > 0) fill.toFloat() / target else 1f
    return when {
        r > 1.2f -> Color(0xFFF87171)
        r > 1.0f -> Color(0xFFFBBF24)
        else -> Color(0xFFFBBF24)
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            "$label $value",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
