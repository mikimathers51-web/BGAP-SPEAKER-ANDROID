package com.tas5805m.ctrl.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.Preset
import com.tas5805m.ctrl.data.PresetTools
import kotlinx.coroutines.launch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun describe(p: Preset): String = when (p.kind) {
    "eq", "voice", "scene" -> p.note.ifEmpty { if (p.kind == "scene") "EQ + voicing sub" else "" }
    else -> buildString {
        append("Mode ${p.mode} · Xover ${p.xover} Hz · Master ${p.master}")
        if (p.voice != null) append(" · voicing")
        if (p.clip != null) append(" · clipper")
        if (p.trim != null) append(" · trim ${p.trim}")
    }
}

private fun kindLabel(p: Preset): String = when (p.kind) {
    "eq" -> "EQ"
    "voice" -> "Voicing sub"
    "scene" -> "Scene"
    else -> "Lengkap"
}

private fun canApply(p: Preset, s: AmpState): Boolean =
    s.connected && (p.kind != "voice" || (s.hasWsub && s.hasVoice))

/** Alasan tombol Terapkan nonaktif (null bila aktif). */
private fun disabledReason(p: Preset, s: AmpState): String? = when {
    !s.connected -> "Belum terhubung."
    p.kind == "voice" && !(s.hasWsub && s.hasVoice) -> "Butuh sub nirkabel dengan slave ≥ 2.9.19."
    else -> null
}

private fun applyMessage(p: Preset, mode: Int, err: String?): String = buildString {
    append("“${p.name}” diterapkan.")
    if (err != null) append(" Peringatan: ").append(err).append('.')
    PresetTools.warning(p, mode)?.let { append(' ').append(it) }
}

@Composable
fun PresetScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    // SAF: baca/tulis file di disk HP tanpa permission storage
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                    }
                }.getOrNull()
            }
            notice = when {
                text.isNullOrBlank() -> "Gagal membaca file (kosong atau tidak bisa dibuka)."
                else -> {
                    val n = vm.importPresets(text)
                    if (n > 0) "$n preset diimpor dari file."
                    else "JSON tidak valid, kosong, atau semua duplikat."
                }
            }
        }
    }
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = vm.exportPresets()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    true
                }.getOrDefault(false)
            }
            notice = if (ok) "Preset diekspor ke file." else "Gagal menulis file."
        }
    }
    fun defaultExportName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "bgap-presets-$ts.json"
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "save") {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama preset") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        onClick = { if (state.connected) { vm.savePreset(name); name = ""; notice = "Preset disimpan." } },
                        enabled = state.connected,
                    ) { Text("Simpan") }
                }
                OutlinedButton(
                    onClick = { if (state.connected) { vm.saveScene(name); name = ""; notice = "Scene disimpan (EQ + voicing sub)." } },
                    enabled = state.connected,
                ) { Text("Simpan sebagai Scene (EQ + voicing)") }
                Text(
                    "Preset lengkap menyimpan mode, xover, volume, gain, mixer, EQ 15 band, serta (bila ada) clipper, polaritas sub, standby, trim, voicing sub, dan EQ center/sub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                if (notice.isNotEmpty()) Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        item(key = "h_builtin") {
            Text("Bawaan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Preset EQ hanya mengubah EQ (band 20/32/50 Hz dilewati saat crossover aktif); preset voicing hanya mengubah voicing sub; Scene = EQ + voicing sub tanpa mengubah mode, crossover, atau volume.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        items(vm.builtinPresets, key = { it.id }) { p ->
            PresetRow(p, canApply(p, state), disabledReason(p, state), PresetTools.warning(p, state.mode),
                onApply = { notice = "Menerapkan “${p.name}”…"; vm.applyPreset(p) { err -> notice = applyMessage(p, state.mode, err) } },
                onDelete = null)
        }

        item(key = "h_user") {
            // Judul dan aksi dipisah baris agar tidak terhimpit / pecah vertikal di layar sempit
            Column(Modifier.padding(top = 10.dp)) {
                Text(
                    "Preset saya",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                clipboard.setText(AnnotatedString(vm.exportPresets()))
                                notice = "Preset disalin ke clipboard (JSON)."
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("Salin", maxLines = 1, softWrap = false) }
                    TextButton(
                        onClick = { exportFileLauncher.launch(defaultExportName()) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("Ekspor", maxLines = 1, softWrap = false) }
                    TextButton(
                        onClick = {
                            importFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("Impor", maxLines = 1, softWrap = false) }
                    TextButton(
                        onClick = { importText = clipboard.getText()?.text ?: ""; showImport = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("Tempel", maxLines = 1, softWrap = false) }
                }
            }
        }
        if (presets.isEmpty()) {
            item(key = "empty") { Text("Belum ada preset. Atur EQ/volume lalu tekan Simpan.", style = MaterialTheme.typography.bodySmall) }
        }
        items(presets, key = { it.id }) { p ->
            PresetRow(p, canApply(p, state), disabledReason(p, state), PresetTools.warning(p, state.mode),
                onApply = { notice = "Menerapkan “${p.name}”…"; vm.applyPreset(p) { err -> notice = applyMessage(p, state.mode, err) } },
                onDelete = { vm.deletePreset(p.id) })
        }
        item(key = "end") { Spacer(Modifier.height(24.dp)) }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Impor preset (JSON)") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("Tempel JSON — atau pakai Impor file dari disk") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val n = vm.importPresets(importText)
                        notice = if (n > 0) "$n preset diimpor." else "JSON tidak valid atau kosong."
                    }
                    showImport = false
                }) { Text("Impor") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun PresetRow(p: Preset, enabled: Boolean, reason: String?, warning: String?, onApply: () -> Unit, onDelete: (() -> Unit)?) {
    com.tas5805m.ctrl.ui.components.GlassCard(contentPadding = 0.dp) {
        ListItem(
            colors = com.tas5805m.ctrl.ui.components.glassListItemColors(),
            headlineContent = { Text(p.name) },
            supportingContent = {
                Column {
                    Text(kindLabel(p) + " · " + describe(p))
                    if (reason != null) Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    if (warning != null) Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onApply, enabled = enabled) {
                        Icon(Icons.Default.PlayArrow, "Terapkan", tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, "Hapus", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
        )
    }
}
