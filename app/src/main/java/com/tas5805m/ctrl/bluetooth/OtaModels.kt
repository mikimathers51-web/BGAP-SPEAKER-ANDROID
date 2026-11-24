package com.tas5805m.ctrl.bluetooth

enum class OtaPhase { IDLE, QUERY, SENDING, VERIFY, DONE, ERROR }

/** Jawaban `ota info` dari firmware. */
data class OtaInfo(
    val run: String,          // label partisi yang sedang berjalan (app0/app1/factory)
    val next: String,         // slot tujuan (kosong "-" bila tabel partisi tidak punya slot OTA)
    val max: Long,            // ukuran maksimum image (byte)
    val pwDefault: Boolean,   // true = sandi OTA masih bawaan ("bgap") → minta pengguna menggantinya
    val build: String,
) {
    val supported: Boolean get() = next != "-" && max > 0
}

data class OtaStatus(
    val phase: OtaPhase = OtaPhase.IDLE,
    val sent: Long = 0,
    val total: Long = 0,
    val message: String = "",
    val info: OtaInfo? = null,
) {
    val busy: Boolean get() = phase == OtaPhase.QUERY || phase == OtaPhase.SENDING || phase == OtaPhase.VERIFY
    val progress: Float get() = if (total > 0) (sent.toFloat() / total).coerceIn(0f, 1f) else 0f
}

object OtaProtocol {
    const val CHUNK = 1024          // byte per tulis
    const val WINDOW = 2048         // maks byte tertunda sebelum menunggu ACK (ring SPP firmware = 4096)
    const val MAX_IMAGE = 3_000_000 // batas keras pembacaan file di app

    /** `OTA INFO run=app0 next=app1 max=2031616 pw=default active=0 build=Nov 20 2026 12:00:00` */
    fun parseInfo(line: String): OtaInfo? {
        if (!line.startsWith("OTA INFO")) return null
        val body = line.removePrefix("OTA INFO").trim()
        val buildIdx = body.indexOf("build=")
        val head = if (buildIdx >= 0) body.substring(0, buildIdx) else body
        val build = if (buildIdx >= 0) body.substring(buildIdx + 6).trim() else ""
        val kv = head.trim().split(' ').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
        return OtaInfo(
            run = kv["run"] ?: "-",
            next = kv["next"] ?: "-",
            max = kv["max"]?.toLongOrNull() ?: 0L,
            pwDefault = kv["pw"] != "custom",
            build = build,
        )
    }

    fun parseAck(line: String): Long? =
        if (line.startsWith("OTA ACK ")) line.removePrefix("OTA ACK ").trim().toLongOrNull() else null

    /** Pesan galat ramah dari balasan firmware `ERR ota_*`. */
    fun errorText(line: String?): String = when {
        line == null -> "Tidak ada jawaban dari perangkat (waktu habis)"
        line.contains("ota_auth") -> "Sandi OTA salah"
        line.contains("ota_locked") -> "Terkunci 60 detik setelah 3 kali salah sandi"
        line.contains("ota_no_slot") -> "Tabel partisi perangkat belum punya slot OTA — flash lewat USB sekali dengan partitions_ota.csv"
        line.contains("ota_size") -> "Ukuran image tidak valid atau melebihi slot"
        line.contains("ota_sha") -> "SHA-256 tidak valid"
        line.contains("ota_hash") -> "Isi image rusak saat transfer (SHA-256 tidak cocok) — slot lama tidak diubah"
        line.contains("ota_image") -> "Image ditolak (bukan firmware ESP32 yang valid) — slot lama tidak diubah"
        line.contains("ota_write") -> "Gagal menulis flash"
        line.contains("ota_timeout") -> "Perangkat berhenti menerima data (waktu habis)"
        line.contains("ota_busy") -> "OTA lain sedang berjalan"
        line.contains("ota_begin") -> "Perangkat tidak bisa memulai OTA"
        line.contains("ota_nvs") -> "Perangkat gagal menyimpan sandi baru (NVS)"
        line.contains("ota_args") -> "Perintah OTA tidak lengkap"
        line.contains("ota_pw_len") -> "Sandi baru harus 4–32 karakter tanpa spasi"
        else -> line
    }

    fun isOtaLine(line: String) = line.startsWith("OTA ") || line.startsWith("ERR ota") || line.startsWith("OK ota")
}
