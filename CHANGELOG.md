# Changelog

## 3.19.0
- **EQ — Sync L→R**: tombol baru di toolbar EQ (muncul saat mode *L / R* terpisah, bukan di target CTR/SUB) yang menyalin gain kiri ke kanan lewat `eqband r`. Hanya band yang berbeda yang dikirim; band 0–2 dilewati bila crossover mengunci EQ (firmware menolak `eqband` untuk band tersebut); gain dibulatkan integer −15..15. Setelah selesai EQ dibaca ulang (`eqdump`) dan muncul ringkasan "N band disalin". Tombol *Sync* lama berganti nama **Muat ulang** (fungsinya tetap `eqdump`). Toolbar EQ kini bisa digulir horizontal.
- **Monitor — pilihan meter tersimpan**: gaya meter (Classic / Segmented / Minimal) dan mode PEAK/AVG disimpan di DataStore (`meter_style`, `meter_rms`) sehingga bertahan setelah app ditutup. Sebelumnya gaya hanya variabel global di memori dan mode selalu kembali ke PEAK.
- **Monitor — data asli**: tab Monitor tidak lagi memakai sinyal simulasi (sinus). Level dibaca dari firmware lewat `lvl` (chip 1), `lvl2` (chip 2, mode 1/3/5) dan `wlvl` (sub nirkabel, mode 4) ±3×/dtk, hanya saat layar terlihat; di mode 4 `status` ikut di-poll ±1,5 dtk karena `WLVL` hanya mengisi peer yang sudah dikenal dari `WSUBS`. Label kanal mengikuti mode (L/R, SAT/SUB, SAT L/R + Sub, Center/Sub, TW/WF, Sub 1–4). Slave offline atau `WLVL … =na` tampil "n/a". Ada penahan puncak, puncak sesi (**SES**) dan tombol **RESET PEAKS** (`MeterChannel`). Indikator CLIP menyala bila puncak ≥ −1 dB.
- Toggle **RMS diganti AVG**: firmware hanya mengirim level cuplikan, jadi nilainya rata-rata daya ±24 sampel (bukan RMS gelombang); label disesuaikan agar jujur. Kunci DataStore tetap `meter_rms`.
- `MonitorViewModel` (generator sinyal palsu, tidak terpakai) dihapus. Pemetaan kanal ada di `MonitorLayout`; uji unit baru: `MonitorLayoutTest`.
- **Wireless**: grafik mini "BUFFER · LAST 30S" di kartu peer dibuang — itu gelombang sintetis (sinus dari nilai fill), bukan riwayat nyata. Diganti angka asli `fill / target ms` dan `und`/`gap` per slave. Grafik "Riwayat fill" (data nyata, agregat) tidak berubah.
- **OTA dihapus**: kartu *Update Firmware (OTA)* di Diagnosa, `OtaModels.kt`, `OtaCard.kt`, `OtaProtocolTest` dan seluruh kode OTA di `SppClient` (state `ota`, antrean `otaLines`, penjaga `otaBusy`, `otaUpdate`/`otaQueryInfo`/`otaChangePassword`) dibuang, karena firmware dibuat tanpa OTA. Baris balasan kini langsung ke `LineParser`. Salinan lama `SppClient.kt` dan `OtaModels.kt` di root repo juga dihapus. Entri OTA di versi 3.13.0 di bawah dibiarkan sebagai riwayat.
- Uji unit baru: `EqSyncTest`.

