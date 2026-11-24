# CatatKu

Aplikasi pencatatan pribadi (todos, transaksi, dompet, anggaran, tagihan,
tabungan, utang) untuk Android, dengan sinkronisasi Firebase Firestore.

## Setup lokal

Proyek ini sengaja **tidak** menyertakan dua file konfigurasi rahasia. Keduanya
perlu dibuat sendiri di mesin lokal dan tidak boleh di-commit.

### 1. `app/google-services.json` (Firebase)

Berkas ini berisi Google API key, sehingga di-ignore oleh git dan akan
memunculkan alert bila di-commit ke repo publik.

Ambil dari Firebase Console:

1. Buka <https://console.firebase.google.com/> → project `aplikasi-pencatatan-7a268`.
2. Project settings → tab *General* → *Your apps* → pilih app Android
   `com.example.pencatatan`.
3. Download `google-services.json`, lalu letakkan di `app/google-services.json`.

Tanpa berkas ini build akan gagal pada plugin `com.google.gms.google-services`.

### 2. `keystore.properties` (signing release)

Kredensial keystore release dibaca dari berkas ini (di-ignore), atau dari
environment variable berikut untuk CI:

| Environment variable      | Property        |
| ------------------------- | --------------- |
| `CATATKU_STORE_PASSWORD`  | `storePassword` |
| `CATATKU_KEY_ALIAS`       | `keyAlias`      |
| `CATATKU_KEY_PASSWORD`    | `keyPassword`   |

Contoh `keystore.properties` di root proyek:

```properties
storePassword=...
keyAlias=catatku
keyPassword=...
```

Berkas keystore-nya sendiri (`catatku-release.jks`) juga tidak di-commit.
Bila keystore tidak ditemukan, build release tetap berhasil tetapi menghasilkan
APK unsigned.

## Build

```bash
./gradlew :app:assembleDebug    # debug
./gradlew :app:assembleRelease  # release (butuh keystore)
```

## Firestore

Aturan keamanan ada di `firestore.rules`. Deploy dengan:

```bash
firebase deploy --only firestore:rules
```

## Keamanan

### Google API key bukan rahasia aplikasi

Google API key pada `google-services.json` **memang ikut terpaket di dalam APK**.
Siapa pun yang mengunduh APK bisa mengekstraknya, jadi memperlakukannya sebagai
rahasia tidak memberi perlindungan nyata. Berkas itu tetap di-ignore di sini
supaya repo publik tidak memicu alert *secret scanning* berulang, bukan karena
isinya bisa dijaga.

Karena itu, yang **benar-benar** melindungi data adalah dua hal berikut.

### 1. Batasi API key (wajib, sekali saja)

Di [Google Cloud Console](https://console.cloud.google.com/apis/credentials) →
pilih API key project `aplikasi-pencatatan-7a268` → *Application restrictions* →
**Android apps** → tambahkan:

- Package name: `com.example.pencatatan`
- SHA-1 signing certificate: `fe8979ec8469edc525687dfae4212a82f37a94fd`

Setelah dibatasi, key tersebut hanya berfungsi dari APK bertanda tangan resmi.
Key yang sudah terlanjur beredar menjadi tidak berguna. **Langkah inilah yang
menuntaskan alert secret scanning** — menghapus file dari commit terbaru saja
tidak cukup, karena riwayat Git menyimpan commit lama.

### 2. Firestore Rules (pertahanan utama)

`firestore.rules` memakai `isOwner()` sehingga setiap user hanya bisa membaca
dan menulis dokumennya sendiri. Bahkan dengan API key di tangan, penyerang tidak
dapat mengakses data user lain. Jangan pernah melonggarkan aturan ini menjadi
`allow read, write: if true`.

Disarankan juga mengaktifkan **Firebase App Check** (Play Integrity) agar hanya
build aplikasi Anda yang dapat memanggil Firestore.

### Jangan pernah commit

- `catatku-release.jks` / `*.keystore` — kunci signing; bila bocor, orang lain
  bisa membuat update APK atas nama aplikasi ini.
- `keystore.properties` — berisi password keystore.
- `app/google-services.json` — memicu alert secret scanning.

Bila salah satunya terlanjur ter-commit, **rotasi dulu** (ganti password/API key),
baru hapus dari riwayat. Menghapus tanpa merotasi tidak memperbaiki apa pun.
