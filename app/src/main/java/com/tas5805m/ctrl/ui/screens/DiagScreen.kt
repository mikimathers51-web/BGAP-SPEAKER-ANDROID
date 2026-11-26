package com.tas5805m.ctrl.ui.screens

import com.tas5805m.ctrl.ui.components.PollWhileStarted
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.tas5805m.ctrl.data.DiagHistory
import com.tas5805m.ctrl.data.DiagReport
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassBadge
import com.tas5805m.ctrl.ui.components.MetricCell
import androidx.compose.foundation.layout.RowScope
import com.tas5805m.ctrl.ui.theme.AccentCyan
import com.tas5805m.ctrl.ui.theme.BadRed
import com.tas5805m.ctrl.ui.theme.OkGreen
import com.tas5805m.ctrl.ui.theme.WarnAmber
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.Diagnosis
import com.tas5805m.ctrl.data.FAULT_KEYS
import com.tas5805m.ctrl.data.Finding
import com.tas5805m.ctrl.data.Level
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.aspectRatio

private val ColErr = BadRed
private val ColWarn = WarnAmber
private val ColInfo = Color(0xFF4FC3F7)
private val ColOk = OkGreen

private fun levelColor(l: Level): Color = when (l) {
    Level.ERR -> ColErr
    Level.WARN -> ColWarn
    Level.INFO -> ColInfo
    Level.OK -> ColOk
}

private fun levelMark(l: Level): String = when (l) {
    Level.ERR -> "✖"
    Level.WARN -> "▲"
    Level.INFO -> "ℹ"
    Level.OK -> "✔"
}

