# Emoney Reader (Android)

Aplikasi Android untuk mencatat transaksi tol menggunakan tap kartu NFC, dengan
history, manajemen nama gerbang tol, dan export ke Excel per periode.

## ⚠️ Batasan Penting (WAJIB dibaca)

Kartu e-money bank di Indonesia (Flazz/BCA, Brizzi/BRI, e-toll/Mandiri,
TapCash/BNI, JakCard) menggunakan chip **Mifare DESFire/Classic dengan kunci
enkripsi rahasia** milik masing-masing bank. Kunci ini tidak pernah
dipublikasikan resmi, sehingga:

- **Saldo asli TIDAK BISA dibaca** oleh aplikasi pihak ketiga manapun
  (termasuk aplikasi ini), tanpa kerja sama resmi dengan bank penerbit.
- Yang bisa dibaca secara legal & teknis hanyalah **UID (nomor identitas unik)**
  kartu — cukup untuk keperluan pencatatan/pelacakan riwayat.

Karena itu aplikasi ini didesain dengan alur:
1. Tempel kartu ke HP → UID kartu otomatis terbaca & tersimpan sebagai referensi.
2. User input nominal transaksi secara manual (sesuai struk/layar EDC gerbang tol).
3. Data (gerbang tol, jam, nominal, UID kartu) tersimpan ke database lokal.

Jika ke depannya kamu butuh baca saldo asli, satu-satunya jalan resmi adalah
kerja sama API dengan bank penerbit kartu (biasanya untuk merchant/EDC resmi).

## Fitur

- ✅ **Scan struk tol otomatis pakai kamera (OCR on-device, offline)** — foto
  struk fisik berulang kali, nominal & nama gerbang tol otomatis terdeteksi
  dari teks di struk (Google ML Kit Text Recognition), user tinggal
  konfirmasi/koreksi, total otomatis terakumulasi. Tidak perlu hitung manual
  satu-satu.
- ✅ Deteksi tap kartu NFC (UID + jenis kartu) — opsional, untuk pencatatan manual
- ✅ Input & simpan transaksi (gerbang tol, nominal, waktu, foto struk) ke database lokal (Room/SQLite)
- ✅ Kelola nama gerbang tol (tambah/hapus)
- ✅ Lihat history dengan filter tanggal mulai–akhir
- ✅ Export ke file **.xlsx** asli (bisa dibuka di Excel/Google Sheets) — kolom
  Gerbang Tol, Jam, Nominal, **plus thumbnail foto struk ter-embed langsung di
  dalam sel** — sesuai periode yang dipilih
- ✅ Export ke **laporan PDF** — satu kartu per transaksi lengkap dengan foto
  struk ukuran penuh, cocok untuk lampiran bukti pengeluaran kerja

## Cara Build

1. Extract folder ini, buka dengan **Android Studio** (Hedgehog/Koala atau lebih baru).
2. Biarkan Gradle sync otomatis mengunduh dependency.
3. Sambungkan HP Android (yang punya NFC) via USB, aktifkan USB debugging.
4. Klik Run ▶️.
5. Pastikan NFC di HP dalam keadaan aktif.

## Cara Build Otomatis via GitHub Actions (tanpa install Android Studio)

Project ini sudah dilengkapi `.github/workflows/build.yml` yang otomatis build
APK di server GitHub setiap kamu push kode.

Langkah-langkah:

1. Buat repository baru di GitHub (public atau private, bebas).
2. Upload/push seluruh isi folder project ini ke repo tersebut, contoh:
   ```
   cd EmoneyReader
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/USERNAME/NAMA-REPO.git
   git push -u origin main
   ```
3. Buka tab **Actions** di halaman repo GitHub kamu. Workflow "Build Debug APK"
   akan otomatis jalan (butuh beberapa menit).
4. Setelah selesai (tanda centang hijau), klik hasil run tersebut → scroll ke
   bagian **Artifacts** → download `emoney-reader-debug-apk.zip`.
5. Extract zip itu, di dalamnya ada `app-debug.apk` — tinggal kirim/transfer
   ke HP Android dan install (perlu izin "Install dari sumber tidak dikenal").

Kalau tidak mau push manual tiap kali, kamu juga bisa trigger build kapan saja
lewat tab Actions → pilih workflow "Build Debug APK" → tombol **Run workflow**
(karena workflow ini juga diset `workflow_dispatch`).

> Catatan: APK yang dihasilkan adalah **debug build** (belum ditandatangani
> untuk rilis Play Store), tapi sudah bisa langsung diinstall & dipakai di HP.

## Struktur Proyek

```
app/src/main/java/com/emoneyreader/app/
├── MainActivity.kt          # Layar utama, handle event tap NFC
├── HistoryActivity.kt       # List history + filter periode + export Excel
├── TollGateActivity.kt      # CRUD nama gerbang tol
├── data/                    # Room database (Entity, DAO, Database)
├── adapter/                 # RecyclerView adapters
└── util/
    ├── NfcHelper.kt         # Baca UID & tipe kartu dari intent NFC
    └── ExportHelper.kt      # Generator file .xlsx murni (tanpa library eksternal)
```

## Kustomisasi Lanjutan (opsional, jika ingin dikembangkan)

- Tambah field "plat nomor kendaraan" di form transaksi.
- Tambah grafik pengeluaran bulanan (mis. pakai MPAndroidChart).
- Tambah backup/restore database ke Google Drive.
- Ganti ikon aplikasi (`android:icon` di AndroidManifest.xml saat ini pakai ikon sistem placeholder).
