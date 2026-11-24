package com.tas5805m.ctrl.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.ctrl.data.AppViewModel
import com.tas5805m.ctrl.data.ThemeMode
import com.tas5805m.ctrl.ui.components.CardTitle
import com.tas5805m.ctrl.ui.components.GlassBadge
import com.tas5805m.ctrl.ui.components.GlassCard
import com.tas5805m.ctrl.ui.components.SwitchRow
import com.tas5805m.ctrl.ui.theme.OkGreen
import com.tas5805m.ctrl.ui.theme.WarnAmber

/** Koneksi — kartu "Perangkat Terhubung", daftar perangkat ter-pair, dan pengaturan tampilan/sambung otomatis. */
@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val bonded by vm.bonded.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // —— Perangkat terhubung ——
        item {
            GlassCard {
                CardTitle(
                    "Perangkat Terhubung",
                    trailing = { GlassBadge(if (state.connected) "TERHUBUNG" else "OFFLINE", color = if (state.connected) OkGreen else WarnAmber) },
                )
                if (state.connected) {
                    DeviceRow(state.deviceName.ifBlank { "BGAP-SPEAKER" }, state.deviceAddress) {
                        Button(
                            onClick = { vm.disconnect() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Default.LinkOff, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Putuskan", fontSize = 12.sp)
                        }
                    }
                } else {
                    Text(
                        "Pilih perangkat ESP32 yang sudah di-pair di Settings → Bluetooth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.lastError.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // —— Perangkat ter-pair ——
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PERANGKAT TER-PAIR",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.refreshBonded() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Refresh", Modifier.size(18.dp))
                }
            }
        }
        if (bonded.isEmpty()) {
            item {
                GlassCard {
                    Text("Tidak ada perangkat paired", fontWeight = FontWeight.Medium)
                    Text("Pair dulu di Settings Android", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(bonded, key = { it.address }) { device ->
            val isThis = state.connected && state.deviceAddress == device.address
            GlassCard(Modifier.clickable(enabled = !state.connected) { vm.connect(device) }) {
                DeviceRow(device.name ?: "Unknown", device.address) {
                    if (isThis) GlassBadge("Terhubung", color = OkGreen)
                    else FilledTonalButton(
                        onClick = { vm.connect(device) },
                        enabled = !state.connected,
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Sambung", fontSize = 12.sp) }
                }
            }
        }

        // —— Tampilan & koneksi ——
        item {
            GlassCard {
                CardTitle("Tampilan & Koneksi")
                // 7 tema ala HTML Final v4 — 3+4 agar label tidak pecah vertikal di layar sempit
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT),
                        listOf(ThemeMode.AMOLED, ThemeMode.NORD, ThemeMode.SUNSET, ThemeMode.FOREST),
                    ).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { m ->
                                FilterChip(
                                    selected = settings.theme == m,
                                    onClick = { vm.setTheme(m) },
                                    label = {
                                        Text(
                                            m.label,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    SwitchRow(
                        title = "Warna dinamis",
                        sub = "Material You (ikut wallpaper)",
                        checked = settings.dynamicColor,
                        onChange = { vm.setDynamicColor(it) },
                    )
                }
                SwitchRow(
                    title = "Auto-reconnect",
                    sub = "Buka app → sambung ke ${settings.lastName ?: "perangkat terakhir"}; bila putus, coba lagi 5× (jeda 3 dtk).",
                    checked = settings.autoReconnect,
                    onChange = { vm.setAutoReconnect(it) },
                )
            }
        }

        item {
            Text(
                "Tips:\n• Device harus Bluetooth Classic (SPP).\n• Setelah connect, app otomatis kirim caps + dump + status.\n• Mode 4 = Wireless Sub (multi-peer max 4).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

/** Baris perangkat: ikon speaker di kotak kaca, nama, alamat MAC (mono), dan aksi di kanan. */
@Composable
private fun DeviceRow(name: String, address: String, action: @Composable () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(primary.copy(alpha = 0.12f))
                .border(1.dp, primary.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Speaker, null, Modifier.size(20.dp), tint = primary) }
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(address, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        action()
    }
}
