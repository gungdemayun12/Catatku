# CatatKu — Teks Siap Salin untuk Portofolio

File ini berisi teks jadi. Tinggal blok mana yang cocok untuk kartu portofolio,
lalu salin. Versi lengkap ada di `PORTOFOLIO.md`.

---

## A. Kartu portofolio — versi pendek

### Judul

```
CatatKu — Aplikasi Android Pencatatan Tugas & Keuangan Pribadi
```

### Deskripsi

```
Aplikasi Android native (Java + Firebase) yang menggabungkan manajemen tugas
dan pencatatan keuangan pribadi dalam satu tempat.
```

### Tag teknologi

```
Java · Android Native · Firebase Firestore · Firebase Auth · MVVM · Material Design 3
```

---

## B. Kartu portofolio — versi sedang (rekomendasi)

### Judul

```
CatatKu — Aplikasi Android Pencatatan Tugas & Keuangan Pribadi
```

### Deskripsi

```
CatatKu adalah aplikasi Android untuk mengelola tugas harian sekaligus mencatat
keuangan pribadi. Dibangun dengan Java dan Firebase Firestore, aplikasi ini
mendukung subtask, multi-dompet, multi-mata uang dengan kurs otomatis, anggaran,
tagihan berulang, target tabungan, serta ekspor laporan CSV dan PDF. Seluruh data
tersinkronisasi real-time dengan isolasi data per pengguna di tingkat aturan
database.
```

### Poin fitur

```
- Modul ganda: To-Do List dan Keuangan dalam satu aplikasi dengan autentikasi terpadu
- Sinkronisasi cloud real-time dengan isolasi data per pengguna
- Fitur keuangan lanjutan: transfer antar dompet, multi-mata uang dengan kurs otomatis,
  anggaran, tagihan berulang, target tabungan, serta utang dan piutang
- Subtask dengan progres dan penawaran penyelesaian otomatis
- Dukungan dua bahasa (Indonesia dan Inggris) dengan 960 string per bahasa
- Ekspor laporan ke CSV dan PDF dengan rentang tanggal kustom
- Kualitas terjaga: 84 unit test, diuji pada ukuran font 100% dan 150%
```

---

## C. Studi kasus teknis (untuk halaman detail portofolio)

### Tantangan 1 — Transfer antar dompet yang tidak merusak total saldo

```
Masalah: mencatat transfer sebagai satu transaksi membuat total saldo melenceng.

Solusi: transfer disimpan sebagai dua sisi transaksi yang saling meniadakan
dan dihubungkan lewat transferGroup. Total saldo tidak berubah, dan kedua sisi
otomatis dikecualikan dari perhitungan pengeluaran bulanan.
```

### Tantangan 2 — Tagihan berulang pada bulan dengan hari lebih sedikit

```
Masalah: tagihan tanggal 31 hilang pada bulan yang hanya punya 30 hari.

Solusi: pola pengulangan disimpan dengan repeatAnchorDay sehingga tagihan tetap
terikat pada tanggal 31 dan tidak bergeser ke tanggal 1 bulan berikutnya.
```

### Tantangan 3 — Tata letak pada ukuran font besar

```
Masalah: pada font 150%, tombol mengambang menutupi konten paling bawah.

Solusi: ruang dihitung ulang saat runtime dari tinggi tombol yang benar-benar
terukur, bukan dari nilai dp tetap. Diverifikasi pada font 100% dan 150%.
```

---

## D. Tech stack

```
Bahasa          : Java
Platform        : Android Native
Backend         : Firebase Firestore, Firebase Auth, Firebase Analytics
Arsitektur      : Repository Pattern, ViewModel, LiveData (MVVM)
UI              : Material Design 3, custom chart views
Lokalisasi      : 960 string × 2 bahasa (Indonesia & Inggris)
Pengujian       : 84 unit test (JUnit) pada 13 kelas
Skala kode      : 105 file Java (±20.700 baris), 63 layout XML, 26 komponen
Kompatibilitas  : Android 8.0 (API 26) hingga Android 16 (API 36)
```

---

## D2. Daftar fitur lengkap (bila portofolio butuh detail penuh)

