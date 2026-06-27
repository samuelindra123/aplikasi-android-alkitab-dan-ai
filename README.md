# 📖 Alkitab AI Companion
> **Asisten Teologi, Tafsir Spiritual, dan Pembaca Alkitab Cerdas Berbasis AI**

[![Platform Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Database Room](https://img.shields.io/badge/Database-Room_SQLite-005C97?style=flat-square)](https://developer.android.com/training/data-storage/room)
[![API Gemini](https://img.shields.io/badge/AI-Gemini_3.5_Flash-F4B400?style=flat-square&logo=google&logoColor=white)](https://ai.google.dev)

**Alkitab AI** adalah aplikasi Android modern yang menggabungkan keindahan firman Tuhan dengan kecerdasan buatan (AI) tercanggih. Didesain secara eksklusif menggunakan **Jetpack Compose** dan **Material Design 3**, aplikasi ini tidak hanya berfungsi sebagai pembaca Alkitab interaktif, tetapi juga sebagai teman renungan, konselor spiritual, dan asisten studi teologi Kristen Protestan yang mendalam.

---

## ✨ Fitur Unggulan

### 🤖 1. Asisten AI Alkitab & Konseling Spiritual
*   Tanyakan apa saja seputar teologi Kristen, sejarah gereja, tafsir ayat, atau ajukan keluh kesah Anda untuk mendapatkan bimbingan rohani Kristen Protestan yang ramah, bijaksana, dan penuh kasih.
*   **Dual-Engine AI**: Mengintegrasikan API Gemini dan OpenRouter secara mulus dengan sistem *fail-safe automatic fallback*.
*   **Optimalisasi Kecepatan**: Respon AI dirancang sangat ringkas (maksimal 2-3 paragraf) dan dikirim dengan token terukur agar jawaban muncul seketika di layar Anda tanpa loading lama.
*   **Friendly Error Handling**: Apabila kuota API gratis mengalami limitasi jaringan, aplikasi akan menampilkan pesan ramah bahasa Indonesia yang menenangkan (bukan pesan eror teknis yang membingungkan).

### 📖 2. Pembaca Alkitab Interaktif & Offline (Terjemahan Baru)
*   Sistem pembaca Alkitab modern dengan navigasi kitab dan pasal yang sangat mulus.
*   **Kustomisasi Ayat**: Dukungan untuk menandai ayat dengan warna-warni (**Highlight**), menyimpan sebagai **Bookmark**, dan menulis catatan refleksi pribadi (**Journaling Notes**).
*   **Navigasi Presisi**: Klik referensi pencarian, bookmark, atau catatan, dan aplikasi akan mengarahkan Anda langsung ke ayat spesifik di dalam kitab tersebut secara konsisten (menyimpan riwayat bacaan terakhir Anda tanpa kembali ke setelan default).

### ☀️ 3. Ayat Hari Ini (Verse of the Day) - Berubah Setiap Jam 3 Pagi
*   Fitur yang memotivasi Anda setiap hari dengan firman Tuhan yang menyejukkan.
*   **Algoritma Tanggal Dinamis**: Ayat berganti secara otomatis mengikuti pergantian hari per **pukul 03.00 AM**. Pergantian ini konsisten dan bertahan meskipun Anda keluar masuk aplikasi, memberikan pengalaman spiritual yang selalu baru setiap pagi sebelum memulai aktivitas Anda.

---

## 📲 Unduh Aplikasi (Android APK)

Kami telah menyediakan file instalasi siap pakai dalam repository ini! Anda dapat langsung mengunduh dan memasangnya di perangkat Android Anda:

💾 **[Unduh Alkitab-AI.apk Direktori APK](./apk_output/Alkitab-AI.apk)**

*Catatan: Pastikan Anda mengizinkan instalasi dari "Sumber Tidak Dikenal" (Unknown Sources) di pengaturan keamanan Android Anda untuk memasang file APK ini.*

---

## 🛠️ Arsitektur & Teknologi

Aplikasi ini dibangun menggunakan standar pengembangan Android modern (*industry-grade*):

*   **Jetpack Compose**: UI deklaratif penuh dengan transisi halus dan animasi modern.
*   **MVVM Architecture**: Pemisahan logika bisnis (ViewModel) dan tampilan (UI State) yang bersih untuk performa tinggi.
*   **Room Database**: Penyimpanan lokal berkinerja tinggi untuk menyimpan Catatan, Bookmark, Highlight, dan Riwayat secara luring (Offline).
*   **Kotlin Coroutines & Flow**: Penanganan operasi asinkronous dan reaktivitas data secara real-time.
*   **OkHttp & Retrofit**: Komunikasi jaringan yang cepat dan andal ke API Gemini / OpenRouter.

---

## ⚙️ Petunjuk Pengembangan (Developer Setup)

Jika Anda ingin mengompilasi aplikasi ini secara mandiri atau memodifikasinya:

1.  **Clone Repository**:
    ```bash
    git clone https://github.com/username/alkitab-ai.git
    cd alkitab-ai
    ```
2.  **Konfigurasi Kunci API**:
    Aplikasi ini menggunakan sistem enkripsi `BuildConfig` yang ditarik dari file `.env`. 
    Buat atau ubah file `.env` di root direktori proyek Anda:
    ```env
    GEMINI_API_KEY="masukkan_kunci_gemini_anda"
    OPENROUTER_API_KEY="masukkan_kunci_openrouter_anda"
    ```
3.  **Kompilasi Menggunakan Gradle**:
    Untuk merakit APK debug baru:
    ```bash
    gradle assembleDebug
    ```
    Atau salin langsung hasil build ke root:
    ```bash
    gradle :app:copyApkToRoot
    ```

---

## 🎨 Next.js Landing Page Prompt (Generator Web Keren)

Gunakan prompt di bawah ini pada AI Code Generator kesayangan Anda (seperti **Gemini**, **v0.dev**, **Claude**, atau **Cursor**) untuk membuat situs web Landing Page modern nan estetis yang mempromosikan aplikasi **Alkitab AI** ini:

```text
Create a modern, ultra-polished, responsive landing page using Next.js, Tailwind CSS, and Lucide Icons for an innovative Android app called "Alkitab AI Companion" (Bible AI Companion). 

Design Aesthetics & Theme:
- Use a premium "Luxury Cosmic Dark" visual concept (deep midnight blue and cosmic purple gradients, elegant charcoal backgrounds, and soft, warm golden/amber highlights representing spiritual illumination).
- Incorporate clean, high-contrast typography, ample negative space, and sleek glowing card elements.
- Add smooth exit/entrance hover animations, modern glassmorphic surfaces, and subtle starry background effects.

Layout & Navigation:
- Sticky Header: Clean transparent-to-blur backdrop navigation containing the branding logo ("Alkitab AI"), interactive menu links (Fitur, Cara Kerja, Tentang Kami), and a prominent "Unduh APK" CTA button in a gold accent border.
- Hero Section: 
  * Left side: An eye-catching headline ("Mendalami Firman, Dibimbing oleh Asisten AI Cerdas"), a reassuring subheadline describing the Alkitab AI app as a companion for Bible reading, personal journaling, and direct theological Q&A. 
  * Right side: An interactive, mock-up visual representing the Android app's modern interface (featuring the 'Verse of the Day' card and an AI Chat dialog bubble with a warm message).
  * Main Hero CTA: Primary solid gold button with a download icon pointing to the APK ("Unduh Sekarang (APK)") and a secondary outline button ("Pelajari Fitur").
- Interactive Feature Grid:
  * Show gorgeous cards highlighting: 1. Asisten AI Teologi (fast answers & counseling), 2. Pembaca Terjemahan Baru (highlight, bookmark, personal notes), 3. Ayat Hari Ini (rotates daily at 3:00 AM), 4. Keamanan Lokal (Room database, fully offline support).
- How It Works / App Flow Section:
  * A step-by-step visual line representing: 1. Install APK, 2. Setup your Gemini API Key in Settings, 3. Talk freely to your Christian spiritual AI mentor.
- Custom Download Subpage / Section:
  * A dedicated section with details of the latest release (e.g., v1.0.0, file size, package name) and an instant download link to Alkitab-AI.apk. Include a quick accordion FAQ on how to install third-party APKs on Android safely.
- Footer: A minimalist, clean footer with copyright, links, and spiritual encouragement "Firman-Mu adalah pelita bagi kakiku dan terang bagi jalanku. - Mazmur 119:105".

Write beautiful, fully responsive copy in professional, inviting Indonesian (Bahasa Indonesia).
```

---

## 📜 Lisensi & Kontribusi

*   Hak cipta teks Alkitab Terjemahan Baru (TB) dipegang oleh Lembaga Alkitab Indonesia (LAI).
*   Aplikasi ini dikembangkan untuk tujuan pelayanan dan pembelajaran rohani digital secara personal. Kontribusi dalam bentuk *Pull Request* atau pelaporan *Bug* sangat diapresiasi!

---
*“Sebab TUHAN memberikan hikmat, dari mulut-Nya datang pengetahuan dan kepandaian.” — Amsal 2:6*