## 3.18.0
- **Loudness sub ikut volume ponsel** (firmware ≥ 2.9.26): sakelar *Ikut volume ponsel* di kartu Loudness sub; menampilkan volume efektif, master, dan volume ponsel. Baris `SUBLOUD` kini memuat `ph= pv= eff=`; perintah `subloud_phone 0|1`.
- **Mode 6 — 1 chip mono 2-way** (firmware ≥ 2.9.26, env `esp32dev_1chip_2way`): chip *2W 1c* di pilihan Mode (tidak butuh chip 2). 2-way selalu aktif seperti mode 5: OUT_L = tweeter (HPF), OUT_R = woofer (LPF), mono; kartu 2-way hanya slider crossover, Channel Mix *Tweeter (L)* / *Woofer (R)*, Loudness sub tetap tersedia. `AmpState.isMono2Way`, `modeName()` = "2-way 1 Chip".
- **Mode 5 — 2 chip stereo 2-way** (firmware ≥ 2.9.26, env `esp32dev_2chip_2way`): chip 1 = dua tweeter (HPF), chip 2 = dua woofer (LPF), sinyal tetap stereo. Chip *2W 2c* baru di pilihan Mode (butuh chip 2, CAPS `ctr`). Kartu *2-way aktif (stereo, 2 chip)* tanpa sakelar — hanya slider crossover (`tway_hz`); Channel Mix berlabel *Tweeter (2×)* / *Woofer (2×)* (tombol fase = polaritas kedua woofer). Kartu Crossover tabel, Loudness sub dan Mixer disembunyikan/dikunci di mode ini; preset yang diterapkan di mode 5 tidak mengirim `xover`/mixer. `AmpState.isStereo2Way`, `modeName()` = "2-way Stereo".
- **Psychoacoustic Bass** (firmware ≥ 2.9.26, CAPS `pbass`, **semua mode 0–4**): kartu *Psychoacoustic Bass* di Home — sakelar, *Jumlah* (0–100 %, langkah 5) dan *FC* (40–150 Hz, langkah 10; titik awal harmonik = frekuensi terendah yang masih dimainkan speaker). Firmware menambahkan harmonik 2f/3f dari bass ke L+R sebelum I2S; badge menampilkan ON · jumlah. Perintah `pbass 0|1` dan `pbass_cfg <amount> <fc>`; baris `PBASS on= amt= fc= act=` diparse (tidak masuk log).
- **Crossover sampai 300 Hz di mode 2** (firmware CAPS `xext`): slider HPF satelit / LPF sub melebar ke 60–300 Hz hanya di mode 2 dengan firmware yang mendukung; mode lain dan firmware lama tetap 60–150 Hz. Preset mode 2 boleh menyimpan sampai 300 Hz. Peringatan bahwa di atas 150 Hz firmware memakai filter LR4 hitungan.
- `AmpState`: `pbass`, `pbassAmt`, `pbassFc`, `pbassActive`, `hasPbass`, `hasXext`, `xoverMax`.
- Uji unit: `XoverSplitTest` (xext) dan `PbassTest` (baru).


## 3.17.0
- **Loudness sub untuk semua mode bersub** (firmware ≥ 2.9.25): kartu *Loudness sub* kini muncul di mode 1, 2, 3, dan 4 (sebelumnya hanya mode 2); mode 0 tetap tanpa kartu karena tidak punya sub. Di mode 4 boost ditambahkan ke level yang dikirim ke slave (resolusi 1 dB), slave tidak perlu di-update.

## 3.16.0
- **Loudness sub** (firmware ≥ 2.9.24, CAPS `subloud`, hanya mode 2): kartu *Loudness sub* di Home — sakelar, *Boost maks* (0–6 dB, langkah 0,5), *Mulai di master ≤* dan *Boost penuh di master ≤*. Sub naik otomatis saat master volume turun agar bass tetap terdengar pelan-pelan; badge menampilkan boost yang sedang diterapkan. Peringatan clipping untuk bass sangat keras.
- `AmpState`: `subloud`, `subloudDb10/Lo/Hi/Cur`, `hasSubloud`; baris `SUBLOUD …` diparse (tidak masuk log). Perintah `subloud 0|1` dan `subloud_cfg <db10> <lo> <hi>`.
- Uji unit: `XoverSplitTest` ditambah kasus SUBLOUD.

## 3.15.0
- **2-way aktif mono** (firmware ≥ 2.9.23, CAPS `tway`, hanya mode 2): kartu *2-way aktif (mono)* di Home — sakelar `tway`, slider crossover (`tway_hz`, langkah 50 Hz, dibatasi `min`/`max` dari firmware, bawaan 1000–5000 Hz). Kanal L = tweeter (HPF), kanal R = woofer (LPF), Linkwitz-Riley orde-4. Saat aktif, kartu Crossover tabel disembunyikan dan Channel Mix berlabel *Tweeter (L)* / *Woofer (R)*.
- `AmpState`: `tway`, `twayHz`, `twayMin`, `twayMax`, `hasTway`, `twayActive`; baris `TWAY …` diparse (tidak masuk log).
- Uji unit: `XoverSplitTest` ditambah kasus TWAY.

