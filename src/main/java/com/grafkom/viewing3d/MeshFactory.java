package com.grafkom.viewing3d;

import org.joml.Vector2f;
import org.joml.Vector3f;

import static com.grafkom.viewing3d.MeshBuilder.v;

/**
 * Pabrik geometri prosedural: semua mesh memiliki Vektor Normal dan Koordinat Tekstur UV.
 */
public final class MeshFactory {

    private MeshFactory() {
    }

    /** UV Sphere (latitude-longitude). u = longitude, v = latitude. */
    public static MeshBuilder sphere(float r, int stacks, int slices) {
        MeshBuilder mb = new MeshBuilder();
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                Vector3f a = spherePoint(r, i, j, stacks, slices);
                Vector3f b = spherePoint(r, i + 1, j, stacks, slices);
                Vector3f c = spherePoint(r, i + 1, j + 1, stacks, slices);
                Vector3f d = spherePoint(r, i, j + 1, stacks, slices);
                Vector2f ta = sphereUV(i, j, stacks, slices);
                Vector2f tb = sphereUV(i + 1, j, stacks, slices);
                Vector2f tc = sphereUV(i + 1, j + 1, stacks, slices);
                Vector2f td = sphereUV(i, j + 1, stacks, slices);
                // a = kiri-atas, b = kiri-bawah, c = kanan-bawah, d = kanan-atas (dilihat dari luar) -> CCW
                mb.triangle(a, b, c, ta, tb, tc);
                mb.triangle(a, c, d, ta, tc, td);
            }
        }
        return mb;
    }

    private static Vector3f spherePoint(float r, int i, int j, int stacks, int slices) {
        double theta = Math.PI * i / stacks;          // 0 = kutub atas
        double phi = 2.0 * Math.PI * j / slices;
        return v((float) (r * Math.sin(theta) * Math.sin(phi)),
                (float) (r * Math.cos(theta)),
                (float) (r * Math.sin(theta) * Math.cos(phi)));
    }

    private static Vector2f sphereUV(int i, int j, int stacks, int slices) {
        return new Vector2f((float) j / slices, 1.0f - (float) i / stacks);
    }

    /** Torus dengan jari-jari mayor R dan minor r. */
    public static MeshBuilder torus(float majorR, float minorR, int rings, int slices) {
        MeshBuilder mb = new MeshBuilder();
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < slices; j++) {
                Vector3f a = torusPoint(majorR, minorR, i, j, rings, slices);
                Vector3f b = torusPoint(majorR, minorR, i, j + 1, rings, slices);
                Vector3f c = torusPoint(majorR, minorR, i + 1, j + 1, rings, slices);
                Vector3f d = torusPoint(majorR, minorR, i + 1, j, rings, slices);
                Vector2f ta = new Vector2f(3.0f * j / slices, (float) i / rings);
                Vector2f tb = new Vector2f(3.0f * (j + 1) / slices, (float) i / rings);
                Vector2f tc = new Vector2f(3.0f * (j + 1) / slices, (float) (i + 1) / rings);
                Vector2f td = new Vector2f(3.0f * j / slices, (float) (i + 1) / rings);
                mb.quad(a, b, c, d, ta, tb, tc, td);
            }
        }
        return mb;
    }

    private static Vector3f torusPoint(float R, float r, int i, int j, int rings, int slices) {
        double theta = 2.0 * Math.PI * i / rings;   // sudut di sekeliling tabung
        double phi = 2.0 * Math.PI * j / slices;    // sudut di sekeliling sumbu Y
        double ring = R + r * Math.cos(theta);
        return v((float) (ring * Math.sin(phi)), (float) (r * Math.sin(theta)), (float) (ring * Math.cos(phi)));
    }

    /** Rumah 3D: badan kotak + atap pelana (prisma) + pintu + cerobong. Alas berada di y = 0. */
    public static MeshBuilder house() {
        MeshBuilder mb = new MeshBuilder();
        float w = 1.0f, h = 1.4f, d = 0.8f, ridge = 2.3f;

        mb.box(v(-w, 0, -d), v(w, h, d), 0.9f);

        // Atap: dua bidang miring
        mb.quad(v(-w - 0.1f, h - 0.05f, d + 0.12f), v(w + 0.1f, h - 0.05f, d + 0.12f),
                v(w + 0.1f, ridge, 0), v(-w - 0.1f, ridge, 0), 0.9f);
        mb.quad(v(w + 0.1f, h - 0.05f, -d - 0.12f), v(-w - 0.1f, h - 0.05f, -d - 0.12f),
                v(-w - 0.1f, ridge, 0), v(w + 0.1f, ridge, 0), 0.9f);
        // Sisi bawah atap (overhang) agar atap tertutup saat culling aktif
        mb.quad(v(-w - 0.1f, h - 0.05f, -d - 0.12f), v(w + 0.1f, h - 0.05f, -d - 0.12f),
                v(w + 0.1f, h - 0.05f, d + 0.12f), v(-w - 0.1f, h - 0.05f, d + 0.12f), 0.9f);
        // Gable (segitiga) kanan dan kiri
        mb.triangle(v(w + 0.1f, h - 0.05f, d + 0.12f), v(w + 0.1f, h - 0.05f, -d - 0.12f), v(w + 0.1f, ridge, 0),
                new Vector2f(0, 0), new Vector2f(1.9f, 0), new Vector2f(0.95f, 1.0f));
        mb.triangle(v(-w - 0.1f, h - 0.05f, -d - 0.12f), v(-w - 0.1f, h - 0.05f, d + 0.12f), v(-w - 0.1f, ridge, 0),
                new Vector2f(0, 0), new Vector2f(1.9f, 0), new Vector2f(0.95f, 1.0f));

        // Pintu menonjol 3 cm dari dinding depan (menghindari Z-fighting coplanar)
        mb.box(v(-0.25f, 0.0f, d), v(0.25f, 0.85f, d + 0.03f), 0.9f);
        // Jendela kiri dan kanan
        mb.box(v(-0.8f, 0.6f, d), v(-0.45f, 1.0f, d + 0.03f), 0.9f);
        mb.box(v(0.45f, 0.6f, d), v(0.8f, 1.0f, d + 0.03f), 0.9f);
        // Cerobong asap
        mb.box(v(0.45f, 1.6f, -0.55f), v(0.75f, 2.55f, -0.25f), 0.9f);
        return mb;
    }

    /** Bidang lantai (ground plane) di y = 0, normal +Y. */
    public static MeshBuilder groundPlane(float halfSize, float repeat) {
        MeshBuilder mb = new MeshBuilder();
        float s = halfSize;
        mb.quad(v(-s, 0, s), v(s, 0, s), v(s, 0, -s), v(-s, 0, -s),
                new Vector2f(0, 0), new Vector2f(repeat, 0), new Vector2f(repeat, repeat), new Vector2f(0, repeat));
        return mb;
    }

    public static MeshBuilder cube(float half) {
        return new MeshBuilder().box(v(-half, -half, -half), v(half, half, half), half * 2.0f);
    }
}
