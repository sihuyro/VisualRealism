package com.grafkom.viewing3d;

import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pengumpul geometri segitiga (posisi + UV) yang dapat menghasilkan dua versi data vertex:
 * <ul>
 *   <li>{@link #buildFlat()}  : satu normal per face (Flat / Facet Shading)</li>
 *   <li>{@link #buildSmooth()}: normal per vertex hasil Vertex Normal Averaging
 *       N_vertex = (sum N_face_i) / ||sum N_face_i|| (Smooth Shading)</li>
 * </ul>
 * Semua segitiga harus berurutan Counter-Clockwise (CCW) bila dilihat dari luar objek.
 */
public class MeshBuilder {

    private final List<Vector3f> positions = new ArrayList<>();
    private final List<Vector2f> uvs = new ArrayList<>();

    public MeshBuilder triangle(Vector3f a, Vector3f b, Vector3f c, Vector2f ta, Vector2f tb, Vector2f tc) {
        Vector3f n = faceNormal(a, b, c);
        if (n == null) {
            return this; // segitiga degenerate (mis. di kutub sphere) dibuang
        }
        positions.add(new Vector3f(a));
        positions.add(new Vector3f(b));
        positions.add(new Vector3f(c));
        uvs.add(new Vector2f(ta));
        uvs.add(new Vector2f(tb));
        uvs.add(new Vector2f(tc));
        return this;
    }

    /** Quad p0-p1-p2-p3 (CCW dari luar) dengan UV eksplisit. */
    public MeshBuilder quad(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
                            Vector2f t0, Vector2f t1, Vector2f t2, Vector2f t3) {
        triangle(p0, p1, p2, t0, t1, t2);
        triangle(p0, p2, p3, t0, t2, t3);
        return this;
    }

    /** Quad dengan UV planar otomatis berdasarkan panjang sisi dibagi skala tekstur (world unit per repeat). */
    public MeshBuilder quad(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float texScale) {
        float w = p0.distance(p1) / texScale;
        float h = p0.distance(p3) / texScale;
        return quad(p0, p1, p2, p3,
                new Vector2f(0, 0), new Vector2f(w, 0), new Vector2f(w, h), new Vector2f(0, h));
    }

    /** Kotak (box) tertutup dengan 6 face, normal menghadap keluar. */
    public MeshBuilder box(Vector3f min, Vector3f max, float texScale) {
        float x0 = min.x, y0 = min.y, z0 = min.z;
        float x1 = max.x, y1 = max.y, z1 = max.z;
        // depan (+Z)
        quad(v(x0, y0, z1), v(x1, y0, z1), v(x1, y1, z1), v(x0, y1, z1), texScale);
        // belakang (-Z)
        quad(v(x1, y0, z0), v(x0, y0, z0), v(x0, y1, z0), v(x1, y1, z0), texScale);
        // kanan (+X)
        quad(v(x1, y0, z1), v(x1, y0, z0), v(x1, y1, z0), v(x1, y1, z1), texScale);
        // kiri (-X)
        quad(v(x0, y0, z0), v(x0, y0, z1), v(x0, y1, z1), v(x0, y1, z0), texScale);
        // atas (+Y)
        quad(v(x0, y1, z1), v(x1, y1, z1), v(x1, y1, z0), v(x0, y1, z0), texScale);
        // bawah (-Y)
        quad(v(x0, y0, z0), v(x1, y0, z0), v(x1, y0, z1), v(x0, y0, z1), texScale);
        return this;
    }

    public static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    private static Vector3f faceNormal(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f e1 = new Vector3f(b).sub(a);
        Vector3f e2 = new Vector3f(c).sub(a);
        Vector3f n = e1.cross(e2);
        if (n.lengthSquared() < 1e-12f) {
            return null;
        }
        return n.normalize();
    }

    /** Flat Shading: setiap vertex dari satu face menerima normal face yang sama. */
    public float[] buildFlat() {
        float[] out = new float[positions.size() * Mesh.FLOATS_PER_VERTEX];
        int k = 0;
        for (int i = 0; i < positions.size(); i += 3) {
            Vector3f n = faceNormal(positions.get(i), positions.get(i + 1), positions.get(i + 2));
            for (int j = 0; j < 3; j++) {
                k = put(out, k, positions.get(i + j), n, uvs.get(i + j));
            }
        }
        return out;
    }

    /**
     * Smooth Shading: normal vertex = rata-rata ternormalisasi dari normal semua face yang berbagi posisi.
     * Vertex dikelompokkan berdasarkan posisi (welding) sehingga seam UV tidak memunculkan lipatan normal.
     */
    public float[] buildSmooth() {
        Map<Long, Vector3f> accum = new HashMap<>();
        for (int i = 0; i < positions.size(); i += 3) {
            Vector3f n = faceNormal(positions.get(i), positions.get(i + 1), positions.get(i + 2));
            for (int j = 0; j < 3; j++) {
                accum.computeIfAbsent(key(positions.get(i + j)), kk -> new Vector3f()).add(n);
            }
        }
        accum.values().forEach(Vector3f::normalize);

        float[] out = new float[positions.size() * Mesh.FLOATS_PER_VERTEX];
        int k = 0;
        for (int i = 0; i < positions.size(); i++) {
            k = put(out, k, positions.get(i), accum.get(key(positions.get(i))), uvs.get(i));
        }
        return out;
    }

    private static long key(Vector3f p) {
        long x = Math.round(p.x * 10000.0) & 0x1FFFFF;
        long y = Math.round(p.y * 10000.0) & 0x1FFFFF;
        long z = Math.round(p.z * 10000.0) & 0x1FFFFF;
        return (x << 42) | (y << 21) | z;
    }

    private static int put(float[] out, int k, Vector3f p, Vector3f n, Vector2f t) {
        out[k++] = p.x;
        out[k++] = p.y;
        out[k++] = p.z;
        out[k++] = n.x;
        out[k++] = n.y;
        out[k++] = n.z;
        out[k++] = t.x;
        out[k++] = t.y;
        return k;
    }
}
