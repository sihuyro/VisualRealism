// ==========================================================
// FILE: src/main/resources/shaders/depth.frag
// Pass 1 (Depth Pass): hanya depth yang ditulis ke Shadow Map,
// tidak ada color attachment (glDrawBuffer(GL_NONE)).
// ==========================================================
#version 330 core

void main() {
    // gl_FragDepth ditulis otomatis oleh rasterizer
}
