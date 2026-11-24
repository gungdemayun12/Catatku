# CatatKu — Gambaran Umum Aplikasi

Dokumen ini berisi deskripsi aplikasi CatatKu untuk keperluan portofolio.
Semua angka diambil langsung dari kode sumber proyek, bukan perkiraan.

---

## 1. Ringkasan satu paragraf (untuk kartu portofolio)

**CatatKu** adalah aplikasi Android untuk mencatat aktivitas harian dan keuangan
pribadi dalam satu tempat. Aplikasi ini menggabungkan **To-Do List** (tugas,
tenggat, pengingat, kalender, statistik) dengan **pencatatan keuangan** (dompet,
transaksi, anggaran, tagihan berulang, target tabungan, utang-piutang). Semua data
tersinkronisasi real-time ke **Firebase Firestore** dengan isolasi data per
pengguna, dan aplikasi mendukung **dua bahasa (Indonesia & Inggris)** serta mode
terang/gelap. Dibangun dengan **Java native Android** dan arsitektur
Repository–ViewModel–LiveData.

---

## 2. Spesifikasi teknis

| Aspek | Detail |
| --- | --- |
| Platform | Android native (Java) |
| Bahasa | Java — 105 file, ±20.700 baris kode |
| Layout | 63 file XML |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 16 (API 36) |
| Backend | Firebase Firestore + Firebase Auth + Analytics |
| Arsitektur | Repository pattern, ViewModel + LiveData (MVVM) |
| UI | Material Design 3, custom chart views |
| Lokalisasi | 960 string × 2 bahasa (Indonesia & Inggris) |
| Kualitas | 84 unit test pada 13 kelas, 0 gagal |
| Output | APK release signed, 11,36 MB |

---

## 3. Fitur Utama — Modul To-Do List

### 3.1 Manajemen tugas
- Judul, deskripsi, kategori, prioritas (Rendah/Sedang/Tinggi)
- Tenggat waktu + jam, estimasi durasi
- Status berjenjang, terkunci otomatis saat selesai (mencegah perubahan
  tidak sengaja)

### 3.2 Pencarian dan 10 filter
- Pencarian teks judul dan catatan
- Filter status: Semua, Hari ini, Mendatang, Terlambat, Selesai
- Filter pintar: Fokus hari ini, Berbintang, Mengendap, Cepat selesai,
  Punya langkah
- Filter tambahan: kategori, prioritas, dan rentang tanggal kustom

### 3.3 Kalender
- Tampilan bulanan dengan penanda pada tanggal yang memiliki tugas
- Memilih tanggal menampilkan daftar tugas hari itu

### 3.4 Statistik
- Tingkat penyelesaian (completion rate) bulanan
- Grafik tren penyelesaian
- Distribusi tugas terbuka per kategori

### 3.5 Pengingat (reminder)
- Notifikasi terjadwal per tugas
- Tetap berfungsi setelah perangkat di-reboot (BootReceiver)
- Jam pengingat default yang dapat diatur
- Validasi: pengingat harus sebelum tenggat

### 3.6 Tugas berulang
- Salinan tugas berikutnya dibuat otomatis sesuai pola pengulangan

### 3.7 Quick Capture
- Tekan lama tombol FAB untuk mencatat tugas cepat tanpa membuka form penuh

### 3.8 Subtask / langkah tugas
- Memecah satu tugas menjadi beberapa langkah kecil
- Progres ditampilkan sebagai rasio (mis. "2 of 4 done")
- Saat seluruh subtask selesai, aplikasi menawarkan untuk menandai tugas
  utama sebagai selesai
- Filter "Punya langkah" untuk menampilkan tugas yang memiliki subtask

### 3.9 Log aktivitas
- Riwayat perubahan tugas tetap tersimpan meskipun tugas dihapus

### 3.10 Pengaturan To-Do
- Kategori kustom dengan warna dan ikon
- Prioritas default dan kategori default untuk tugas baru
- Jam pengingat default
- Urutan tampilan (sort) dan pengelompokan daftar
- Tampilkan/sembunyikan tugas yang sudah selesai
- Riwayat aktivitas dan pengaturan notifikasi modul To-Do

---

## 4. Fitur Utama — Modul Keuangan

### 4.1 Dompet (multi-wallet)
- Jenis: Tunai, Bank, E-wallet
- Warna kustom dan saldo awal
- Multi-currency dengan kurs yang dapat diambil otomatis dari internet
- Validasi: dompet yang sudah memiliki transaksi tidak dapat dihapus,
  pengguna diarahkan untuk mengganti nama saja

### 4.2 Transaksi
- Pemasukan dan pengeluaran dengan kategori berwarna dan ikon
- Pengelompokan per tanggal dengan Net per hari
- Pencarian dan filter: jenis, kategori, dompet, rentang tanggal, nominal,
  serta urutan

### 4.3 Transfer antar dompet
- Dicatat sebagai sepasang transaksi (keluar dan masuk) yang terhubung
  lewat `transferGroup`
