# Aplikasi Resmi Mippedia untuk Android

Aplikasi Android resmi untuk ekosistem Mippedia (Ensiklopedia daring umum multibahasa berbasis MediaWiki).

## Fitur Utama
1. Dukungan Subdomain Multibahasa & Commons:
   - `https://id.mippedia.org`: Edisi Bahasa Indonesia
   - `https://en.mippedia.org`: Edisi Bahasa Inggris
   - `https://commons.mippedia.org`: Repositori berkas media dan gambar bersama
2. Antarmuka Mobile Modern (Material Design 3):
   - Desain aplikasi native layaknya aplikasi ensiklopedia modern (bukan tampilan situs web biasa).
   - Penjelajahan artikel dengan kartu ringkasan, pembacaan yang nyaman, pembagian sub-bab, serta pengaturan ukuran huruf dan mode serif.
3. Pemisahan Berkas Media (Berkas: / Commons):
   - Judul dengan awalan `Berkas:` atau `File:` secara otomatis ditangani oleh penampil media Commons resolusi tinggi dengan informasi uploader, ukuran, dan resolusi.
   - Tab eksplorasi khusus berkas media Commons.
4. Infobox Terstruktur:
   - Kotak informasi (Infobox) diparsing dan ditampilkan rapi dalam kartu native Material 3.
5. Penyuntingan Nyata & Deteksi Halaman Terlindungi:
   - Jika artikel dilindungi pada situs web asli, aplikasi secara otomatis menampilkan status perlindungan dan mengunci tombol penyuntingan.
   - Editor Wikitext native lengkap dengan toolbar sintaks (tebal, miring, judul, berkas, templat, tautan), pratinjau langsung, ringkasan suntingan, dan animasi sukses penerbitan.
6. Autentikasi & Akun Sementara:
   - Masuk (Login) melalui API MediaWiki resmi.
   - Pendaftaran akun baru langsung dari aplikasi.
   - Atur ulang kata sandi (Reset Password).
   - Pengenalan Akun Sementara (IP pengguna) sesuai dengan sistem MediaWiki 1.46.
7. Pencarian Cerdas & Halaman Terkait:
   - Filter pencarian menurut Artikel, Berkas Commons, dan Kategori.
   - Riwayat pencarian yang tersimpan di perangkat.
   - Tautan balik (Halaman terkait / backlinks).

## Cara Build APK
Untuk membuat APK melalui komputer atau GitHub Actions:
```bash
./gradlew assembleRelease
# atau
./gradlew assembleDebug
```
File APK akan berada di: `app/build/outputs/apk/debug/app-debug.apk` atau `release/app-release.apk`.
