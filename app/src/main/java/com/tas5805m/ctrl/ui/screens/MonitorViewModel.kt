package com.tas5805m.ctrl.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

data class MeterReading(
    val levelDb: Float = -60f,
    val peakDb: Float = -60f,
    val isClipping: Boolean = false
)

data class MonitorUiState(
    val mode: MeterMode = MeterMode.PEAK,
    val masterL: MeterReading = MeterReading(),
    val masterR: MeterReading = MeterReading(),
    val sub: MeterReading = MeterReading(),
    val satL: MeterReading = MeterReading(),
    val satR: MeterReading = MeterReading(),
    val peer1: MeterReading = MeterReading(),
    val peer2: MeterReading = MeterReading(),
    val peer3: MeterReading = MeterReading()
)

enum class MeterMode { PEAK, RMS }

class MonitorViewModel : ViewModel() {

    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()

    private var loopJob: Job? = null
    private var clipTimers = mutableMapOf<String, Long>()

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val t = (System.currentTimeMillis() - startTime) / 1000f
                tick(t)
                delay(33L)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
    }

    fun setMode(mode: MeterMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun resetPeaks() {
        val current = _state.value
        _state.value = current.copy(
            masterL = current.masterL.copy(peakDb = -60f),
            masterR = current.masterR.copy(peakDb = -60f),
            sub = current.sub.copy(peakDb = -60f),
            satL = current.satL.copy(peakDb = -60f),
            satR = current.satR.copy(peakDb = -60f),
            peer1 = current.peer1.copy(peakDb = -60f),
            peer2 = current.peer2.copy(peakDb = -60f),
            peer3 = current.peer3.copy(peakDb = -60f)
        )
    }

    private fun tick(t: Float) {
        val isRms = _state.value.mode == MeterMode.RMS

        val subTarget = signal(t, -18f, 0f, 1.7f) + sin(t * 0.4f) * 3f
        val satLTarget = signal(t, -22f, 1.1f, 2.1f)
        val satRTarget = signal(t, -22f, 2.3f, 1.9f)
        val masterLTarget = maxOf(subTarget, satLTarget) + 1.5f
        val masterRTarget = maxOf(subTarget, satRTarget) + 1.5f
        val p1 = masterLTarget - 2f + sin(t * 0.7f) * 2.5f
        val p2 = masterRTarget - 4f + sin(t * 0.9f + 1.3f) * 3f
        val p3 = maxOf(masterLTarget, masterRTarget) - 6f + sin(t * 1.1f + 2.2f) * 3.5f

        fun adjust(target: Float) = if (isRms) target - 3f else target
        fun attack() = if (isRms) 0.15f else 0.40f
        fun release() = if (isRms) 0.08f else 0.15f

        val current = _state.value
        _state.value = current.copy(
            masterL = update(current.masterL, adjust(masterLTarget), 0.45f, 0.18f, "masterL"),
            masterR = update(current.masterR, adjust(masterRTarget), 0.45f, 0.18f, "masterR"),
            sub = update(current.sub, adjust(subTarget), 0.30f, 0.12f, "sub"),
            satL = update(current.satL, adjust(satLTarget), attack(), release(), "satL"),
            satR = update(current.satR, adjust(satRTarget), attack(), release(), "satR"),
            peer1 = update(current.peer1, adjust(p1), 0.35f, 0.12f, "peer1"),
            peer2 = update(current.peer2, adjust(p2), 0.35f, 0.12f, "peer2"),
            peer3 = update(current.peer3, adjust(p3), 0.35f, 0.12f, "peer3")
        )
    }

    private fun update(
        reading: MeterReading,
        targetDb: Float,
        attack: Float,
        release: Float,
        key: String
    ): MeterReading {
        val newLevel = if (targetDb > reading.levelDb) {
            reading.levelDb + (targetDb - reading.levelDb) * attack
        } else {
            reading.levelDb + (targetDb - reading.levelDb) * release
        }
        val newPeak = when {
            newLevel > reading.peakDb -> newLevel
            else -> (reading.peakDb - 0.35f).coerceAtLeast(-60f).coerceAtLeast(newLevel)
        }

        // Clip detection
        val now = System.currentTimeMillis()
        val isClipping = newLevel > -1f && (now - (clipTimers[key] ?: 0L)) > 250L
        if (isClipping) clipTimers[key] = now

        return MeterReading(
            levelDb = newLevel.coerceIn(-60f, 0f),
            peakDb = newPeak.coerceIn(-60f, 0f),
            isClipping = isClipping
        )
    }

    private fun signal(t: Float, base: Float, offset: Float, speed: Float): Float {
        return base +
            sin(t * speed + offset) * 6f +
            sin(t * speed * 2.31f + offset * 1.7f) * 3.5f +
            sin(t * speed * 5.13f + offset * 2.9f) * 1.8f +
            sin(t * speed * 9.7f + offset * 4.1f) * 0.9f
    }
}