- Saldo total tidak berubah — desain yang mencegah kesalahan hitung paling
  umum pada aplikasi keuangan
- Otomatis dikecualikan dari total pengeluaran bulanan

### 4.4 Anggaran (budget)
- Batas bulanan per kategori
- Progress bar dan peringatan saat anggaran terlampaui

### 4.5 Tagihan berulang (recurring bills)
- Penjadwalan otomatis dengan `repeatAnchorDay`
- Notifikasi pengingat pembayaran

### 4.6 Target tabungan (savings goals)
- Target nominal dengan progres setoran
- Halaman detail per target

### 4.7 Utang dan piutang (debts)
- Membedakan "saya berutang" dan "diberikan ke saya"
- Pencatatan pembayaran bertahap
- Status: Belum lunas, Lunas, Terlambat
- Ringkasan posisi keuangan bersih

### 4.8 Laporan dan ekspor
- Donut chart per kategori dan grafik tren
- Ekspor CSV (spreadsheet) dan PDF (siap cetak)
- Pilihan cakupan: bulan ini, semua transaksi, atau rentang tanggal kustom
- Berbagi langsung melalui lembar berbagi Android

### 4.9 Kurs mata uang (exchange rates)
- Mengatur nilai tukar tiap mata uang terhadap mata uang utama
- Pengambilan kurs langsung dari internet dengan satu ketukan
- Kurs hanya dipakai untuk total dan laporan; transaksi tersimpan tetap
  memakai kursnya sendiri
- Setiap kurs menampilkan waktu pembaruan terakhir

### 4.10 Kategori keuangan
- Kategori kustom dengan ikon, warna, dan jenis (pemasukan/pengeluaran)

---

## 5. Fitur lintas modul

| Fitur | Keterangan |
| --- | --- |
| Autentikasi | Login, registrasi, lupa password (kirim tautan reset), Google Sign-In |
| Sesi persisten | Bertahan setelah aplikasi ditutup atau perangkat di-reboot; hanya logout manual yang mengakhiri sesi |
| Keamanan data | Firestore Rules dengan `isOwner()` — setiap pengguna hanya dapat mengakses datanya sendiri |
| Hapus akun | Menghapus akun beserta seluruh data di cloud, dengan re-autentikasi (password/Google) dan konfirmasi mengetik `DELETE` |
| Dua bahasa | Indonesia (default) dan Inggris, 960 string per bahasa |
| Tema | Mode terang dan gelap |
| Aksesibilitas | Tata letak menyesuaikan ukuran font sistem hingga 150%; haptic feedback pada aksi geser |
| Notifikasi | Pengaturan notifikasi terpusat, permintaan izin notifikasi, dan deteksi izin exact alarm (agar pengingat tepat waktu) |
| Halaman utama | Home dengan ringkasan harian, kutipan motivasi, dan pemilihan modul |
| Tentang aplikasi | Info versi, developer, dan fitur bagikan aplikasi ke pengguna lain |
| Logout | Konfirmasi sebelum keluar akun |

---

## 6. Kualitas dan pengujian

- **84 unit test** pada 13 kelas, mencakup kalkulasi transfer, penjadwalan
  tagihan, logika tugas berulang, validasi autentikasi, konversi mata uang,
  dan ekspor laporan
- **Pengujian perangkat**: seluruh fitur diverifikasi manual pada emulator,
  baik pada ukuran font 100% maupun 150%
- **Bebas crash**: 0 crash, 0 ANR, dan 0 error tulis Firebase pada pengujian akhir

## 6.1 Daftar komponen aplikasi (26 komponen)

Bukti cakupan fitur, diambil dari `AndroidManifest.xml`:

**Autentikasi dan awal**
`SplashActivity` · `LoginActivity` · `RegisterActivity` · `ForgotPasswordActivity`

**Utama**
`HomeActivity` · `CatatKuApp`

**To-Do**
`TodoActivity` · `TodoFormActivity` · `TodoDetailActivity` · `CategoriesActivity` ·
`ActivityLogActivity`

**Keuangan**
`FinanceActivity` · `TransactionFormActivity` · `WalletsActivity` ·
`FinanceCategoriesActivity` · `BillsActivity` · `SavingsGoalsActivity` ·
`SavingsGoalDetailActivity` · `DebtsActivity` · `ExchangeRatesActivity`

**Pengaturan**
`SettingsActivity` · `NotificationSettingsActivity` · `AboutActivity`

**Notifikasi (receiver)**
`ReminderReceiver` · `NotificationActionReceiver` · `BootReceiver`

---

## 7. Aspek teknis yang layak ditonjolkan

Bagian ini membedakan proyek ini dari aplikasi CRUD biasa.

1. **Desain transfer anti-bug.** Transfer dicatat sebagai dua sisi transaksi
   yang saling meniadakan, sehingga total saldo mustahil melenceng. Ini adalah
   keputusan desain data, bukan sekadar tampilan.
2. **Logika tanggal berulang yang tepat.** Penanganan `repeatAnchorDay` membuat
   tagihan tanggal 31 tetap benar pada bulan yang hanya memiliki 30 hari.
