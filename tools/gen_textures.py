#!/usr/bin/env python3
"""Generate procedural PNG textures for the Visual Realism Suite (no external deps)."""
import math
import os
import random
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "textures")


def write_png(path, w, h, pixel):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b = pixel(x, y)
            raw += bytes((max(0, min(255, int(r))), max(0, min(255, int(g))), max(0, min(255, int(b)))))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def noise_table(seed, n=256):
    rnd = random.Random(seed)
    return [[rnd.random() for _ in range(n)] for _ in range(n)]


def checker():
    # 8x8 papan catur + penanda arah "U" (merah) dan "V" (hijau) untuk verifikasi orientasi UV
    size, cells = 512, 8

    def px(x, y):
        cx, cy = x * cells // size, y * cells // size
        base = (236, 232, 222) if (cx + cy) % 2 == 0 else (52, 74, 120)
        # pojok kiri-atas gambar = v tertinggi setelah flip; tandai baris atas dengan pita merah
        if y < 10:
            return (200, 40, 40)
        if x < 10:
            return (40, 160, 70)
        return base

    write_png(os.path.join(OUT, "checker.png"), size, size, px)


def brick():
    size = 512
    nt = noise_table(7)
    bw, bh, mortar = 128, 48, 6

    def px(x, y):
        row = y // bh
        off = (bw // 2) if row % 2 else 0
        bx = (x + off) % bw
        by = y % bh
        n = nt[y % 256][x % 256]
        if bx < mortar or by < mortar:
            g = 175 + n * 30
            return (g, g * 0.97, g * 0.92)
        brick_id = (row * 7 + (x + off) // bw * 13) % 5
        tint = [0, 12, -10, 20, -18][brick_id]
        return (150 + tint + n * 35, 62 + tint * 0.4 + n * 20, 45 + n * 15)

    write_png(os.path.join(OUT, "brick.png"), size, size, px)


def tiles():
    # Ubin lantai kontras tinggi dengan nat tipis -> memperlihatkan aliasing/moire saat GL_NEAREST
    size, cells, grout = 512, 4, 4
    nt = noise_table(11)

    def px(x, y):
        cs = size // cells
        lx, ly = x % cs, y % cs
        n = nt[y % 256][x % 256]
        if lx < grout or ly < grout:
            return (40, 40, 44)
        cx, cy = x // cs, y // cs
        if (cx + cy) % 2 == 0:
            v = 214 + n * 20
            return (v, v * 0.96, v * 0.9)
        v = 120 + n * 20
        return (v * 0.55, v * 0.62, v * 0.7)

    write_png(os.path.join(OUT, "tiles.png"), size, size, px)


def planet():
    # Pita atmosfer ala planet gas: kontinu pada seam u=0/u=1 karena memakai fungsi periodik sudut
    w, h = 1024, 512
    palette = [(236, 222, 196), (205, 160, 110), (170, 104, 66), (226, 190, 140), (140, 86, 60), (244, 236, 220)]

    def lerp(a, b, t):
        return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))

    def px(x, y):
        ang = x / w * 2 * math.pi
        v = y / h
        warp = 0.035 * math.sin(ang * 2 + v * 9) + 0.02 * math.sin(ang * 5 - v * 23) + 0.01 * math.sin(ang * 11 + v * 40)
        t = (v + warp) * 9.0
        i = int(math.floor(t)) % len(palette)
        f = t - math.floor(t)
        f = f * f * (3 - 2 * f)
        c = lerp(palette[i], palette[(i + 1) % len(palette)], f)
        # badai oval besar
        dx = (math.atan2(math.sin(ang - 1.2), math.cos(ang - 1.2))) / 0.35
        dy = (v - 0.62) / 0.06
        d = dx * dx + dy * dy
        if d < 1.0:
            c = lerp(c, (190, 80, 50), (1.0 - d) ** 0.7)
        return c

    write_png(os.path.join(OUT, "planet.png"), w, h, px)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    checker()
    brick()
    tiles()
    planet()
    print("textures written to", os.path.abspath(OUT))
