# Keamanan

Catatan ini menjelaskan alert *secret scanning* yang dikirim GitHub dan langkah
yang perlu diambil. Ditulis agar bisa diikuti tanpa latar belakang keamanan.

## Ringkasan alert

GitHub menemukan **Google API key** di `app/google-services.json` pada commit
`d591e97` ("Initial commit").

| Item | Status |
| --- | --- |
| Google API key (Firebase) | Terbuka di riwayat Git — **perlu dibatasi**, lihat langkah 1 |
| Password keystore release | **Tidak bocor.** Dibaca dari `keystore.properties` / env var sejak commit pertama |
| File `catatku-release.jks` | **Tidak pernah ter-commit** |
| Data user di Firestore | **Aman.** Dilindungi `isOwner()` di `firestore.rules` |

### Mengapa API key Firebase tidak fatal

API key Firebase dipakai untuk mengidentifikasi *project*, bukan untuk
mengotorisasi user. Key ini selalu ikut di dalam APK, jadi siapa pun yang
memasang aplikasi Anda bisa mengekstraknya. Menyembunyikannya di repo tidak
menambah keamanan berarti.

Risiko nyatanya terbatas pada **penyalahgunaan kuota** (mis. spam pembuatan akun
lewat Firebase Auth), bukan pada kebocoran data.

Perlindungan data yang sesungguhnya ada di `firestore.rules`: setiap user hanya
dapat mengakses dokumen miliknya sendiri.

## Langkah yang perlu dilakukan

### 1. Batasi API key (wajib)

1. Buka <https://console.cloud.google.com/apis/credentials>
2. Pilih project **aplikasi-pencatatan-7a268**
3. Klik API key yang dipakai aplikasi ini (yang ada di `google-services.json`,
   kolom `current_key`)
4. Bagian **Application restrictions** → pilih **Android apps**
5. Klik **Add an item** dan isi:
   - **Package name**: `com.example.pencatatan`
   - **SHA-1 certificate fingerprint**:
     `fe8979ec8469edc525687dfae4212a82f37a94fd`
6. **Save**, lalu tunggu sekitar 5 menit sampai berlaku

Setelah ini, API key hanya berfungsi bila dipanggil dari APK yang ditandatangani
keystore resmi. Key yang beredar di GitHub menjadi tidak berguna.

> SHA-1 di atas adalah sidik jari sertifikat release `catatku-release.jks`.
> Periksa ulang dengan:
> `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`

### 2. Aktifkan App Check (disarankan)

Firebase Console → **App Check** → daftarkan aplikasi Android dengan penyedia
**Play Integrity**. Ini memastikan hanya build aplikasi Anda yang dapat memanggil
Firestore dan Auth.

### 3. Tandai alert sebagai "Revoked"

Setelah langkah 1 selesai, buka alert di
`https://github.com/gungdemayun12/Catatku/security/secret-scanning`
lalu pilih **Revoke secret**.

Jangan tandai *Revoked* sebelum langkah 1 dikerjakan: selama key belum dibatasi,
key tersebut masih aktif.

## Mengapa riwayat Git tidak ditulis ulang

Commit `d591e97` masih dapat diakses lewat URL langsung meskipun sudah tidak ada
di branch `main`, karena Git menyimpan seluruh riwayat.

Menghapusnya memerlukan *force-push* yang menulis ulang riwayat. Cara itu
berisiko merusak repo, memutus semua clone dan fork, dan tetap tidak menjamin
salinan lain ikut hilang. Manfaat nyatanya kecil dibanding membatasi API key,
yang menjadikan key tersebut tidak berguna sama sekali.

Karena itu langkah yang dipilih adalah **membatasi key**, bukan menulis ulang
riwayat.

## Hal yang tidak boleh di-commit

| Berkas | Alasan |
| --- | --- |
| `catatku-release.jks`, `*.keystore` | Kunci signing. Bila bocor, orang lain dapat merilis update APK atas nama aplikasi ini, dan Anda kehilangan kendali atas aplikasi klien Anda |
| `keystore.properties` | Berisi password keystore |
| `app/google-services.json` | Memicu alert secret scanning |
| `local.properties` | Berisi path SDK lokal, spesifik per mesin |
| `*.apk`, `*.aab` | Berkas build, tidak perlu dilacak |

Semuanya sudah tercantum di `.gitignore`.

Jika salah satunya terlanjur ter-commit: **rotasi dulu** (ganti password atau
buat API key baru), baru bersihkan riwayat. Menghapus tanpa merotasi tidak
mengubah apa pun karena nilainya sudah terekspos.

## Kehilangan keystore

`catatku-release.jks` tidak dapat dibuat ulang. Bila hilang:

- Anda tidak dapat merilis update untuk klien yang sudah memasang APK ini
- Klien harus meng-uninstall versi lama (data lokal terhapus) sebelum memasang
  versi baru

Simpan di dua tempat terpisah (mis. password manager dan backup offline), dan
jangan pernah mengirimkannya lewat chat atau email.