3. **Aksesibilitas ukuran font besar.** Tata letak menyesuaikan diri hingga
   ukuran font 150%, termasuk penghitungan ulang ruang untuk tombol mengambang
   (FAB) agar tidak menutupi konten.
4. **Pemisahan lapisan yang rapi.** Repository, ViewModel, dan LiveData dengan
   105 file yang terstruktur per modul.
5. **Keamanan berlapis.** Firestore Rules per pengguna, kredensial signing
   dipisahkan dari repositori, dan tersedia dokumentasi keamanan (`SECURITY.md`).

---

## 8. Versi ringkas untuk kartu portofolio

### Judul

**CatatKu — Aplikasi Android Pencatatan Tugas & Keuangan Pribadi**

### Deskripsi (pilih salah satu)

**Versi pendek (1 kalimat):**
> Aplikasi Android native (Java + Firebase) yang menggabungkan manajemen tugas
> dan pencatatan keuangan pribadi dalam satu tempat.

**Versi sedang (2–3 kalimat):**
> CatatKu adalah aplikasi Android untuk mengelola tugas harian sekaligus mencatat
> keuangan pribadi. Dibangun dengan Java dan Firebase Firestore, aplikasi ini
> mendukung multi-dompet, multi-mata uang, anggaran, tagihan berulang, target
> tabungan, serta ekspor laporan CSV dan PDF. Seluruh data tersinkronisasi
> real-time dengan isolasi data per pengguna.

### Poin fitur (untuk bullet list)

- Modul ganda: To-Do List dan Keuangan dalam satu aplikasi dengan autentikasi terpadu
- Sinkronisasi cloud real-time dengan isolasi data per pengguna
- Fitur keuangan lanjutan: transfer antar dompet, multi-mata uang dengan kurs
  otomatis, anggaran, tagihan berulang, target tabungan, serta utang dan piutang
- Subtask dengan progres dan penawaran penyelesaian otomatis
- Dukungan dua bahasa (Indonesia dan Inggris) dengan 960 string per bahasa
- Ekspor laporan ke CSV dan PDF
- Kualitas terjaga: 84 unit test, diuji pada ukuran font 100% dan 150%

### Tech stack (untuk tag/badge)

`Java` · `Android Native` · `Firebase Firestore` · `Firebase Auth` ·
`MVVM` · `ViewModel & LiveData` · `Material Design 3` · `Repository Pattern`

---

## 9. Catatan kredibilitas

Agar portofolio tetap kredibel, hindari klaim berikut karena tidak sesuai
kondisi aplikasi saat ini:

- Jangan menyebut aplikasi sudah rilis di Play Store — APK didistribusikan
  langsung (sideload)
- Jangan menyebut jumlah pengguna apabila belum ada pengguna riil
- Jangan menyebut R8/ProGuard aktif — saat ini `isMinifyEnabled = false`

Yang **boleh** dan **akurat** untuk diklaim:

- Aplikasi berjalan dan telah diuji menyeluruh pada perangkat
- APK release sudah ditandatangani (signed) dan siap distribusi
- Seluruh fitur pada dokumen ini benar-benar ada di dalam kode

---

## 10. English version (if the portfolio is in English)

### Short description

> **CatatKu** is a native Android app that combines daily task management with
> personal finance tracking in a single place. It pairs a To-Do module (tasks,
> deadlines, reminders, calendar, statistics) with a finance module (wallets,
> transactions, budgets, recurring bills, savings goals, debts). All data syncs
> in real time to Firebase Firestore with per-user data isolation, and the app
> ships fully localised in Indonesian and English with light and dark themes.
> Built in Java using a Repository–ViewModel–LiveData architecture.

### Key features

- Dual modules: To-Do List and Finance behind a single authentication flow
- Real-time cloud sync with per-user data isolation via Firestore security rules
- Advanced finance: wallet-to-wallet transfers, multi-currency with live exchange
  rates, budgets, recurring bills, savings goals, debts and receivables
- Subtasks with progress tracking and an automatic completion prompt
- Bilingual UI (Indonesian and English), 960 strings per language
- CSV and PDF report export with custom date ranges
- Account deletion with re-authentication and typed confirmation
- 84 unit tests; verified at both 100% and 150% system font sizes

### Technical highlights

- **Transfer design that cannot corrupt totals.** A transfer is stored as two
  linked transaction legs that cancel each other out, so total balance can never
  drift. This is a data-modelling decision, not a UI trick.
- **Correct recurring dates.** `repeatAnchorDay` keeps a bill anchored to the
  31st even in months with 30 days.
- **Large-font accessibility.** Layouts adapt up to 150% font scale, including
  recalculating space reserved for the floating action button.
- **Layered architecture.** Repository, ViewModel and LiveData across 105 files
  organised by feature module.
- **Defence in depth.** Per-user Firestore rules, signing credentials kept out of
  version control, and a dedicated `SECURITY.md`.

### One-line pitch

> A native Android app (Java + Firebase) that unifies task management and
> personal finance tracking.
