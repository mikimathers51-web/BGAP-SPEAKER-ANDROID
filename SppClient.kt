package com.tas5805m.ctrl.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.tas5805m.ctrl.data.AmpState
import com.tas5805m.ctrl.data.Diagnosis
import com.tas5805m.ctrl.data.LineParser
import com.tas5805m.ctrl.data.Preset
import com.tas5805m.ctrl.data.PresetTools
import com.tas5805m.ctrl.data.VoiceParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class SppClient(private val context: Context) {
    companion object {
        private const val TAG = "SppClient"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Jeda antar perintah (ms): firmware memproses 1 baris per loop dan membalas status panjang. */
        private const val TX_GAP_MS = 12L

        /** Perintah poll: tidak dicatat di log TX. */
        private val POLL_CMDS = setOf("lvl", "lvl2", "wlvl", "status")
        private const val MAX_QUEUED_POLL_GUARD = 40

        /** Format angka desimal SELALU dengan titik (Locale.US) — HP berlokal id-ID memakai koma, firmware tidak paham. */
        fun f1(v: Float): String = String.format(Locale.US, "%.1f", v)
        fun f2(v: Float): String = String.format(Locale.US, "%.2f", v)
    }

    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var socket: BluetoothSocket? = null
    private var readerJob: Job? = null
    private var writerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Antrean tulis tunggal: urutan perintah terjamin dan tidak ada dua penulisan bersamaan ke stream. */
    private val outbox = Channel<String>(Channel.UNLIMITED)

    // ---- OTA ---------------------------------------------------------------
    /** Balasan firmware yang berkaitan dengan OTA dipisah dari parser status biasa. */
    private val otaLines = Channel<String>(Channel.UNLIMITED)
    @Volatile private var otaBusy = false
    @Volatile private var otaCancelled = false
    private val _ota = MutableStateFlow(OtaStatus())
    val ota: StateFlow<OtaStatus> = _ota.asStateFlow()

    private val _state = MutableStateFlow(AmpState())
    val state: StateFlow<AmpState> = _state.asStateFlow()

    private val _bonded = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val bonded: StateFlow<List<BluetoothDevice>> = _bonded.asStateFlow()

    @SuppressLint("MissingPermission")
    fun refreshBonded() {
        try {
            _bonded.value = adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "refreshBonded", e)
        }
    }

    /** Perangkat dari alamat MAC tersimpan (untuk sambung otomatis); null bila alamat/izin tidak valid. */
    @SuppressLint("MissingPermission")
    fun remoteDevice(address: String): BluetoothDevice? = try {
        if (BluetoothAdapter.checkBluetoothAddress(address)) adapter?.getRemoteDevice(address) else null
    } catch (e: Exception) { Log.e(TAG, "remoteDevice", e); null }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        return@withContext try {
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            try { adapter?.cancelDiscovery() } catch (_: SecurityException) { /* tidak ada discovery yang berjalan */ }
            sock.connect()
            socket = sock
            // state lama (CAPS, peer, dsb.) dibuang agar tidak bocor ke perangkat lain
            _state.value = AmpState(
                connected = true,
                deviceName = device.name ?: "Unknown",
                deviceAddress = device.address,
            )
            startWriter(sock)
            startReader(sock)
            // initial sync (urutan dijamin antrean tulis)
            send("caps")
            delay(120)
            send("dump")
            delay(180)
            send("status")
            delay(1500)
            captureBaseline()
            true
        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            _state.update { it.copy(connected = false, lastError = "Connect gagal: ${e.message}") }
            false
        }
    }

    fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        writerJob?.cancel()
        writerJob = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        _state.update { it.copy(connected = false) }
    }

    private fun startWriter(sock: BluetoothSocket) {
        while (outbox.tryReceive().isSuccess) { /* buang sisa antrean sesi lama */ }
        queued.set(0); pendingPolls.clear()
        writerJob = scope.launch {
            try {
                val out = sock.outputStream
                for (cmd in outbox) {
                    if (queued.decrementAndGet() < 0) queued.set(0)
                    pendingPolls.remove(cmd.trim())
                    out.write((cmd.trim() + "\n").toByteArray(Charsets.UTF_8))
                    out.flush()
                    Log.d(TAG, ">> $cmd")
                    if (cmd.trim() !in POLL_CMDS) {
                        _state.update { it.copy(lastLines = (listOf("> " + cmd.trim()) + it.lastLines).take(LineParser.LOG_MAX)) }
                    }
                    delay(TX_GAP_MS)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "writer", e)
                    _state.update { it.copy(lastError = "Kirim gagal") }
                }
            }
        }
    }

    private fun startReader(sock: BluetoothSocket) {
        readerJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.UTF_8))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        if (OtaProtocol.isOtaLine(trimmed)) otaLines.trySend(trimmed)
                        else _state.update { LineParser.apply(it, trimmed) }
                    }
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "reader", e)
            } finally {
                // Hanya bila socket ini masih sesi aktif (bukan sisa sesi lama setelah reconnect).
                if (socket === sock) _state.update { it.copy(connected = false) }
            }
        }
    }

    /** Poll yang sudah mengantre tidak diantrekan lagi (mencegah antrean membengkak saat link macet). */
    private val pendingPolls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val queued = java.util.concurrent.atomic.AtomicInteger(0)

    fun send(cmd: String) {
        if (socket == null || otaBusy) return   // selama OTA stream mentah: tidak boleh ada perintah/poll nyasar
        val c = cmd.trim()
        if (c in POLL_CMDS) {
            if (queued.get() >= MAX_QUEUED_POLL_GUARD) return   // link macet: jangan menumpuk poll
            if (!pendingPolls.add(c)) return                    // poll sama masih menunggu giliran
        }
        if (outbox.trySend(cmd).isSuccess) queued.incrementAndGet() else pendingPolls.remove(c)
    }

    /** Dipakai alur OTA saja (melewati penjaga otaBusy). */
    private fun sendOta(cmd: String) {
        if (socket == null) return
        if (outbox.trySend(cmd).isSuccess) queued.incrementAndGet()
    }

    private fun drainOta() { while (otaLines.tryReceive().isSuccess) { /* buang sisa */ } }

    /** Tunggu baris OTA yang memenuhi [accept]; baris lain dilewati. null = waktu habis. */
    private suspend fun awaitOta(timeoutMs: Long, accept: (String) -> Boolean): String? =
        withTimeoutOrNull(timeoutMs) {
            var hit: String? = null
            while (hit == null) { val l = otaLines.receive(); if (accept(l)) hit = l }
            hit
        }

    /** `ota info`: null bila perangkat tidak menjawab (firmware lama tanpa OTA). */
    suspend fun otaQueryInfo(): OtaInfo? {
        if (socket == null || _ota.value.busy) return null
        _ota.value = _ota.value.copy(phase = OtaPhase.QUERY, message = "Memeriksa perangkat…")
        drainOta()
        sendOta("ota info")
        val line = awaitOta(3000) { it.startsWith("OTA INFO") || it.startsWith("ERR") }
        val info = line?.let { OtaProtocol.parseInfo(it) }
        _ota.value = OtaStatus(
            phase = OtaPhase.IDLE, info = info,
            message = if (info == null) "Perangkat tidak mendukung OTA (firmware lama?)" else "",
        )
        return info
    }

    /** Ganti sandi OTA. null = berhasil, selain itu pesan galat. */
    suspend fun otaChangePassword(old: String, new: String): String? {
        if (socket == null) return "Tidak terhubung"
        if (new.length !in 4..32 || new.any { it.isWhitespace() }) return OtaProtocol.errorText("ERR ota_pw_len")
        drainOta()
        sendOta("ota pass $old $new")
        val r = awaitOta(4000) { it == "OK ota_pass" || it.startsWith("ERR ota_") }
        return if (r == "OK ota_pass") null else OtaProtocol.errorText(r)
    }

    fun otaCancel() { otaCancelled = true }

    /**
     * Kirim [image] (.bin aplikasi ESP32) ke perangkat. Firmware memverifikasi SHA-256 + header image sebelum
     * mengganti slot boot; bila apa pun gagal, slot lama tetap utuh. Hasil akhir ada di [ota].
     */
    suspend fun otaUpdate(image: ByteArray, password: String) = withContext(Dispatchers.IO) {
        val sock = socket
        if (sock == null) { _ota.value = OtaStatus(OtaPhase.ERROR, message = "Tidak terhubung"); return@withContext }
        if (_ota.value.busy) return@withContext
        val total = image.size.toLong()
        fun fail(msg: String) { _ota.value = OtaStatus(OtaPhase.ERROR, _ota.value.sent, total, msg, _ota.value.info) }
        otaCancelled = false
        val sha = MessageDigest.getInstance("SHA-256").digest(image).joinToString("") { "%02x".format(it) }
        otaBusy = true
        try {
            _ota.value = OtaStatus(OtaPhase.SENDING, 0, total, "Menghubungi perangkat…", _ota.value.info)
            drainOta()
            sendOta("ota begin ${image.size} $sha $password")
            val ready = awaitOta(10_000) { it == "OTA READY" || it.startsWith("ERR ota_") }
            if (ready != "OTA READY") { fail(OtaProtocol.errorText(ready)); return@withContext }

            val out = sock.outputStream
            var sent = 0
            var acked = 0L
            _ota.value = _ota.value.copy(message = "Mengirim firmware…")
            while (sent < image.size) {
                if (otaCancelled) { fail("Dibatalkan — perangkat membatalkan sendiri dalam ±8 dtk, slot lama utuh"); return@withContext }
                while (sent - acked >= OtaProtocol.WINDOW) {
                    val l = awaitOta(8000) { it.startsWith("OTA ACK") || it.startsWith("ERR ota_") }
                    if (l == null || l.startsWith("ERR")) { fail(OtaProtocol.errorText(l)); return@withContext }
                    OtaProtocol.parseAck(l)?.let { acked = it }
                }
                val n = minOf(OtaProtocol.CHUNK, image.size - sent)
                out.write(image, sent, n)
                out.flush()
                sent += n
                _ota.value = _ota.value.copy(sent = sent.toLong())
            }
            _ota.value = _ota.value.copy(phase = OtaPhase.VERIFY, message = "Memverifikasi dan menulis slot…")
            val done = awaitOta(30_000) { it == "OK ota_done" || it.startsWith("ERR ota_") }
            when {
                done == "OK ota_done" ->
                    _ota.value = OtaStatus(OtaPhase.DONE, total, total, "Selesai. Perangkat restart — sambungkan ulang dalam beberapa detik.", _ota.value.info)
                done == null && !_state.value.connected -> fail("Koneksi putus saat verifikasi — kemungkinan perangkat sudah restart; sambungkan ulang dan cek `ota info`")
                else -> fail(OtaProtocol.errorText(done))
            }
        } catch (e: Exception) {
            Log.e(TAG, "ota", e)
            fail("Gagal kirim: ${e.message ?: "koneksi putus"}")
        } finally {
            otaBusy = false
        }
    }

    // ---- Convenience -------------------------------------------------------
    fun setMode(m: Int) = send("mode $m")
    fun setMaster(v: Int) = send("master $v")
    fun setSat(v: Int) = send("satvol $v")
    fun setSub(v: Int) = send("subvol $v")
    fun setCtr(v: Int) = send("ctr $v")
    fun setXover(hz: Int) = send("xover $hz")
    fun setGain(g: Int) = send("gain $g")
    fun setAmp(s: String) = send("ampstate $s")
    fun setEq(on: Boolean) = send(if (on) "eq on" else "eq off")

    /** Firmware memakai sscanf("%d"): gain EQ HARUS integer (−15..15). Pecahan akan terpotong diam-diam. */
    fun setEqBand(ch: String, band: Int, gain: Float) =
        send("eqband $ch $band ${gain.roundToInt().coerceIn(-15, 15)}")

    /** Mode 3: EQ center (`c`) / sub (`s`), band 3..14, gain integer. */
    fun setEqChip2(who: String, band: Int, gain: Float) =
        send("eqc $who $band ${gain.roundToInt().coerceIn(-15, 15)}")

    fun setMixer(ch: String, g: Float) = send("mixer $ch ${f2(g.coerceIn(-1f, 2f))}")
    fun setSubInv(on: Boolean) = send("subinv ${if (on) 1 else 0}")
    fun setStandby(minutes: Int) = send("standby ${minutes.coerceIn(0, 240)}")
    fun requestStatus() = send("status")
    fun requestDump() = send("dump")
    fun requestEqDump() = send("eqdump")
    fun requestLvl() = send("lvl")
    fun requestLvl2() = send("lvl2")
    fun requestWlvl() = send("wlvl")
    fun save() = send("save")
    fun wsubScan() = send("wsub_scan")
    fun wsubPair(mac: String) = send("wsub_pair $mac")
    fun wsubUnpair(mac: String? = null) =
        send(if (mac == null) "wsub_unpair" else "wsub_unpair $mac")
    fun wsubUnpairAll() = send("wsub_unpair_all")
    fun wsubTrim(ms: Int) = send("wsub_trim ${ms.coerceIn(-60, 100)}")
    /** Firmware ≥ 2.9.21 (CAPS `wsub_tune`): batas tambahan latensi penyelarasan sub, 0…200 ms. */
    fun wsubExtra(ms: Int) = send("wsub_extra ${ms.coerceIn(0, 200)}")
    fun wsubDiagReset() = send("wsub_diag_reset")
    fun setVoice(v: VoiceParams) {
        // Optimistis: UI memegang v terbaru sebelum firmware membalas `WSUBV`. Tanpa ini slider kedua yang digeser
        // sebelum balasan tiba memakai v basi dan mengembalikan nilai slider pertama di firmware.
        _state.update { it.copy(voice = v) }
        send(v.command())
    }

    // ---- v3.11: DSP lanjutan & diagnosa ------------------------------------
    fun setClip(gain: Int, l: Int, r: Int) = send("clipset ${gain.coerceIn(-300, 0)} ${l.coerceIn(0, 300)} ${r.coerceIn(0, 300)}")
    fun setMod(mode: String, freq: String, bd: String) = send("modset $mode $freq $bd")
    fun setDac(mode: String) = send("dacset $mode")
    /** ch: l | r | b ; n: 0..20 (0 FLAT, 1..10 LPF 60..150, 11..20 HPF 60..150). */
    fun setEqProfile(ch: String, n: Int) = send("eqpset $ch ${n.coerceIn(0, 20)}")
    fun setMixMode(m: String) = send("mixmode $m")
    fun requestInfo() = send("info")
    fun requestFault() = send("fault")
    fun erase() = send("erase")
    fun reboot() = send("reboot")

    /** Simpan counter saat ini sebagai titik nol pengukuran diagnosa. */
    fun captureBaseline() {
        _state.update { it.copy(baseline = Diagnosis.counters(it), baselineAtMs = System.currentTimeMillis()) }
    }

    /** Nolkan counter TX di firmware (`wsub_diag_reset`) lalu ukur ulang; riwayat fault sesi ikut dihapus. */
    suspend fun resetDiagnostics() {
        if (_state.value.hasWsub || _state.value.isWireless) send("wsub_diag_reset")
        delay(400)
        send("status")
        delay(900)
        _state.update { it.copy(faultHistory = emptyList()) }
        captureBaseline()
    }

    /** Kirim perintah mentah dari terminal (dibersihkan, maks 200 karakter sesuai firmware). */
    fun sendRaw(cmd: String) {
        val c = cmd.trim().take(200)
        if (c.isNotEmpty()) send(c)
    }

    /**
     * Terapkan preset. Mengembalikan null bila semua perintah diterima firmware, atau ringkasan singkat bila ada
     * balasan `ERR` selama proses (mis. mode terkunci, perintah tak dikenal di firmware lama).
     */
    suspend fun applyPreset(p: Preset): String? {
        val errBefore = _state.value.errCount
        when (p.kind) {
            "eq" -> applyEqOnly(p)
            "voice" -> applyVoiceOnly(p)
            "scene" -> applyScene(p)
            else -> applyFull(p)
        }
        delay(250)   // beri waktu balasan terakhir tiba sebelum menghitung ERR
        val st = _state.value
        val n = st.errCount - errBefore
        return if (n > 0) "$n perintah ditolak firmware (terakhir: ${st.lastError.removePrefix("ERR").trim().ifEmpty { "?" }})" else null
    }

    private suspend fun applyFull(p: Preset) {
        val st = _state.value
        // Mode hanya dikirim bila firmware mau menerimanya (master nirkabel terkunci mode 4, env 1-chip tanpa mode 1/3)
        var mode = st.mode
        if (p.mode != st.mode && st.modeAllowed(p.mode)) {
            setMode(p.mode)
            mode = p.mode
            delay(140)
        }
        val lockEq = mode != 0          // eq off & band 0..2 ditolak saat crossover aktif
        if (p.xover != st.xover || mode != st.mode) setXover(p.xover)
        setMaster(p.master)
        setSat(p.sat)
        setSub(p.sub)
        if (mode == 3) setCtr(p.ctr)
        setGain(p.gain)
        if (!lockEq || p.eqOn) setEq(p.eqOn)
        if (mode != 2) {                // mode 2: mixer dikunci
            setMixer("ll", p.ll); setMixer("lr", p.lr)
            setMixer("rl", p.rl); setMixer("rr", p.rr)
        }
        sendEqBands(p, mode)
        // --- v3.11 ---
        val st2 = _state.value
        p.clip?.takeIf { it.size >= 3 && st2.hasClip }?.let { setClip(it[0], it[1], it[2]); delay(40) }
        if (mode != 0 && st2.hasSubInv) p.subInv?.let { setSubInv(it) }
        if (st2.hasStandby) p.standby?.let { setStandby(it) }
        if (mode == 3 && st2.hasEqc) {
            for (i in 3..14) {
                p.eqCtr?.getOrNull(i)?.let { setEqChip2("c", i, it); delay(40) }
                p.eqSub?.getOrNull(i)?.let { setEqChip2("s", i, it); delay(40) }
            }
        }
        if (st2.hasWsub) {
            p.trim?.let { wsubTrim(it) }
            p.voice?.let { if (st2.hasVoice) setVoice(it) }
        }
        delay(100)
        requestStatus()
        requestEqDump()
    }

    /** Kirim band EQ (L/R atau linked); band 0..2 dilewati saat crossover aktif (mode ≠ 0). */
    private suspend fun sendEqBands(p: Preset, mode: Int) {
        val linked = p.eqLeft == p.eqRight
        for (i in PresetTools.activeBands(mode)) {
            if (linked) {
                setEqBand("b", i, p.eqLeft.getOrElse(i) { 0f })
            } else {
                setEqBand("l", i, p.eqLeft.getOrElse(i) { 0f })
                setEqBand("r", i, p.eqRight.getOrElse(i) { 0f })
            }
            delay(28)
        }
    }

    /** Preset bentuk EQ saja: tidak menyentuh volume/mixer/xover. */
    private suspend fun applyEqOnly(p: Preset) {
        val mode = _state.value.mode
        if (mode == 0 || p.eqOn) setEq(p.eqOn)
        sendEqBands(p, mode)
        delay(100)
        requestEqDump()
    }

    /** Preset voicing sub: hanya `wsub_voice` (+ trim bila ada). */
    private suspend fun applyVoiceOnly(p: Preset) {
        val st = _state.value
        if (!(st.hasWsub && st.hasVoice)) return
        p.trim?.let { wsubTrim(it); delay(60) }
        p.voice?.let { setVoice(it) }
        delay(100)
        requestStatus()
    }

    /** Scene: EQ (satelit) + voicing sub bila ada sub nirkabel; tanpa mode/xover/volume. */
    private suspend fun applyScene(p: Preset) {
        val st = _state.value
        val mode = st.mode
        if (mode == 0 || p.eqOn) setEq(p.eqOn)
        sendEqBands(p, mode)
        if (st.hasWsub && st.hasVoice) p.voice?.let { setVoice(it); delay(60) }
        delay(100)
        requestStatus()
        requestEqDump()
    }

}
