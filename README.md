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
