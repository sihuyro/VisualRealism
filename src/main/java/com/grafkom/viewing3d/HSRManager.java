package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL11.*;

/**
 * Pengelola Hidden Surface Removal: Z-Buffer Depth Test dan Back-Face Culling.
 */
public class HSRManager {

    public static void setupDepthTest(boolean enable) {
        if (enable) {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS); // Pixel lebih dekat lolos uji depth
            glDepthMask(true);
        } else {
            glDisable(GL_DEPTH_TEST);
        }
    }

    public static void setupBackFaceCulling(boolean enable) {
        setupBackFaceCulling(enable, false);
    }

    /**
     * @param cullFront true untuk sengaja membuang face DEPAN (GL_FRONT), dipakai sebagai
     *                  demonstrasi "kesalahan culling" (tampak depan terpotong).
     */
    public static void setupBackFaceCulling(boolean enable, boolean cullFront) {
        if (enable) {
            glEnable(GL_CULL_FACE);
            glCullFace(cullFront ? GL_FRONT : GL_BACK); // Buang polygon belakang
            glFrontFace(GL_CCW);                         // Counter-Clockwise adalah tampak depan
        } else {
            glDisable(GL_CULL_FACE);
        }
    }
}
