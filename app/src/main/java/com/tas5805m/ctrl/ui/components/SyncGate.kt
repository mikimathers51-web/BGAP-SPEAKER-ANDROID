package com.tas5805m.ctrl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tas5805m.ctrl.data.AmpState

/**
 * Pengganti layar saat data asli belum ada: belum terhubung, atau sudah terhubung tetapi `dump` pertama belum tiba.
 * Mencegah nilai bawaan `AmpState` (master 80, gain 18, EQ datar, …) tampil seolah kondisi sebenarnya perangkat.
 */
@Composable
fun NotReady(state: AmpState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (state.connected) {
                CircularProgressIndicator(Modifier.size(32.dp))
                Spacer(Modifier.height(12.dp))
                Text("Menyinkronkan dengan perangkat…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(Icons.Default.BluetoothDisabled, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Text("Hubungkan perangkat di tab Koneksi", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Peringatan kecil bila sinkronisasi tidak lengkap (batas waktu tercapai tanpa balasan `dump`). */
@Composable
fun SyncWarning(state: AmpState, modifier: Modifier = Modifier) {
    if (state.connected && state.syncPartial) {
        Text(
            "Sinkronisasi tidak lengkap — sebagian nilai mungkin belum dari perangkat. Cek firmware atau hubungkan ulang.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.fillMaxWidth(),
        )
    }
}