### To-Do List

```
- Manajemen tugas: judul, deskripsi, kategori, prioritas, tenggat + jam, estimasi
- Subtask dengan progres dan penawaran penyelesaian otomatis
- Pencarian dan 10 filter: Semua, Hari ini, Mendatang, Terlambat, Selesai,
  Fokus hari ini, Berbintang, Mengendap, Cepat selesai, Punya langkah
- Filter kategori, prioritas, dan rentang tanggal kustom
- Kalender bulanan dengan penanda tanggal berisi tugas
- Statistik: completion rate, grafik tren, distribusi per kategori
- Pengingat terjadwal yang tahan reboot
- Tugas berulang dengan salinan otomatis
- Quick Capture via tekan lama tombol FAB
- Log aktivitas yang tetap tersimpan meski tugas dihapus
- Pengaturan: kategori kustom, prioritas default, kategori default,
  jam pengingat default, sort, grouping, tampilkan tugas selesai
```

### Keuangan

```
- Dompet: Tunai/Bank/E-wallet, warna kustom, saldo awal, multi-mata uang
- Transaksi: pemasukan/pengeluaran, kategori berwarna + ikon, Net per hari
- Transfer antar dompet sebagai sepasang transaksi yang saling meniadakan
- Anggaran bulanan per kategori dengan progress bar dan peringatan
- Tagihan berulang dengan repeatAnchorDay dan notifikasi
- Target tabungan dengan progres setoran
- Utang dan piutang dengan pembayaran bertahap dan status
- Laporan: donut chart, grafik tren, ekspor CSV dan PDF
- Kurs mata uang dengan pengambilan otomatis dari internet
- Kategori keuangan kustom dengan ikon, warna, dan jenis
```

### Lintas modul

```
- Login, registrasi, lupa password, Google Sign-In
- Sesi persisten hingga logout manual
- Hapus akun dengan re-autentikasi dan konfirmasi ketik DELETE
- Firestore Rules isOwner() per pengguna
- Dua bahasa (Indonesia & Inggris), 960 string per bahasa
- Tema terang dan gelap
- Aksesibilitas font hingga 150% dan haptic feedback
- Pengaturan notifikasi dan izin exact alarm
- Tentang aplikasi dan bagikan aplikasi
```

---

## E. English version

### Title

```
CatatKu — Native Android App for Tasks and Personal Finance
```

### Description

```
CatatKu is a native Android app that combines daily task management with personal
finance tracking in a single place. Built in Java with Firebase Firestore, it pairs
a To-Do module (tasks, deadlines, reminders, calendar, statistics) with a finance
module (wallets, transactions, budgets, recurring bills, savings goals, debts).
All data syncs in real time with per-user isolation enforced at the database rule
level. Fully localised in Indonesian and English, with CSV and PDF report export.
```

### Key features

```
- Dual modules: To-Do List and Finance behind a single authentication flow
- Real-time cloud sync with per-user data isolation via Firestore security rules
- Advanced finance: wallet transfers, multi-currency with live exchange rates,
  budgets, recurring bills, savings goals, debts and receivables
- Subtasks with progress tracking and an automatic completion prompt
- Bilingual UI (Indonesian and English), 960 strings per language
- CSV and PDF report export with custom date ranges
- Account deletion with re-authentication and typed confirmation
- 84 unit tests; verified at both 100% and 150% system font sizes
```

### One-line pitch

```
A native Android app (Java + Firebase) that unifies task management and personal
finance tracking.
```

---

## F. Catatan kredibilitas

Hindari klaim berikut karena tidak sesuai kondisi aplikasi saat ini:

```
- Menyebut sudah rilis di Play Store (APK didistribusikan langsung / sideload)
- Menyebut jumlah pengguna bila belum ada pengguna riil
- Menyebut R8/ProGuard aktif (saat ini isMinifyEnabled = false)
```

Yang akurat dan boleh diklaim:

```
- Aplikasi berjalan dan telah diuji menyeluruh pada perangkat
- APK release sudah ditandatangani dan siap distribusi
- Seluruh fitur di dokumen ini benar-benar ada di dalam kode
```
