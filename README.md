# TAS5805M CTRL — Pure Kotlin + Jetpack Compose

Aplikasi kontroler **Bluetooth Classic SPP** untuk firmware ESP32 + TAS5805M (termasuk multi-sub wireless).

## Fitur

UI Home + EQ terinspirasi pola AXTON/DS18 (status Connect, master gauge, kurva EQ frekuensi).


- **Multi-sub pairing UI lengkap** + grafik buffer real-time (fill sparkline, underrun/gap history, gauge per-peer)
  - Scan slave, daftar peer (max 4), status per-peer (link, fill, underrun, gap, RX rate)
  - Pair / Unpair individual / Unpair All
  - Trim latency (-60 … +100 ms, slider + tombol ±1/±5)
  - Fallback stereo indicator
  - Radio counters (rxa/rxc/rej/stx)
- **Voicing sub wireless** — HPF subsonic (biquad) + peaking di DSP chip slave, auto-setup dari **fb** (frekuensi resonansi driver) lewat `wsub_voice`.
  - Slave ≥ 2.9.20 (app ≥ 3.12.0): peaking bebas — frekuensi 30–150 Hz, Q 0,5–3,0, gain 0,5 dB.
  - Slave 2.9.19: peaking hanya di band 80/125 Hz (Q tetap, gain 1 dB); UI menyesuaikan otomatis.
  - Limiter dan harmonik sudah dihapus (diabaikan firmware): batasi gain peak (≤ +4 dB) dan volume.
- **Polaritas sub** + **auto-standby** (`subinv`, `standby`), level meter sub per slave (`wlvl`), fault slave (`WSUBS flt=`)
- **DSP lengkap** — 15-band EQ (1 dB, integer seperti firmware), link L/R, quick shapes, EQ center/sub di mode 3 (`eqc`)
- **Preset** — simpan/terapkan mode+volume+EQ (DataStore); preset bawaan EQ / voicing sub / **Scene** (EQ + voicing)
- **Diagnosa** — fault, level meter, CAPS, log SPP
- **Custom adaptive icon** (speaker + EQ bars)

## Build via GitHub Actions

1. Push repo ke GitHub
2. Tab **Actions** → **Build APK** (otomatis atau *Run workflow*)
3. Unduh artifact `Tas5805mCtrl-APK`

## Lokal

```bash
gradle assembleRelease      # butuh Gradle 8.9 + JDK 17 + Android SDK 35 (atau buka di Android Studio)
# APK: app/build/outputs/apk/release/app-release.apk
```

## Tab aplikasi

| Tab | Fungsi |
|-----|--------|
| Koneksi | Pair & connect SPP |
| Kontrol | Mode, volume, xover, gain, amp state, level meter |
| EQ | 15-band + quick shapes + kurva respons |
| DSP | Sub-tab **DSP** (mixer, profil filter, clipper, PWM, DAC, gain, standby, polaritas, simpan/reboot) dan **Preset** |
| **Wireless** | Multi-sub pairing, trim, voicing sub & diagnosa per-peer (tampil bila firmware wireless) |
| Diagnosa | Ringkasan masalah otomatis, status lengkap, riwayat laporan, log |

minSdk 26 · targetSdk 35 · Kotlin 2.0.20 · Compose BOM 2024.10.00

## Riwayat perubahan

Lihat [CHANGELOG.md](CHANGELOG.md).

## Dokumen terkait

Protokol SPP & firmware: `docs/PROTOCOL.md` dan `README.md` di proyek firmware (2.9.20).