/**
 * Diagnosa: ringkasan masalah otomatis (aturan dari README firmware), status fault chip,
 * info chip, statistik link nirkabel, terminal perintah mentah, dan laporan yang bisa dibagikan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val spp = vm.spp
    val ctx = LocalContext.current
    val history by vm.diagHistory.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<DiagReport?>(null) }
    var pendingText by remember { mutableStateOf<String?>(null) }
    // Laporan sesi setelah Stop — ditampilkan dulu; user tekan Simpan bila mau.
    var sessionReport by remember { mutableStateOf<Pair<String, String>?>(null) } // title to text

    // Simpan ke file lewat dialog pilih lokasi Android (tanpa izin storage tambahan).
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val t = pendingText
        pendingText = null
        if (uri != null && t != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(t.toByteArray(Charsets.UTF_8)) }
                    ?: error("stream null")
            }.isSuccess
            notice = if (ok) "Laporan disimpan ke file." else "Gagal menyimpan file."
        }
    }

    fun saveFile(text: String, ms: Long) {
        pendingText = text
        saver.launch(DiagHistory.fileName(ms))
    }

    fun shareText(text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Laporan diagnosa TAS5805M")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, "Bagikan laporan"))
    }

    viewing?.let { r ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(r.title) },
            text = {
                Column {
                    Text(DiagHistory.stamp(r.createdMs), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        r.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Tutup") } },
            dismissButton = { TextButton(onClick = { shareText(r.text) }) { Text("Bagikan") } },
        )
    }

    // Setelah Stop: tampilkan laporan sesi dulu; user tekan Simpan / Bagikan / Tutup.
    sessionReport?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { sessionReport = null },
            title = { Text(title) },
            text = {
                Column {
                    Text(
                        "Laporan sesi ukur. Belum disimpan otomatis — tekan Simpan ke riwayat bila mau.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveDiagReport(title, text)
                    notice = "Laporan sesi disimpan ke riwayat."
                    sessionReport = null
                }) { Text("Simpan ke riwayat") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { shareText(text) }) { Text("Bagikan") }
                    TextButton(onClick = {
                        saveFile(text, System.currentTimeMillis())
                        sessionReport = null
                    }) { Text("Simpan file") }
                    TextButton(onClick = { sessionReport = null }) { Text("Tutup") }
                }
            },
        )
    }

    val histCard: @Composable () -> Unit = {
        HistoryCard(
            history = history,
            onView = { viewing = it },
            onShare = { shareText(it.text) },
            onSave = { saveFile(it.text, it.createdMs) },
            onDelete = { vm.deleteDiagReport(it.id) },
            onClear = { vm.clearDiagHistory() },
        )
    }

    if (!state.connected) {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "offline") {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BluetoothDisabled, null, tint = Color.Gray)
                        Spacer(Modifier.height(12.dp))
                        Text("Hubungkan perangkat di tab Koneksi", color = Color.Gray)
                        Text("Riwayat laporan tetap bisa dibuka di bawah.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
            if (notice.isNotEmpty()) item(key = "notice") { Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            item(key = "history") { histCard() }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
        return
    }

    // Poll status saat layar tampil. Saat sesi ukur aktif & tidak pause: tiap 2 dtk.
    // Saat pause/stop: tetap poll pelan agar fault chip masih terlihat, tapi tidak membebani.
    val measuring = state.measureRunning && !state.measurePaused
    PollWhileStarted {
        while (true) {
            // Baca state terkini tiap putaran (jangan andalkan capture composition).
            val fast = spp.state.value.measureRunning && !spp.state.value.measurePaused
            delay(if (fast) 2000L else 5000L)
            spp.requestStatus()
        }
    }

    val findings = remember(state) { Diagnosis.diagnose(state) }
    var cmd by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    // Refresh label timer tiap 1 dtk saat measuring.
    LaunchedEffect(state.measureRunning, state.measurePaused) {
        while (state.measureRunning && !state.measurePaused) {
            delay(1000)
            tick++
        }
    }
    @Suppress("UNUSED_VARIABLE")
    val timerTick = tick // paksa recompose untuk label durasi

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Metrics row — sama seperti HTML (Uptime / PVDD / Dropped / Latency)
        item(key = "metrics") {
            val peers = state.peerDetails
            val avgLat = peers.mapNotNull { p -> p.fillMs.takeIf { it > 0 } }.let { if (it.isEmpty()) state.wsubLat else it.average().toInt() }
            val dropped = peers.sumOf { it.und + it.gap }.coerceAtLeast(state.wsubUnd + state.wsubGap)
            val uptimeStr = state.peerDetails.firstOrNull { it.upSec >= 0 }?.let { peer ->
                val sec = peer.upSec
                val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
                "%02d:%02d:%02d".format(h, m, s)
            } ?: "—"
            val pvdd = state.tags["PVDD"]?.get("v") ?: state.tags["INFO"]?.get("pvdd") ?: "—"
            Spacer(Modifier.height(4.dp))
            // Grid 2×2 seperti HTML .metrics
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCell("Uptime", uptimeStr, Modifier.weight(1f), AccentCyan)
                    MetricCell("PVDD", if (pvdd == "—") "—" else "$pvdd V", Modifier.weight(1f), ColOk)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCell("Dropped", "$dropped", Modifier.weight(1f), if (dropped > 0) ColWarn else ColOk)
                    MetricCell("Latency AVG", "${avgLat} ms", Modifier.weight(1f), AccentCyan)
                }
            }
        }

        item(key = "summary") {
            val peers = state.peerDetails
            val faultOn = state.faults.values.any { it == 1 } || (state.tags["FAULT2"]?.values?.any { it == "1" } == true)
            GlassCard {
                CardTitle("Status ringkas")
                Row(Modifier.fillMaxWidth()) {
                    InfoCell("Amp", state.ampstate.uppercase(), if (state.ampstate == "mute") ColWarn else ColOk)
                    InfoCell("Fault", if (faultOn) "Aktif" else "Tidak ada", if (faultOn) ColErr else ColOk)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    InfoCell("Mode", state.modeName(), MaterialTheme.colorScheme.onSurface)
                    InfoCell("Firmware", state.fwVersion.ifBlank { "—" }, MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    InfoCell("SPP", if (state.connected) "Terhubung" else "Putus", if (state.connected) ColOk else ColErr)
                    InfoCell("Peer wireless", if (peers.isEmpty()) "—" else "${peers.count { it.link }}/${peers.size} link", MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        item(key = "actions") {
            GlassCard(contentPadding = 0.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardTitle("Diagnosa")
                    val elapsedSec = state.measureElapsedSec()
                    val statusLabel = when {
                        state.measureRunning && state.measurePaused ->
                            "Dijeda · %02d:%02d (counter sejak Start)".format(elapsedSec / 60, elapsedSec % 60)
                        state.measureRunning ->
                            "Mengukur · %02d:%02d (counter dihitung sejak Start)".format(elapsedSec / 60, elapsedSec % 60)
                        state.baselineAtMs > 0L ->
                            "Berhenti · %02d:%02d — tekan Simpan bila mau simpan laporan sesi".format(elapsedSec / 60, elapsedSec % 60)
                        else -> "Jendela ukur: belum dimulai — tekan Start"
                    }
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            state.measureRunning && !state.measurePaused -> ColOk
                            state.measurePaused -> ColWarn
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                    )
                    // Start / Pause / Stop
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        when {
                            !state.measureRunning -> {
                                Button(onClick = {
                                    vm.startMeasure(resetFirmware = false)
                                    notice = "Ukur dimulai (baseline saja). Counter firmware tidak di-reset."
                                }) { Text("▶ Start") }
                                OutlinedButton(onClick = {
                                    vm.startMeasure(resetFirmware = true)
                                    notice = "Ukur dimulai + reset counter firmware (wsub_diag_reset)."
                                }) { Text("▶ Start + reset") }
                            }
                            state.measurePaused -> {
                                Button(onClick = {
                                    vm.resumeMeasure()
                                    notice = "Ukur dilanjutkan."
                                }) { Text("▶ Lanjut") }
                                OutlinedButton(onClick = {
                                    val sec = state.measureElapsedSec()
                                    val text = Diagnosis.report(state, findings)
                                    vm.stopMeasure()
                                    sessionReport = "Sesi ukur %02d:%02d".format(sec / 60, sec % 60) to text
                                    notice = "Ukur dihentikan. Laporan ditampilkan — tekan Simpan bila mau."
                                }) { Text("⏹ Stop") }
                            }
                            else -> {
                                OutlinedButton(onClick = {
                                    vm.pauseMeasure()
                                    notice = "Ukur dijeda."
                                }) { Text("⏸ Pause") }
                                OutlinedButton(onClick = {
                                    val sec = state.measureElapsedSec()
                                    val text = Diagnosis.report(state, findings)
                                    vm.stopMeasure()
                                    sessionReport = "Sesi ukur %02d:%02d".format(sec / 60, sec % 60) to text
                                    notice = "Ukur dihentikan. Laporan ditampilkan — tekan Simpan bila mau."
                                }) { Text("⏹ Stop") }
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { vm.refreshAll() }) { Text("Refresh semua") }
                        OutlinedButton(onClick = { spp.requestFault() }) { Text("Baca fault") }
                        OutlinedButton(onClick = {
                            vm.saveDiagReport("Laporan manual", Diagnosis.report(state, findings))
                            notice = "Laporan disimpan ke riwayat."
                        }) { Text("Simpan ke riwayat") }
                        OutlinedButton(onClick = { saveFile(Diagnosis.report(state, findings), System.currentTimeMillis()) }) { Text("Simpan file") }
                        OutlinedButton(onClick = { shareText(Diagnosis.report(state, findings)) }) { Text("Bagikan laporan") }
                    }
                    if (notice.isNotEmpty()) Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        items(findings) { f -> FindingCard(f) }

        item(key = "faults") { FaultCard(state) }

        item(key = "history") { histCard() }

        item(key = "chip") { RawCard("Chip & konfigurasi", state, listOf("VER", "INFO", "CLIP", "MOD", "DAC", "DAC2", "EQP", "EQP2")) }

        if (state.hasWsub || state.isWireless) {
            item(key = "wireless") { WirelessRaw(state) }
        }

        item(key = "caps") {
            GlassCard(contentPadding = 0.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardTitle("CAPS dump")
                    Button(onClick = { spp.sendRaw("caps") }) { Text("Refresh CAPS") }
                    val caps = state.lastLines.filter { it.startsWith("CAPS") }
                    if (caps.isEmpty()) {
                        Text("Belum ada data.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        caps.take(20).forEach {
                            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }

        // Log khusus sesi ukur (hanya sejak Start) — lebih bersih untuk diagnosa error.
        item(key = "sessionLog") {
            GlassCard(contentPadding = 0.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardTitle("Log sesi ukur")
                    val sessHint = when {
                        state.measureRunning && state.measurePaused ->
                            "Sesi dijeda — log tetap menerima ERR/fault. ${state.sessionLines.size} baris."
                        state.measureRunning ->
                            "Mengumpulkan log sejak Start (${state.sessionLines.size} baris). Baris poll (LVL/WSUB/…) disembunyikan."
                        state.sessionLines.isNotEmpty() ->
                            "Sesi berhenti — ${state.sessionLines.size} baris tersimpan sampai Start berikutnya."
                        else -> "Tekan Start untuk mulai buffer log sesi (kosong tiap Start)."
                    }
                    Text(
                        sessHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    HorizontalDivider()
                    if (state.sessionLines.isEmpty()) {
                        Text("Belum ada log sesi.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        state.sessionLines.take(80).forEach { line ->
                            Text(
                                line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = when {
                                    line.startsWith("ERR") -> ColErr
                                    line.startsWith("FAULT") -> ColErr
                                    line.startsWith(">") -> ColInfo
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                },
                            )
                        }
                        if (state.sessionLines.size > 80) {
                            Text(
                                "… +${state.sessionLines.size - 80} baris lebih lama (lihat laporan lengkap)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }

        item(key = "terminal") {
            GlassCard(contentPadding = 0.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardTitle("Terminal (log global)")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cmd,
                            onValueChange = { cmd = it },
                            label = { Text("Perintah (mis. status, fault, help)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { spp.sendRaw(cmd); cmd = "" }),
                        )
                        Button(onClick = { spp.sendRaw(cmd); cmd = "" }) { Text("Kirim") }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ping", "status", "fault", "info", "caps", "eqdump", "clipget", "modget", "dacget", "saved")
                            .forEach { c -> AssistChip(onClick = { spp.sendRaw(c) }, label = { Text(c) }) }
                    }
                    Text(
                        "> = perintah terkirim. Baris poll (LVL, WSUB, WSUBS, …) tidak ditampilkan. Untuk diagnosa sesi, pakai kartu Log sesi ukur di atas.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    HorizontalDivider()
                    if (state.lastLines.isEmpty()) {
                        Text("Belum ada log.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        items(state.lastLines.take(60)) { line ->
            Text(
                line,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = when {
                    line.startsWith("ERR") -> ColErr
                    line.startsWith(">") -> ColInfo
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                },
            )
        }

        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FindingCard(f: Finding) {
    val c = levelColor(f.level)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.copy(alpha = 0.30f)),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(levelMark(f.level), color = c, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(f.title, fontWeight = FontWeight.SemiBold, color = c)
                if (f.detail.isNotEmpty()) Text(f.detail, style = MaterialTheme.typography.bodySmall)
                if (f.action.isNotEmpty()) {
                    Text(
                        "→ " + f.action,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FaultCard(s: AmpState) {
    val kv2 = s.tags["FAULT2"]
    // Ringkasan 4 sel utama (HTML fault-grid): OCP, DC, PVDD, OTP
    val ocpOn = (s.faults["oc_l"] ?: 0) == 1 || (s.faults["oc_r"] ?: 0) == 1
    val dcOn  = (s.faults["dc_l"] ?: 0) == 1 || (s.faults["dc_r"] ?: 0) == 1
    val uvOn  = (s.faults["uv"] ?: 0) == 1
    val ovOn  = (s.faults["ov"] ?: 0) == 1
    val otOn  = (s.faults["ot_sd"] ?: 0) == 1 || (s.faults["ot_w"] ?: 0) == 1
    val otWarn = (s.faults["ot_w"] ?: 0) == 1 && (s.faults["ot_sd"] ?: 0) == 0
    val anyFault = ocpOn || dcOn || uvOn || ovOn || otOn
    val pvddDetail = s.tags["PVDD"]?.get("v")?.let { "$it V" }
        ?: s.tags["INFO"]?.get("pvdd")?.let { "$it V" } ?: "—"
    val tempDetail = s.tags["TEMP"]?.get("c")?.let { "$it°C" }
        ?: s.tags["INFO"]?.get("temp")?.let { "$it°C" } ?: "—"

    GlassCard {
        CardTitle(
            "Fault Status",
            trailing = {
                GlassBadge(
                    if (anyFault) "FAULT" else "ALL OK",
                    color = if (anyFault) ColErr else ColOk
                )
            }
        )
        // 2×2 grid ala HTML
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FaultStatusCell("OCP", ocpOn, if (ocpOn) "FAULT" else "OK", "—", Modifier.weight(1f))
                FaultStatusCell("DC", dcOn, if (dcOn) "FAULT" else "OK", "—", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FaultStatusCell(
                    "PVDD",
                    uvOn || ovOn,
                    when {
                        uvOn -> "UV"
                        ovOn -> "OV"
                        else -> "OK"
                    },
                    pvddDetail,
                    Modifier.weight(1f)
                )
                FaultStatusCell(
                    "OTP",
                    otOn,
                    when {
                        (s.faults["ot_sd"] ?: 0) == 1 -> "SHUTDOWN"
                        otWarn -> "WARM"
                        else -> "OK"
                    },
                    tempDetail,
                    Modifier.weight(1f),
                    warnOnly = otWarn
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Detail chip", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        for (chip in 1..2) {
            if (chip == 2 && kv2 == null) continue
            Text("Chip $chip", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FAULT_KEYS.chunked(4).forEach { rowKeys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in rowKeys) {
                        val active = if (chip == 1) (s.faults[k] ?: 0) == 1 else kv2?.get(k) == "1"
                        FaultCell(Diagnosis.faultLabel(k), active, Modifier.weight(1f))
                    }
                    repeat(4 - rowKeys.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        if (s.faultHistory.isEmpty()) {
            Text("Riwayat sesi ini: bersih", style = MaterialTheme.typography.bodySmall, color = ColOk)
        } else {
            Text("Riwayat sesi ini:", style = MaterialTheme.typography.labelMedium)
            s.faultHistory.takeLast(8).reversed().forEach { e ->
                Text(
                    "${fmt.format(Date(e.timeMs))}  chip${e.chip}  ${e.flags.joinToString(",") { Diagnosis.faultLabel(it) }}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** Sel besar 2×2 (HTML .fault-cell) — nama + status + detail. */
