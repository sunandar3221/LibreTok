# LibreTok - TikTok Video & Audio Downloader

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20(M3)-4285F4.svg)](https://developer.android.com/jetpack/compose)

Aplikasi Android native yang dirancang khusus sebagai alternatif modern dari **AhaTok Downloader**. Dibangun secara penuh menggunakan **Google Jetpack Compose (Material 3)**, arsitektur bersih, dan **100% GRATIS** tanpa iklan, tanpa batasan unduhan, dan tanpa watermark.

---

## 🌟 Fitur Utama

1. **Download Video Tanpa Watermark (No Watermark & Full HD)**
   - Mengunduh video TikTok bersih tanpa logo watermark TikTok.
   - Pilihan resolusi Standar maupun Full HD langsung dari server CDN.
2. **Ekstrak & Unduh Audio (MP3)**
   - Mengonversi dan mengunduh musik/suara TikTok secara terpisah ke dalam format MP3.
3. **Dukungan Unduh Slideshow / Foto**
   - Mendukung konten TikTok berupa album foto / gambar geser.
4. **Deteksi Link Otomatis dari Papan Klip (Clipboard Listener)**
   - Saat membuka aplikasi, LibreTok otomatis mendeteksi jika Anda menyalin tautan TikTok dan menawarkan tombol unduh instan satu ketukan.
5. **Dukungan Share Sheet Langsung dari Aplikasi TikTok**
   - Di aplikasi TikTok, cukup ketuk tombol **Bagikan (Share)** -> pilih **LibreTok** -> aplikasi langsung menganalisis dan menyiapkan opsi unduhan tanpa perlu menyalin tautan secara manual.
6. **Pemutar Media Bawaan (Built-in Media3 / ExoPlayer)**
   - Dilengkapi pemutar video & audio bawaan yang mulus untuk melihat pratinjau sebelum mengunduh atau memutar hasil unduhan langsung di dalam aplikasi.
7. **Pengelola Unduhan & Galeri Lokal (Downloads Manager)**
   - Tab khusus untuk melihat riwayat unduhan dengan filter: *Semua*, *Video*, *Audio MP3*, dan *Foto*.
   - Tombol cepat untuk membagikan berkas ke WhatsApp, Telegram, Instagram, atau membuka di pemutar bawaan perangkat.
8. **Penyimpanan Terintegrasi MediaStore (Android 10 - 14+)**
   - Berkas otomatis tersimpan di folder publik perangkat:
     - Video: `Movies/LibreTok/`
     - Audio: `Music/LibreTok/`
     - Foto: `Pictures/LibreTok/`
   - Berkas langsung muncul di Galeri HP, Google Photos, dan pemutar musik perangkat.
9. **100% Gratis & Bebas Iklan**
   - Tidak ada iklan banner, tidak ada video iklan mengganggu, tidak ada sistem langganan atau pembelian dalam aplikasi.

---

## 📱 Teknologi & Arsitektur

- **Bahasa:** Kotlin 1.9.23
- **UI Framework:** Google Jetpack Compose dengan Material Design 3 (M3)
- **Networking:** OkHttp 4.12.0 & Gson
- **Image Loading:** Coil Compose
- **Media Playback:** AndroidX Media3 (ExoPlayer 1.3.1)
- **Architecture:** MVVM (Model-View-ViewModel) dengan StateFlow & Coroutines
- **Optimasi:** R8 / ProGuard Minification & Resource Shrinking (Smooth 60/120 FPS)

---

## 📦 Unduh APK Rilis

Unduh APK resmi yang telah dioptimasi dan ditandatangani (*Signed Release APK*) langsung melalui halaman **[Releases](../../releases)**.

---

## 🚀 Cara Menjalankan & Membangun

Untuk mengkompilasi APK Release (Signed):
```bash
./gradlew assembleRelease
```

Untuk mengkompilasi Debug:
```bash
./gradlew assembleDebug
```

Untuk memasang langsung ke perangkat Android via ADB:
```bash
./gradlew installDebug
```

---

## 📄 Lisensi

Proyek ini dilisensikan di bawah **GNU General Public License v3.0 (GPLv3)**. Lihat berkas [LICENSE](LICENSE) untuk informasi lebih lanjut.
