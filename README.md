# NalarAI — GitHub Pages + Android APK

Repository ini berisi:
- `index.html` — website NalarAI yang diterbitkan melalui GitHub Pages.
- `android/` — aplikasi Android WebView yang membuka website NalarAI.
- `.github/workflows/` — otomatis menerbitkan website dan membangun APK setiap push ke `main`.

## Cara memasang di GitHub

1. Buat repository bernama **NalarAI** di akun GitHub.
2. Upload seluruh isi folder proyek ini ke root repository (bukan folder luarnya).
3. Pastikan branch utama bernama `main`.
4. Buka **Settings → Pages**. Pada bagian Build and deployment, pilih **GitHub Actions**.
5. Buka tab **Actions** dan jalankan workflow `Deploy Website and Build APK` (atau push commit baru).
6. Tunggu sampai job `deploy-pages` dan `build-apk` selesai.
7. Unduh `NalarAI-debug-apk` dari bagian Artifacts pada run workflow.

## URL website

Workflow menggunakan URL GitHub Pages otomatis:
`https://USERNAME.github.io/NalarAI/`

Nama repository harus `NalarAI` agar URL default pada workflow sesuai. Jika nama repository berbeda, ubah nilai `webAppUrl` pada langkah build di workflow.

## Catatan penting

- APK ini adalah pembungkus WebView online. Koneksi internet diperlukan untuk memuat website.
- NalarAI memakai CDN eksternal (Tailwind, Google Fonts, Marked, KaTeX, JSZip); koneksi internet diperlukan untuk fitur yang memuat dependensi tersebut.
- API Key Gemini dimasukkan sendiri melalui Pengaturan di aplikasi. Jangan menaruh API Key di source code atau repository publik.
- API Key yang disimpan oleh halaman web berada di penyimpanan lokal WebView pada perangkat.
- Kamera memerlukan izin Android dan HTTPS; GitHub Pages menyediakan HTTPS.
- Artifact `debug` ditujukan untuk pengujian dan instalasi langsung. Untuk distribusi publik jangka panjang, buat APK/AAB release dengan signing key milik sendiri.