## 3.14.0
- **Crossover terpisah** (firmware ≥ 2.9.22, CAPS `xover2`): kartu Crossover di Home punya slider *HPF SAT* dan *LPF SUB* sendiri (60–150 Hz, snap 10 Hz) dengan sakelar **Kunci bersama** (aktif = satu perintah `xover` untuk keduanya, seperti sebelumnya). Bila tidak dikunci dan HPF ≠ LPF, tampil peringatan overlap/celah. Firmware lama tetap memakai satu slider.
- Perintah baru `xover_hp` / `xover_lp`; baris `MODE` memuat `xhp=`/`xlp=` (`xover=` tetap = HPF). `AmpState.xoverLp` (firmware lama: sama dengan `xover`).
- **Preset** menyimpan `xoverLp` (opsional; preset lama = LPF sama dengan HPF) dan menerapkannya lewat `xover_hp` + `xover_lp` bila berbeda.
- Uji unit baru: `XoverSplitTest`.

## 3.13.0
- **Wireless:** kartu *Penyelarasan latensi* (firmware ≥ 2.9.21, CAPS `wsub_tune`): slider 0–200 ms (`wsub_extra`) untuk membatasi seberapa jauh sub boleh menunda diri; menampilkan latensi dasar dan yang diperintahkan ke sub (`latb`/`latc` dari `WSUBT`).
- **Diagnosa:** kartu *Status ringkas* di paling atas (Amp, Fault, Mode, Firmware, SPP, peer wireless) — hanya data yang benar-benar dilaporkan firmware (tanpa suhu/PVDD/uptime). Judul kartu Diagnosa dan OTA diseragamkan dengan `CardTitle`.
- **EQ:** kurva respons diberi isian gradien di bawah garis (gaya preview).
- Semua layar (Koneksi, Home, EQ, DSP/Preset, Wireless, Monitor, Diagnosa + kartu OTA) memakai `GlassCard`; layar **Koneksi** ditulis ulang (kartu perangkat terhubung, daftar ter-pair dengan tombol Sambung, switch Auto-reconnect/Warna dinamis).
- `hasVoice` kini juga mengenali token `voice_basic` dari `CAPS` firmware (tidak lagi bergantung pada kedatangan baris `WSUBV`).
- Pesan galat OTA untuk `ota_nvs` dan `ota_args`.
- Layar **Wireless** dan kurva **EQ** memakai `GlassCard` yang sama dengan Home; warna status (merah/kuning/hijau) di Wireless dan EQ kini dari tema (`BadRed`/`WarnAmber`/`OkGreen`). Perilaku tidak berubah.
- **Home** ditulis ulang bergaya Glass (komponen baru `GlassCard`/`CardTitle`/`GlassBadge`/`SwitchRow`): master angka besar, Channel Mix (sub dengan tombol fase 0°/180°, sat, ctr), crossover satu slider LPF sub ⇄ HPF sat (snap 10 Hz), gain analog, Mode, switch **Mute** + amp state + auto-standby, level meter. Hanya kontrol yang ada di firmware 2.9.20; Loudness/Night/sumber input/PVDD/suhu sengaja tidak ditampilkan.
- Tampilan baru "Glass" (panel hampir hitam, kartu kaca, cyan #7DD3FC + amber #FBBF24), tema Sistem/Gelap/Terang + warna dinamis, top bar logo + status mono berdenyut, snackbar galat.
- Sambung otomatis ke perangkat terakhir saat app dibuka; sambung ulang 5× (jeda 3 dtk) saat koneksi putus. Antrean poll dibatasi/di-dedupe.
- Tab **Monitor** baru: meter L/R, chip 2, level sub wireless (PEAK/AVG, peak-hold, puncak sesi, Reset Peaks) dan sparkline buffer 30 dtk per sub.
- Polling `lvl`/`status`/`wlvl` kini hanya berjalan saat app terlihat (`PollWhileStarted`), tidak lagi di latar belakang.
- Preset `voice` dipaksa `harm=0 limit=100` (firmware menolak nilai lain).
- 12 preset bawaan baru (EQ, voicing sub, scene).
- Bandwidth loop class-D dibatasi sesuai datasheet per modulasi dan frekuensi switching (`ModRules`).
- Kartu **Update Firmware (OTA)** di tab Diagnosa: kirim `firmware.bin` lewat Bluetooth (butuh firmware dengan OTA + tabel partisi 2 slot).

## 3.12.1

- **Slider fb (Voicing Sub)**: rentang kini 30–150 Hz, sama dengan peaking/firmware (sebelumnya 30–100 Hz sehingga thumb tertahan di 100 saat label menampilkan nilai lebih besar).
- **Voicing**: perintah `wsub_voice` kini memperbarui state UI langsung (optimistis). Sebelumnya, menggeser dua slider berurutan sebelum balasan `WSUBV` tiba bisa mengirim nilai basi dan mengembalikan slider pertama di firmware.
- **SendSlider**: tidak lagi mengirim perintah ganda untuk nilai yang sama (throttle lalu "dilepas", atau beberapa posisi float yang dibulatkan ke Int yang sama) — mengurangi beban link SPP/A2DP.
- Teks UI/dokumentasi disesuaikan: keterangan slider fb (gain +4 dB hanya bila gain sebelumnya ≤ 0), catatan voicing slave ≥ 2.9.20, tabel tab, README ringkas, riwayat dipindah ke berkas ini.

## 3.12.0

- **Preset**
  - Preset punya `id` (UUID); hapus berdasarkan id, bukan indeks (tidak salah hapus saat daftar berubah). Preset lama mendapat id deterministik.
  - Jenis baru **Scene** = EQ + voicing sub tanpa mode/xover/volume (aman di mode 4, tidak melonjakkan volume). Tombol "Simpan sebagai Scene".
  - Preset bawaan baru: EQ (Satelit kecil, Suara/Podcast, Hangat, Anti-desis, Game), voicing (Sub sealed kecil, Sub ported, Ruangan kecil, Sub aman), Scene (Malam, Film, Musik, Pesta).
  - Peringatan per preset: band 20/32/50 Hz dilewati saat crossover aktif (bass lewat voicing sub), perkiraan boost maks + saran turunkan Master, peak sub > +4 dB tanpa limiter.
  - Apply memberi tahu bila firmware menolak perintah (hitung baris `ERR` selama apply) — bukan sekadar "dikirim".
  - Impor: semua nilai di-clamp ke rentang firmware (NaN aman), maks 200 KB / 50 preset per impor / 100 total, duplikat persis dilewati, nama kembar diberi akhiran (2).
- **Voicing sub** (slave ≥ 2.9.20): peaking bebas — frekuensi, **Q**, gain 0,5 dB. Slave 2.9.19 tetap didukung (UI lama otomatis).
- **Build**: rilis dapat ditandatangani kunci sendiri lewat secret `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (tanpa itu: kunci debug + peringatan). Tes unit sekarang **menggagalkan** build; tes baru `PresetToolsTest`.

### 3.12.0 — Diagnosa: simpan laporan

- **Simpan file** — tulis laporan ke `diagnosa-YYYYMMDD-HHmmss.txt` lewat dialog pilih lokasi Android (tanpa izin storage tambahan).
- **Riwayat laporan** — maks 20 terbaru, tersimpan di DataStore (bertahan setelah app ditutup, bisa dibuka walau tidak terhubung). Buka / Bagikan / Simpan file / Hapus per laporan.
- **Reset ukur** otomatis menyimpan laporan "Sebelum reset ukur" ke riwayat, jadi hasil tuning (`wsub_trim`, `WSUB_PKT_TX`, dll.) bisa dibandingkan dengan laporan sesudahnya.

## 3.11.0

- **Diagnosa** ditulis ulang: ringkasan masalah otomatis (aturan dari tabel README firmware, laju paket dihitung dari `pkt=` slave),
  fault chip 1/2 + riwayat sesi, info chip (`INFO`, `VER`, `CLIP`, `MOD`, `DAC`), statistik link nirkabel & Bluetooth
  (`WSUB`, `WSUBT`, `WSUBD`, `WSUBC`, `BTD`, `BTEV`), "Reset ukur" (delta counter), terminal, dan **Bagikan laporan**.
- **Tab DSP** baru: mixer 2×2 + mixmode + lebar stereo, profil filter (mode 0), clipper, modulasi PWM, DAC BTL/PBTL (dengan konfirmasi),
  gain analog, auto-standby, polaritas sub, simpan/hapus/reboot.
- **Preset** (sub-tab di DSP): preset bawaan (EQ + voicing sub), preset lengkap kini juga menyimpan clipper, polaritas sub, standby,
  trim, voicing sub, dan EQ center/sub; ekspor/impor JSON lewat clipboard.
- Home: level meter hidup (poll `lvl`/`lvl2` saat tab tampil). Log tidak lagi dibanjiri baris poll.
- Perbaikan: daftar perangkat ter-pair kosong saat pertama dijalankan; `cancelDiscovery()` tidak lagi menggagalkan koneksi bila izin SCAN ditolak;
  reader sesi lama tidak menandai sesi baru sebagai putus; peringatan voicing (slave < 2.9.19, gain peak > +4 dB tanpa limiter).
- Build: AGP 8.7.3, `gradlew` palsu dihapus (CI memakai `gradle` 8.9), tes unit di CI, rilis otomatis untuk tag `v*`.

