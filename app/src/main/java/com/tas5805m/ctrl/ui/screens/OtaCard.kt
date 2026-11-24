package com.tas5805m.ctrl.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tas5805m.ctrl.bluetooth.OtaPhase
import com.tas5805m.ctrl.bluetooth.OtaProtocol
import com.tas5805m.ctrl.bluetooth.SppClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class PickedImage(val name: String, val bytes: ByteArray)

/** Baca file .bin (maks [OtaProtocol.MAX_IMAGE] byte). null bila terlalu besar / gagal dibaca. */
private suspend fun readImage(context: android.content.Context, uri: Uri): PickedImage? = withContext(Dispatchers.IO) {
    try {
        var name = "firmware.bin"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > OtaProtocol.MAX_IMAGE) return@withContext null
            }
            PickedImage(name, out.toByteArray())
        }
    } catch (e: Exception) { null }
}

@Composable
fun OtaCard(spp: SppClient, connected: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ota by spp.ota.collectAsState()
    var picked by remember { mutableStateOf<PickedImage?>(null) }
    var pickError by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("bgap") }
    var newPassword by remember { mutableStateOf("") }
    var pwMessage by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val img = readImage(context, uri)
            pickError = when {
                img == null -> "File tidak bisa dibaca atau lebih dari 3 MB"
                img.bytes.size < 64 -> "File terlalu kecil untuk firmware"
                (img.bytes[0].toInt() and 0xFF) != 0xE9 -> "Bukan image aplikasi ESP32 (byte pertama harus 0xE9). Pakai firmware.bin dari build PlatformIO, bukan bootloader/partitions."
                ota.info?.supported == true && img.bytes.size > ota.info!!.max -> "Image ${img.bytes.size} byte melebihi slot ${ota.info!!.max} byte"
                else -> ""
            }
            picked = if (pickError.isEmpty()) img else null
        }
    }

    GlassCard(Modifier.padding(top = 8.dp), contentPadding = 0.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle("Update Firmware (OTA)")
            Text(
                "Mengirim firmware.bin lewat Bluetooth ke perangkat yang sedang tersambung (master/stereo). Slave sub nirkabel belum didukung. " +
                    "Pilih file untuk ENV yang sama dengan perangkat — file untuk env lain akan memasang firmware yang salah (pemulihan: flash USB).",
                style = MaterialTheme.typography.bodySmall,
            )

            val info = ota.info
            if (info != null) {
                Text(
                    if (info.supported) "Berjalan: ${info.run} → tujuan: ${info.next} (maks ${info.max / 1024} KB)\nBuild: ${info.build}"
                    else "Tabel partisi perangkat belum punya slot OTA. Flash USB sekali dengan partitions_ota.csv, lalu OTA bisa dipakai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (info.supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
                if (info.pwDefault) Text(
                    "Sandi OTA masih bawaan (\"bgap\"). Ganti di bawah agar orang lain yang tersambung tidak bisa mengganti firmware.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedButton(
                enabled = connected && !ota.busy,
                onClick = { scope.launch { spp.otaQueryInfo() } },
            ) { Text("Cek perangkat") }

            OutlinedTextField(
                value = password, onValueChange = { password = it.filter { c -> !c.isWhitespace() }.take(32) },
                label = { Text("Sandi OTA") }, singleLine = true, enabled = !ota.busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(enabled = !ota.busy, onClick = { launcher.launch(arrayOf("*/*")) }) { Text("Pilih file .bin") }
            picked?.let { Text("${it.name} — ${it.bytes.size / 1024} KB", style = MaterialTheme.typography.bodySmall) }
            if (pickError.isNotEmpty()) Text(pickError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

            if (ota.phase == OtaPhase.SENDING || ota.phase == OtaPhase.VERIFY) {
                LinearProgressIndicator(progress = { ota.progress }, modifier = Modifier.fillMaxWidth())
                Text("${ota.message}  ${(ota.progress * 100).toInt()}%  (${ota.sent / 1024}/${ota.total / 1024} KB)", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = ota.phase == OtaPhase.SENDING, onClick = { spp.otaCancel() }) { Text("Batal") }
            } else if (ota.message.isNotEmpty()) {
                Text(
                    ota.message, style = MaterialTheme.typography.bodySmall,
                    color = if (ota.phase == OtaPhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }

            Button(
                enabled = connected && !ota.busy && picked != null && password.length >= 4 && ota.info?.supported != false,
                onClick = { confirm = true },
            ) { Text("Mulai update") }

            // ---- ganti sandi ----
            OutlinedTextField(
                value = newPassword, onValueChange = { newPassword = it.filter { c -> !c.isWhitespace() }.take(32) },
                label = { Text("Sandi OTA baru (4–32 karakter)") }, singleLine = true, enabled = !ota.busy,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = connected && !ota.busy && newPassword.length in 4..32,
                onClick = {
                    scope.launch {
                        val err = spp.otaChangePassword(password, newPassword)
                        pwMessage = err ?: "Sandi diganti."
                        if (err == null) { password = newPassword; newPassword = ""; spp.otaQueryInfo() }
                    }
                },
            ) { Text("Ganti sandi") }
            if (pwMessage.isNotEmpty()) Text(pwMessage, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirm) {
        val img = picked
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Update firmware?") },
            text = {
                Text(
                    "Jangan matikan daya atau tutup app selama proses (±30–60 detik). Audio bisa terputus sebentar. " +
                        "Bila verifikasi gagal, firmware lama tetap utuh. Perangkat restart otomatis setelah sukses.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    if (img != null) scope.launch { spp.otaUpdate(img.bytes, password) }
                }) { Text("Mulai") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Batal") } },
        )
    }
}
