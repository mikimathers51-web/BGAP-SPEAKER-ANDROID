package com.tas5805m.ctrl.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/**
 * Jalankan [block] (biasanya loop polling) HANYA selama Activity berada di STARTED (terlihat di layar).
 * Saat app diminimalkan blok dibatalkan, dan dimulai ulang saat kembali — sehingga `lvl`/`status`/`wlvl`
 * tidak terus dikirim di latar belakang (hemat baterai HP dan beban radio Bluetooth ESP32).
 */
@Composable
fun PollWhileStarted(block: suspend CoroutineScope.() -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
}
