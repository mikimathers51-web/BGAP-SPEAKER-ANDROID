package com.tas5805m.ctrl.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tas5805m.ctrl.bluetooth.SppClient
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val spp = SppClient(app)

    val state: StateFlow<AmpState> = spp.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AmpState()
    )
    val bonded = spp.bonded

    private val presetRepo = PresetRepository(app)
    val presets: StateFlow<List<Preset>> = presetRepo.presets.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val diagRepo = DiagHistoryRepository(app)
    val diagHistory: StateFlow<List<DiagReport>> = diagRepo.reports.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val settingsRepo = SettingsRepository(app)
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.Eagerly, AppSettings()
    )

    /** 0 = tidak sedang menyambung ulang; 1..[MAX_RECONNECT] = nomor percobaan. */
    private val _reconnecting = MutableStateFlow(0)
    val reconnecting: StateFlow<Int> = _reconnecting.asStateFlow()
    private var reconnectJob: Job? = null
    private var manualDisc = false
    private var autoTried = false

    init {
        spp.refreshBonded()
        // Koneksi putus sendiri (bukan tombol Putuskan) → sambung ulang otomatis bila diaktifkan.
        viewModelScope.launch {
            var was = false
            spp.state.map { it.connected }.distinctUntilChanged().collect { now ->
                if (was && !now && !manualDisc) scheduleReconnect()
                was = now
            }
        }
    }

    private fun hasBtPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Dipanggil sekali saat app dibuka (setelah izin Bluetooth ada): sambung ke perangkat terakhir. */
    fun tryAutoConnect() {
        if (autoTried || !hasBtPermission()) return
        autoTried = true
        viewModelScope.launch {
            val st = settingsRepo.settings.first()
            val addr = st.lastAddress ?: return@launch
            if (!st.autoReconnect || state.value.connected) return@launch
            val dev = spp.remoteDevice(addr) ?: return@launch
            manualDisc = false
            if (spp.connect(dev)) settingsRepo.setLastDevice(addr, dev.name ?: st.lastName ?: addr)
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val st = settingsRepo.settings.first()
            val addr = st.lastAddress ?: return@launch
            if (!st.autoReconnect || !hasBtPermission()) return@launch
            for (i in 1..MAX_RECONNECT) {
                delay(RECONNECT_DELAY_MS)
                if (manualDisc || state.value.connected) { _reconnecting.value = 0; return@launch }
                _reconnecting.value = i
                val dev = spp.remoteDevice(addr)
                if (dev != null && spp.connect(dev)) { _reconnecting.value = 0; return@launch }
            }
            _reconnecting.value = 0
        }
    }

    fun setTheme(m: ThemeMode) { viewModelScope.launch { settingsRepo.setTheme(m) } }
    fun setDynamicColor(on: Boolean) { viewModelScope.launch { settingsRepo.setDynamic(on) } }
    fun setAutoReconnect(on: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoReconnect(on) }
        if (!on) { reconnectJob?.cancel(); _reconnecting.value = 0 }
    }

    fun setMeterStyle(i: Int) { viewModelScope.launch { settingsRepo.setMeterStyle(i) } }
    fun setMeterRms(on: Boolean) { viewModelScope.launch { settingsRepo.setMeterRms(on) } }

    fun refreshBonded() = spp.refreshBonded()

    fun connect(device: android.bluetooth.BluetoothDevice) {
        manualDisc = false
        reconnectJob?.cancel(); _reconnecting.value = 0
        viewModelScope.launch {
            if (spp.connect(device)) {
                settingsRepo.setLastDevice(device.address, try { device.name } catch (_: SecurityException) { null } ?: device.address)
            }
        }
    }

    fun disconnect() {
        manualDisc = true
        reconnectJob?.cancel(); _reconnecting.value = 0
        spp.disconnect()
    }
    fun send(cmd: String) = spp.send(cmd)

    val builtinPresets: List<Preset> = BuiltinPresets.all

    fun savePreset(name: String) {
        val s = state.value
        val clip = listOf("gain", "ml", "mr").map { s.tagInt("CLIP", it) }
        val p = Preset(
            name = name.ifBlank { "Preset ${System.currentTimeMillis() % 10000}" },
            mode = s.mode, xover = s.xover,
            xoverLp = if (s.hasXover2 && s.xoverLp != s.xover) s.xoverLp else null,
            master = s.master, sat = s.sat,
            sub = s.sub, ctr = s.ctr, gain = s.gain, eqOn = s.eqOn,
            ll = s.ll, lr = s.lr, rl = s.rl, rr = s.rr,
            eqLeft = s.eqLeft, eqRight = s.eqRight,
            created = java.time.Instant.now().toString(),
            clip = if (clip.all { it != null }) clip.map { it ?: 0 } else null,
            subInv = if (s.hasSubInv) s.subInv else null,
            standby = if (s.hasStandby) s.standbyMin else null,
            trim = if (s.hasWsub) s.wsubTrim else null,
            voice = if (s.hasWsub && s.hasVoice) s.voice else null,
            eqCtr = if (s.mode == 3 && s.hasEqc) s.eqCtr else null,
            eqSub = if (s.mode == 3 && s.hasEqc) s.eqSub else null,
        )
        viewModelScope.launch { presetRepo.add(p) }
    }

    /** Simpan EQ 15 band saat ini sebagai preset jenis "eq" (tanpa mode/xover/volume). */
    fun saveEqPreset(name: String, left: List<Float>, right: List<Float>) {
        val p = Preset(
            name = name.ifBlank { "EQ ${System.currentTimeMillis() % 10000}" },
            kind = "eq",
            eqOn = true, eqLeft = left, eqRight = right,
            created = java.time.Instant.now().toString(),
        )
        viewModelScope.launch { presetRepo.add(p) }
    }

    /** Simpan EQ + voicing sub saat ini sebagai preset "scene" (tanpa mode/xover/volume). */
    fun saveScene(name: String) {
        val s = state.value
        val p = Preset(
            name = name.ifBlank { "Scene ${System.currentTimeMillis() % 10000}" },
            kind = "scene",
            eqOn = s.eqOn, eqLeft = s.eqLeft, eqRight = s.eqRight,
            created = java.time.Instant.now().toString(),
            voice = if (s.hasWsub && s.hasVoice) s.voice else null,
        )
        viewModelScope.launch { presetRepo.add(p) }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch { presetRepo.removeById(id) }
    }

    /** Terapkan preset; `onDone` menerima null (semua diterima) atau ringkasan perintah yang ditolak firmware. */
    fun applyPreset(p: Preset, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch { onDone(spp.applyPreset(p)) }
    }

    suspend fun exportPresets(): String = presetRepo.exportJson()

    /** Mengembalikan jumlah preset yang berhasil diimpor. */
    suspend fun importPresets(raw: String): Int = presetRepo.importJson(raw)

    /** Simpan teks laporan ke riwayat (maks 20 terbaru). */
    fun saveDiagReport(title: String, text: String) {
        val r = DiagHistory.make(title, text, System.currentTimeMillis())
        viewModelScope.launch { diagRepo.add(r) }
    }

    fun deleteDiagReport(id: String) { viewModelScope.launch { diagRepo.remove(id) } }

    fun clearDiagHistory() { viewModelScope.launch { diagRepo.clear() } }

    fun resetDiagnostics() {
        viewModelScope.launch { spp.resetDiagnostics() }
    }

    /** Muat ulang semua status (caps, dump, status, fault, info) — dipakai tombol Refresh di Diagnosa. */
    fun refreshAll() {
        viewModelScope.launch {
            spp.send("caps"); kotlinx.coroutines.delay(120)
            spp.requestDump(); kotlinx.coroutines.delay(200)
            spp.requestStatus(); spp.requestFault()
            if (state.value.hasInfo) spp.requestInfo()
        }
    }

    private companion object {
        const val MAX_RECONNECT = 5
        const val RECONNECT_DELAY_MS = 3000L
    }
}
