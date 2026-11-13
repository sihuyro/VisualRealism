package com.grafkom.viewing3d;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Kamera orbital (turntable) hasil pengembangan Minggu 10.
 * Matriks LookAt tetap dihitung manual dari basis ortonormal (u, v, n).
 */
public class Camera3D {

    private final Vector3f eye = new Vector3f();
    private final Vector3f target;
    private final Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);

    private final float defaultRadius;
    private final float defaultAzimuth;
    private final float defaultElevation;

    private float radius;
    private float azimuthDeg;
    private float elevationDeg;

    public Camera3D(Vector3f target, float radius, float azimuthDeg, float elevationDeg) {
        this.target = new Vector3f(target);
        this.defaultRadius = radius;
        this.defaultAzimuth = azimuthDeg;
        this.defaultElevation = elevationDeg;
        set(radius, azimuthDeg, elevationDeg);
    }

    // Perhitungan manual Matriks LookAt (View Matrix)
    public Matrix4f calculateLookAtMatrix() {
        Vector3f n = new Vector3f();
        eye.sub(target, n).normalize(); // View Plane Normal

        Vector3f u = new Vector3f();
        up.cross(n, u).normalize();     // Right Vector

        Vector3f v = new Vector3f();
        n.cross(u, v);                  // True Up Vector

        Matrix4f viewMatrix = new Matrix4f();
        viewMatrix.m00(u.x).m10(u.y).m20(u.z).m30(-u.dot(eye));
        viewMatrix.m01(v.x).m11(v.y).m21(v.z).m31(-v.dot(eye));
        viewMatrix.m02(n.x).m12(n.y).m22(n.z).m32(-n.dot(eye));
        viewMatrix.m03(0.0f).m13(0.0f).m23(0.0f).m33(1.0f);
        return viewMatrix;
    }

    // Update koordinat sferis untuk kamera orbital
    public final void set(float radius, float azimuthDeg, float elevationDeg) {
        this.radius = Math.max(2.0f, Math.min(30.0f, radius));
        this.azimuthDeg = azimuthDeg % 360.0f;
        this.elevationDeg = Math.max(-10.0f, Math.min(85.0f, elevationDeg));

        float azRad = (float) Math.toRadians(this.azimuthDeg);
        float elRad = (float) Math.toRadians(this.elevationDeg);

        eye.x = target.x + this.radius * (float) (Math.cos(elRad) * Math.sin(azRad));
        eye.y = target.y + this.radius * (float) (Math.sin(elRad));
        eye.z = target.z + this.radius * (float) (Math.cos(elRad) * Math.cos(azRad));
    }

    public void orbit(float deltaAzimuth, float deltaElevation) {
        set(radius, azimuthDeg + deltaAzimuth, elevationDeg + deltaElevation);
    }

    public void zoom(float deltaRadius) {
        set(radius + deltaRadius, azimuthDeg, elevationDeg);
    }

    public void reset() {
        set(defaultRadius, defaultAzimuth, defaultElevation);
    }

    public Vector3f getEye() {
        return new Vector3f(eye);
    }

    public float getRadius() {
        return radius;
    }
}