@Composable
private fun FaultStatusCell(
    name: String,
    active: Boolean,
    status: String,
    detail: String,
    modifier: Modifier = Modifier,
    warnOnly: Boolean = false,
) {
    // Mirip HTML .fault-cell — ikon di kotak, nama mono, status tebal, detail kecil
    val c = when {
        active && !warnOnly -> ColErr
        warnOnly -> ColWarn
        else -> ColOk
    }
    val shape = RoundedCornerShape(12.dp)
    val iconBg = c.copy(alpha = 0.15f)
    val cellBg = c.copy(alpha = if (warnOnly) 0.06f else if (active) 0.08f else 0.04f)
    val borderC = c.copy(alpha = if (warnOnly) 0.30f else if (active) 0.40f else 0.25f)
    Column(
        modifier
            .clip(shape)
            .background(cellBg, shape)
            .border(1.dp, borderC, shape)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (active || warnOnly) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = c,
                modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name.uppercase(),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = Color(0xFF94A3B8),
        )
        Text(
            status,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = c,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (detail.isNotBlank() && detail != "—") {
            Text(
                detail,
                fontSize = 9.sp,
                color = Color(0xFF64748B),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** Sel fault detail kecil. */
@Composable
private fun FaultCell(name: String, active: Boolean, modifier: Modifier = Modifier) {
    val c = if (active) ColErr else ColOk
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.copy(alpha = 0.08f), shape)
            .border(1.dp, c.copy(alpha = 0.28f), shape)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (active) Icons.Default.Warning else Icons.Default.Check,
            contentDescription = null,
            tint = c,
            modifier = Modifier.size(16.dp),
        )
        Text(name, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(if (active) "FAULT" else "OK", fontSize = 9.sp, color = c, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun RawCard(title: String, s: AmpState, heads: List<String>) {
    val lines = heads.mapNotNull { s.rawByHead[it] }
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (lines.isEmpty()) Text("Belum ada data.", style = MaterialTheme.typography.bodySmall)
            lines.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp) }
        }
    }
}

@Composable
private fun WirelessRaw(s: AmpState) {
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle("Link nirkabel & Bluetooth (mentah)")
            listOf("WSUB", "WSUBT", "WSUBC", "BTD").mapNotNull { s.rawByHead[it] }.forEach {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
            s.slaveDiag.forEach { (mac, kv) ->
                Text(
                    "WSUBD $mac " + kv.filterKeys { it != "mac" }.entries.joinToString(" ") { "${it.key}=${it.value}" },
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                )
            }
            if (s.btEvents.isNotEmpty()) {
                HorizontalDivider()
                Text("Event Bluetooth", style = MaterialTheme.typography.labelMedium)
                s.btEvents.takeLast(8).reversed().forEach {
                    Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryCard(
    history: List<DiagReport>,
    onView: (DiagReport) -> Unit,
    onShare: (DiagReport) -> Unit,
    onSave: (DiagReport) -> Unit,
    onDelete: (DiagReport) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    GlassCard(contentPadding = 0.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Riwayat laporan (${history.size}/${DiagHistory.MAX})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = { if (confirmClear) { onClear(); confirmClear = false } else confirmClear = true }) {
                        Text(if (confirmClear) "Yakin hapus semua?" else "Hapus semua", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (history.isEmpty()) {
                Text(
                    "Belum ada. Tekan Start → Pause/Stop, lalu Simpan dari dialog laporan sesi; atau \"Simpan ke riwayat\" manual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            history.forEach { r ->
                HorizontalDivider()
                Text(r.title, fontWeight = FontWeight.SemiBold)
                Text(
                    DiagHistory.stamp(r.createdMs) + " · " + r.text.count { it == '\n' } + " baris",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onView(r) }) { Text("Buka") }
                    TextButton(onClick = { onShare(r) }) { Text("Bagikan") }
                    TextButton(onClick = { onSave(r) }) { Text("Simpan file") }
                    TextButton(onClick = { onDelete(r) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/** Sel info: label kecil + nilai mono tebal (gaya "Status Ringkas" pada preview). */
@Composable
private fun RowScope.InfoCell(label: String, value: String, color: Color) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
