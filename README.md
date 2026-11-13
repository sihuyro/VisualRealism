# 3D Visual Realism Suite (Grafika Komputer Minggu 11)

Implementasi **Rendering Faces pada Visual Realism** berbasis **Java OOP**, **JOML 1.10.5**, **LWJGL 3.3.3 (OpenGL 3.3 Core Profile, GLFW, STBImage)**, dan **Modern GLSL Shaders (`#version 330 core`)** sebagai kelanjutan dari aplikasi 3D Viewing Minggu 10.

## Fitur Utama (5 Indikator CPMK Visual Realism)
1. **Shading Models (`phong.vert` & `phong.frag`)** — Perhitungan pencahayaan lokal per-pixel yang mencakup komponen **Ambient**, **Diffuse (Lambertian)**, dan **Specular** dengan peralihan interaktif real-time antara **Standard Phong** ($R \cdot V$) dan **Blinn-Phong** ($N \cdot H$).
2. **Flat Shading vs Smooth Shading (`MeshBuilder`, `Model3D`)** — Perbandingan langsung antara normal homogen per-segitiga (*Flat / Facet Shading*) dan *Vertex Normal Averaging* ($\mathbf{N}_v = \frac{\sum \mathbf{N}_{f,i}}{\|\sum \mathbf{N}_{f,i}\|}$) yang terinterpolasi halus per-pixel tanpa efek *Mach Banding*.
3. **Hidden Surface Removal (`HSRManager`)** — Konfigurasi **Z-Buffer Depth Test** (`GL_DEPTH_TEST`, `GL_LESS`, 24-bit depth buffer) dan **Back-Face Culling** (`GL_CULL_FACE`, `GL_BACK`, `GL_CCW`) beserta mode demonstrasi kesalahan culling (`GL_FRONT`).
4. **Texture Mapping to Faces (`Texture2D`)** — Pemuatan gambar tekstur PNG menggunakan `STBImage` (`lwjgl-stb`), pemetaan koordinat UV perspektif-korektif pada *Sphere*, *Torus*, *House 3D*, *Cube*, dan *Ground Plane*, serta perbandingan filtering **Nearest Neighbor (`GL_NEAREST`)** vs **Bilinear Mipmapped (`GL_LINEAR_MIPMAP_LINEAR`)**.
5. **Real-Time Shadow Mapping 2-Pass (`ShadowMapFBO`, `depth.vert`, `depth.frag`)** — Rendering bayangan dinamis 2-Pass menggunakan *Depth Framebuffer Object* `2048x2048` (`GL_DEPTH_COMPONENT24`, border clamp `1.0`), dilengkapi perbandingan 3 mode *Shadow Bias* (Tanpa Bias / *Shadow Acne*, Rumus Modul, dan *Slope-Scaled Tuned Bias*) serta *Percentage-Closer Filtering* (PCF 3x3).

## Struktur Kelas (`com.grafkom.viewing3d`)
- `VisualRealismSuite.java` : Kelas utama penyelenggara window GLFW, state interaktif 5 indikator, pipeline rendering 2-Pass (Depth Pass + Lighting Pass), serta mode perekaman pengujian otomatis (`--capture`).
- `Camera3D.java` : Kamera orbital 3D dengan perhitungan manual matriks *LookAt* berbasis vektor ortonormal `(u, v, n)`.
- `ShaderProgram.java` : Utilitas kompilasi, *linking*, dan manajemen *uniform* GLSL Vertex & Fragment Shader.
- `Mesh.java` : Enkapsulasi *Vertex Array Object* (VAO) dan *Vertex Buffer Object* (VBO) *interleaved* (`location 0` posisi, `location 1` normal, `location 2` UV).
- `MeshBuilder.java` : Pembangun geometri segitiga yang menghasilkan dua varian buffer: *Flat Shading* (per-face normal) dan *Smooth Shading* (*vertex normal averaging*).
- `MeshFactory.java` : Generator mesh prosedural ber-UV (`Sphere`, `Torus`, `House 3D`, `Cube`, dan `Ground Plane`).
- `Model3D.java` : Representasi objek 3D bermateri (pasangan mesh Flat/Smooth, tekstur, warna material, *specular strength*, dan *shininess*).
- `HSRManager.java` : Pengelola *Z-Buffer Depth Test* dan *Back-Face Culling*.
- `Texture2D.java` : Pemuat tekstur GPU berbasis `STBImage` dengan pembuatan *Mipmap* otomatis dan pengubah mode *filtering*.
- `ShadowMapFBO.java` : Pengelola *Framebuffer Object* khusus *depth attachment* (`2048x2048`) untuk *Shadow Mapping*.

## Kontrol Interaktif
| Tombol / Mouse | Indikator | Fungsi |
| :--- | :---: | :--- |
| `Drag Mouse Kiri` / `WASD` / `Panah` | Navigasi | Orbit kamera mengelilingi objek |
| `Scroll Mouse` / `Q` / `E` | Navigasi | Zoom kamera |
| `M` | Mesh 3D | Ganti model utama (`Sphere` / `Torus` / `House 3D`) |
| `B` | Indikator 1 | Toggle **Phong Shading** vs **Blinn-Phong Shading** |
| `F` | Indikator 2 | Toggle **Flat Shading** vs **Smooth Shading** |
| `Z` | Indikator 3 | Toggle **Z-Buffer Depth Test** (`ON` / `OFF`) |
| `C` | Indikator 3 | Toggle **Back-Face Culling** (`ON` / `OFF`) |
| `V` | Indikator 3 | Toggle simulasi kesalahan culling (`GL_FRONT` vs `GL_BACK`) |
| `T` | Indikator 4 | Toggle **Texture Mapping** (`ON` / `OFF`) |
| `N` | Indikator 4 | Toggle mode filtering tekstur (**Nearest** vs **Bilinear Mipmapped**) |
| `H` | Indikator 5 | Toggle **Real-Time Shadow Mapping** (`ON` / `OFF`) |
| `K` | Indikator 5 | Ganti mode **Shadow Bias** (`NONE` / `MODUL` / `SLOPE-SCALED TUNED`) |
| `P` | Indikator 5 | Toggle **PCF 3x3 Soft Shadow** (`ON` / `OFF`) |
| `L` & `PgUp`/`PgDn` | Cahaya | Toggle rotasi sumber cahaya & atur ketinggian cahaya |
| `SPACE` | Objek | Toggle rotasi objek utama |
| `R` | Reset | Kembalikan seluruh pengaturan ke kondisi awal |
| `ESC` | Sistem | Keluar dari aplikasi |
