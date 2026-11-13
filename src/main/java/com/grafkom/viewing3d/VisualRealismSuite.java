package com.grafkom.viewing3d;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * 3D Visual Realism Suite (Grafika Komputer Minggu 11).
 * <p>
 * Pipeline per frame:
 * <ol>
 *   <li>Pass 1 (Depth Pass)   : scene dirender dari sudut pandang cahaya ke {@link ShadowMapFBO}.</li>
 *   <li>Pass 2 (Lighting Pass): scene dirender dari kamera dengan shader Phong/Blinn-Phong,
 *       tekstur, dan uji bayangan terhadap shadow map.</li>
 * </ol>
 * Jalankan dengan argumen {@code --capture <dir>} untuk merekam screenshot pengujian dan benchmark FPS.
 */
public class VisualRealismSuite {

    // ---------------------------------------------------------------- window
    private long window;
    private int width = 1280;
    private int height = 720;
    private int fbWidth = width;
    private int fbHeight = height;
    private int targetFramebuffer = 0;   // 0 = layar; != 0 = FBO offscreen (mode capture)
    private int resolveFramebuffer = 0;

    // ---------------------------------------------------------------- scene
    private Camera3D camera;
    private ShaderProgram sceneShader;
    private ShaderProgram depthShader;
    private ShadowMapFBO shadowFBO;
    private final List<Texture2D> textures = new ArrayList<>();
    private Model3D[] mainModels;
    private Model3D floor;
    private Model3D propCube;
    private Model3D lightMarker;
    private final Matrix4f projection = new Matrix4f();

    // ---------------------------------------------------------------- render state
    private int mainIndex;
    private boolean useBlinnPhong;
    private boolean flatShading;
    private boolean depthTest;
    private boolean culling;
    private boolean cullFrontError;
    private boolean useTexture;
    private Texture2D.FilterMode filterMode;
    private boolean shadows;
    private boolean pcf;
    private int biasMode;
    private boolean animateLight;
    private boolean rotateObject;
    private float lightAngleDeg;
    private float objectAngleDeg;
    private float lightHeight;

    private static final String[] BIAS_NAMES = {"NONE (acne)", "MODUL max(0.05(1-N.L),0.005)", "SLOPE-SCALED tuned"};
    private static final float Z_NEAR = 0.5f;
    private static final float Z_FAR = 60.0f;

    // ---------------------------------------------------------------- input / stats
    private boolean mouseDragging;
    private double lastMouseX;
    private double lastMouseY;
    private int frameCounter;
    private double fpsTimer;
    private double currentFps;

    private static boolean skipBenchmark;

    public static void main(String[] args) {
        String captureDir = null;
        for (int i = 0; i < args.length; i++) {
            if ("--skip-bench".equals(args[i])) {
                skipBenchmark = true;
            }
            if ("--capture".equals(args[i]) && i + 1 < args.length) {
                captureDir = args[i + 1];
            }
        }
        new VisualRealismSuite().run(captureDir);
    }

    public void run(String captureDir) {
        initWindow();
        initScene();
        resetState();

        if (captureDir != null) {
            runCapture(new File(captureDir));
        } else {
            printControls();
            loop();
        }

        cleanup();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) {
            cb.free();
        }
    }

    private void resetState() {
        mainIndex = 0;
        useBlinnPhong = true;
        flatShading = false;
        depthTest = true;
        culling = true;
        cullFrontError = false;
        useTexture = true;
        filterMode = Texture2D.FilterMode.BILINEAR_MIPMAPPED;
        shadows = true;
        pcf = true;
        biasMode = 2;
        animateLight = true;
        rotateObject = true;
        lightAngleDeg = 35.0f;
        objectAngleDeg = 20.0f;
        lightHeight = 8.0f;
        applyFilterMode();
        if (camera != null) {
            camera.reset();
        }
    }

    // =====================================================================================
    // Inisialisasi
    // =====================================================================================
    private void initWindow() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Gagal inisialisasi GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_DEPTH_BITS, 24);   // Z-buffer 24-bit untuk menekan Z-fighting
        glfwWindowHint(GLFW_SAMPLES, 4);

        window = glfwCreateWindow(width, height, "3D Visual Realism Suite", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Gagal membuat window GLFW (butuh OpenGL 3.3 Core)");
        }

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            if (w > 0 && h > 0 && targetFramebuffer == 0) {
                fbWidth = w;
                fbHeight = h;
                glViewport(0, 0, w, h);
            }
        });
        glfwSetWindowSizeCallback(window, (win, w, h) -> {
            width = w;
            height = h;
        });
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                onKeyPress(key);
            }
        });
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                mouseDragging = (action == GLFW_PRESS);
            }
        });
        glfwSetCursorPosCallback(window, (win, x, y) -> {
            double dx = x - lastMouseX;
            double dy = y - lastMouseY;
            lastMouseX = x;
            lastMouseY = y;
            if (mouseDragging) {
                camera.orbit((float) -dx * 0.35f, (float) dy * 0.35f);
            }
        });
        glfwSetScrollCallback(window, (win, xo, yo) -> camera.zoom((float) -yo * 0.6f));

        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidmode != null) {
            glfwSetWindowPos(window, (vidmode.width() - width) / 2, (vidmode.height() - height) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        int[] fw = new int[1];
        int[] fh = new int[1];
        glfwGetFramebufferSize(window, fw, fh);
        fbWidth = fw[0];
        fbHeight = fh[0];

        System.out.println("OpenGL  : " + glGetString(GL_VERSION));
        System.out.println("Renderer: " + glGetString(GL_RENDERER));
    }

    private void initScene() {
        camera = new Camera3D(new Vector3f(0.0f, 1.1f, 0.0f), 9.0f, 30.0f, 22.0f);
        sceneShader = new ShaderProgram("/shaders/phong.vert", "/shaders/phong.frag");
        depthShader = new ShaderProgram("/shaders/depth.vert", "/shaders/depth.frag");
        shadowFBO = new ShadowMapFBO();

        Texture2D planet = addTexture("/textures/planet.png");
        Texture2D checker = addTexture("/textures/checker.png");
        Texture2D brick = addTexture("/textures/brick.png");
        Texture2D tiles = addTexture("/textures/tiles.png");

        Vector3f white = new Vector3f(1.0f);
        mainModels = new Model3D[]{
                new Model3D("Sphere", MeshFactory.sphere(1.1f, 18, 28), planet,
                        new Vector3f(0.80f, 0.22f, 0.18f), white, 0.9f, 32.0f),
                new Model3D("Torus", MeshFactory.torus(1.0f, 0.38f, 20, 36), checker,
                        new Vector3f(0.20f, 0.45f, 0.85f), white, 0.8f, 32.0f),
                new Model3D("House 3D", MeshFactory.house(), brick,
                        new Vector3f(0.78f, 0.52f, 0.38f), white, 0.25f, 16.0f)
        };
        floor = new Model3D("Ground Plane", MeshFactory.groundPlane(12.0f, 6.0f), tiles,
                new Vector3f(0.42f, 0.44f, 0.48f), white, 0.6f, 32.0f);
        propCube = new Model3D("Prop Cube", MeshFactory.cube(0.5f), checker,
                new Vector3f(0.85f, 0.68f, 0.35f), new Vector3f(0.95f, 0.85f, 0.7f), 0.4f, 16.0f);
        lightMarker = new Model3D("Light", MeshFactory.cube(0.15f), null,
                new Vector3f(1.0f, 0.95f, 0.6f), white, 0.0f, 1.0f);

        glClearColor(0.10f, 0.12f, 0.16f, 1.0f);
        glEnable(org.lwjgl.opengl.GL13.GL_MULTISAMPLE);
    }

    private Texture2D addTexture(String path) {
        Texture2D t = new Texture2D(path);
        textures.add(t);
        return t;
    }

    private void applyFilterMode() {
        for (Texture2D t : textures) {
            t.setFilterMode(filterMode);
        }
    }

    // =====================================================================================
    // Input
    // =====================================================================================
    private void onKeyPress(int key) {
        switch (key) {
            case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true);
            case GLFW_KEY_B -> useBlinnPhong = !useBlinnPhong;                 // Indikator 1
            case GLFW_KEY_F -> flatShading = !flatShading;                     // Indikator 2
            case GLFW_KEY_Z -> depthTest = !depthTest;                         // Indikator 3
            case GLFW_KEY_C -> culling = !culling;                             // Indikator 3
            case GLFW_KEY_V -> cullFrontError = !cullFrontError;               // demo culling salah
            case GLFW_KEY_T -> useTexture = !useTexture;                       // Indikator 4
            case GLFW_KEY_N -> {                                               // Indikator 4
                filterMode = filterMode == Texture2D.FilterMode.NEAREST
                        ? Texture2D.FilterMode.BILINEAR_MIPMAPPED : Texture2D.FilterMode.NEAREST;
                applyFilterMode();
            }
            case GLFW_KEY_H -> shadows = !shadows;                             // Indikator 5
            case GLFW_KEY_K -> biasMode = (biasMode + 1) % BIAS_NAMES.length;  // Indikator 5
            case GLFW_KEY_P -> pcf = !pcf;                                     // Indikator 5
            case GLFW_KEY_L -> animateLight = !animateLight;
            case GLFW_KEY_SPACE -> rotateObject = !rotateObject;
            case GLFW_KEY_M -> mainIndex = (mainIndex + 1) % mainModels.length;
            case GLFW_KEY_R -> resetState();
            default -> {
            }
        }
        updateTitle();
    }

    private void processContinuousInput(float dt) {
        float rot = 70.0f * dt;
        if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            camera.orbit(-rot, 0);
        }
        if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            camera.orbit(rot, 0);
        }
        if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            camera.orbit(0, rot);
        }
        if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS || glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            camera.orbit(0, -rot);
        }
        if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) {
            camera.zoom(-5.0f * dt);
        }
        if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) {
            camera.zoom(5.0f * dt);
        }
        if (glfwGetKey(window, GLFW_KEY_PAGE_UP) == GLFW_PRESS) {
            lightHeight = Math.min(14.0f, lightHeight + 4.0f * dt);
        }
        if (glfwGetKey(window, GLFW_KEY_PAGE_DOWN) == GLFW_PRESS) {
            lightHeight = Math.max(2.0f, lightHeight - 4.0f * dt);
        }
    }

    // =====================================================================================
    // Rendering
    // =====================================================================================
    private void update(float dt) {
        if (animateLight) {
            lightAngleDeg = (lightAngleDeg + 20.0f * dt) % 360.0f;
        }
        if (rotateObject) {
            objectAngleDeg = (objectAngleDeg + 25.0f * dt) % 360.0f;
        }
    }

    private Vector3f lightPosition() {
        float a = (float) Math.toRadians(lightAngleDeg);
        return new Vector3f(6.0f * (float) Math.cos(a), lightHeight, 6.0f * (float) Math.sin(a));
    }

    private Matrix4f lightSpaceMatrix(Vector3f lightPos) {
        // Directional light -> proyeksi ortografik yang membungkus seluruh scene
        Matrix4f lightProj = new Matrix4f().ortho(-9.0f, 9.0f, -9.0f, 9.0f, 1.0f, 26.0f);
        Matrix4f lightView = new Matrix4f().lookAt(lightPos, new Vector3f(0.0f, 0.5f, 0.0f), new Vector3f(0, 1, 0));
        return lightProj.mul(lightView);
    }

    private Matrix4f mainModelMatrix() {
        Matrix4f m = new Matrix4f();
        Model3D main = mainModels[mainIndex];
        switch (main.getName()) {
            case "Sphere" -> m.translate(0.0f, 1.7f, 0.0f).rotateY((float) Math.toRadians(objectAngleDeg));
            case "Torus" -> m.translate(0.0f, 1.8f, 0.0f)
                    .rotateY((float) Math.toRadians(objectAngleDeg))
                    .rotateX((float) Math.toRadians(60.0f));
            default -> m.translate(0.0f, 0.0f, 0.0f).rotateY((float) Math.toRadians(objectAngleDeg));
        }
        return m;
    }

    private Matrix4f propModelMatrix() {
        return new Matrix4f().translate(2.6f, 0.5f, 1.4f).rotateY((float) Math.toRadians(25.0f));
    }

    /** Menggambar seluruh objek scene. depthPass = true untuk Pass 1 (tanpa material). */
    private void drawScene(ShaderProgram shader, boolean depthPass, Vector3f lightPos) {
        drawObject(shader, depthPass, floor, new Matrix4f(), false);
        drawObject(shader, depthPass, mainModels[mainIndex], mainModelMatrix(), false);
        drawObject(shader, depthPass, propCube, propModelMatrix(), false);
        if (!depthPass) {
            drawObject(shader, false, lightMarker, new Matrix4f().translate(lightPos), true);
        }
    }

    private void drawObject(ShaderProgram shader, boolean depthPass, Model3D model, Matrix4f modelMatrix, boolean unlit) {
        shader.setMat4("model", modelMatrix);
        if (!depthPass) {
            Matrix3f normalMatrix = new Matrix3f(modelMatrix).invert().transpose();
            shader.setMat3("normalMatrix", normalMatrix);
            shader.setVec3("objectColor", model.getColor());
            shader.setVec3("textureTint", model.getTextureTint());
            shader.setFloat("specularStrength", model.getSpecularStrength());
            shader.setFloat("shininess", model.getShininess());
            shader.setBool("unlit", unlit);
            boolean hasTex = useTexture && model.getTexture() != null;
            shader.setBool("useTexture", hasTex);
            if (model.getTexture() != null) {
                model.getTexture().bind(0);
            }
        }
        model.draw(flatShading);
    }

    private void renderFrame() {
        Vector3f lightPos = lightPosition();
        Matrix4f lightSpace = lightSpaceMatrix(lightPos);

        // ---------------- Pass 1: Depth Pass (sudut pandang cahaya)
        if (shadows) {
            shadowFBO.bindForWriting();
            HSRManager.setupDepthTest(true);       // shadow map selalu butuh depth test
            HSRManager.setupBackFaceCulling(false);
            depthShader.use();
            depthShader.setMat4("lightSpaceMatrix", lightSpace);
            drawScene(depthShader, true, lightPos);
            shadowFBO.unbind(fbWidth, fbHeight);
        }

        // ---------------- Pass 2: Lighting Pass (sudut pandang kamera)
        glBindFramebuffer(GL_FRAMEBUFFER, targetFramebuffer);
        glViewport(0, 0, fbWidth, fbHeight);
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        HSRManager.setupDepthTest(depthTest);
        HSRManager.setupBackFaceCulling(culling, cullFrontError);

        projection.setPerspective((float) Math.toRadians(45.0f), (float) fbWidth / Math.max(1, fbHeight), Z_NEAR, Z_FAR);

        sceneShader.use();
        sceneShader.setMat4("projection", projection);
        sceneShader.setMat4("view", camera.calculateLookAtMatrix());
        sceneShader.setMat4("lightSpaceMatrix", lightSpace);
        sceneShader.setVec3("lightPos", lightPos);
        sceneShader.setVec3("viewPos", camera.getEye());
        sceneShader.setVec3("lightColor", new Vector3f(1.0f, 0.98f, 0.92f));
        sceneShader.setFloat("ambientStrength", 0.22f);
        sceneShader.setBool("useBlinnPhong", useBlinnPhong);
        sceneShader.setBool("useShadows", shadows);
        sceneShader.setBool("usePCF", pcf);
        sceneShader.setInt("biasMode", biasMode);
        sceneShader.setInt("textureSampler", 0);
        sceneShader.setInt("shadowMap", 1);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, shadowFBO.getDepthTextureID());

        drawScene(sceneShader, false, lightPos);
    }

    private void loop() {
        double last = glfwGetTime();
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) Math.min(0.1, now - last);
            last = now;

            processContinuousInput(dt);
            update(dt);
            renderFrame();

            frameCounter++;
            fpsTimer += dt;
            if (fpsTimer >= 0.5) {
                currentFps = frameCounter / fpsTimer;
                frameCounter = 0;
                fpsTimer = 0;
                updateTitle();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private String stateSummary() {
        return String.format(Locale.US,
                "%s | %s | Z-Buffer:%s | Cull:%s | Tex:%s | Shadow:%s Bias:%s PCF:%s | Model:%s",
                useBlinnPhong ? "BLINN-PHONG" : "PHONG",
                flatShading ? "FLAT" : "SMOOTH",
                depthTest ? "ON" : "OFF",
                culling ? (cullFrontError ? "FRONT(salah)" : "BACK") : "OFF",
                useTexture ? (filterMode == Texture2D.FilterMode.NEAREST ? "NEAREST" : "MIPMAP") : "OFF",
                shadows ? "ON" : "OFF", BIAS_NAMES[biasMode], pcf ? "ON" : "OFF",
                mainModels[mainIndex].getName());
    }

    private void updateTitle() {
        glfwSetWindowTitle(window, String.format(Locale.US, "Visual Realism Suite | %.0f FPS | %s", currentFps, stateSummary()));
    }

    private void printControls() {
        System.out.println("Kontrol 3D Visual Realism Suite:");
        System.out.println("  [Mouse Drag / WASD / Panah] : Orbit kamera   [Scroll / Q / E] : Zoom");
        System.out.println("  [B]  : Phong <-> Blinn-Phong              (Indikator 1)");
        System.out.println("  [F]  : Flat <-> Smooth Shading            (Indikator 2)");
        System.out.println("  [Z]  : Z-Buffer Depth Test ON/OFF         (Indikator 3)");
        System.out.println("  [C]  : Back-Face Culling ON/OFF           (Indikator 3)");
        System.out.println("  [V]  : Demo culling salah (GL_FRONT)      (Indikator 3)");
        System.out.println("  [T]  : Tekstur ON/OFF                     (Indikator 4)");
        System.out.println("  [N]  : Filter Nearest <-> Bilinear Mipmap (Indikator 4)");
        System.out.println("  [H]  : Shadow Mapping ON/OFF              (Indikator 5)");
        System.out.println("  [K]  : Mode Shadow Bias (None/Modul/Tuned)(Indikator 5)");
        System.out.println("  [P]  : PCF Soft Shadow ON/OFF             (Indikator 5)");
        System.out.println("  [L]  : Animasi cahaya  [PgUp/PgDn] : tinggi cahaya");
        System.out.println("  [SPACE] : Rotasi objek  [M] : Ganti model  [R] : Reset  [ESC] : Keluar");
    }

    // =====================================================================================
    // Mode capture: screenshot pengujian + benchmark FPS untuk laporan
    // =====================================================================================
    private record Preset(String file, Consumer<VisualRealismSuite> setup) {
    }

    private void runCapture(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Tidak dapat membuat folder " + dir);
        }
        List<Preset> presets = new ArrayList<>();
        // (a) Phong vs Blinn-Phong: tekstur off, cahaya rendah di belakang objek (sudut grazing)
        //     agar beda bentuk highlight specular pada lantai & bola terlihat jelas.
        Consumer<VisualRealismSuite> specView = s -> {
            s.useTexture = false; s.camera.set(7.5f, 20.0f, 14.0f);
            s.lightAngleDeg = 240.0f; s.lightHeight = 3.2f;
        };
        presets.add(new Preset("01_phong.png", specView.andThen(s -> s.useBlinnPhong = false)));
        presets.add(new Preset("02_blinn_phong.png", specView.andThen(s -> s.useBlinnPhong = true)));
        presets.add(new Preset("01b_phong_front.png", s -> {
            s.useTexture = false; s.useBlinnPhong = false; s.camera.set(5.0f, 30.0f, 18.0f); s.lightAngleDeg = 80.0f;
        }));
        presets.add(new Preset("02b_blinn_phong_front.png", s -> {
            s.useTexture = false; s.useBlinnPhong = true; s.camera.set(5.0f, 30.0f, 18.0f); s.lightAngleDeg = 80.0f;
        }));
        // (b) Flat vs Smooth
        presets.add(new Preset("03_flat_shading.png", s -> {
            s.useTexture = false; s.flatShading = true; s.camera.set(5.0f, 25.0f, 14.0f); s.lightAngleDeg = 70.0f;
        }));
        presets.add(new Preset("04_smooth_shading.png", s -> {
            s.useTexture = false; s.flatShading = false; s.camera.set(5.0f, 25.0f, 14.0f); s.lightAngleDeg = 70.0f;
        }));
        presets.add(new Preset("05_flat_torus.png", s -> {
            s.mainIndex = 1; s.useTexture = false; s.flatShading = true; s.camera.set(5.5f, 25.0f, 22.0f);
        }));
        presets.add(new Preset("06_smooth_torus.png", s -> {
            s.mainIndex = 1; s.useTexture = false; s.flatShading = false; s.camera.set(5.5f, 25.0f, 22.0f);
        }));
        // (c) HSR On/Off: kamera di sisi berlawanan dengan kubus (kubus berada DI BELAKANG rumah)
        Consumer<VisualRealismSuite> hsrView = s -> {
            s.mainIndex = 2; s.camera.set(8.0f, 235.0f, 20.0f); s.lightAngleDeg = 200.0f;
        };
        presets.add(new Preset("07_hsr_on.png", hsrView));
        presets.add(new Preset("08_zbuffer_off.png", hsrView.andThen(s -> s.depthTest = false)));
        presets.add(new Preset("09_zbuffer_off_cull_off.png", hsrView.andThen(s -> {
            s.depthTest = false; s.culling = false;
        })));
        presets.add(new Preset("09b_cull_off_zbuffer_on.png", hsrView.andThen(s -> s.culling = false)));
        presets.add(new Preset("10_cull_front_error.png", hsrView.andThen(s -> s.cullFrontError = true)));
        // (d) Texture filtering: sudut pandang rendah ke lantai (minification ekstrem)
        presets.add(new Preset("11_texture_nearest.png", s -> {
            s.filterMode = Texture2D.FilterMode.NEAREST; s.applyFilterMode(); s.camera.set(11.0f, 15.0f, 5.0f);
        }));
        presets.add(new Preset("12_texture_mipmap.png", s -> {
            s.filterMode = Texture2D.FilterMode.BILINEAR_MIPMAPPED; s.applyFilterMode(); s.camera.set(11.0f, 15.0f, 5.0f);
        }));
        presets.add(new Preset("12b_texture_nearest_closeup.png", s -> {
            s.mainIndex = 2; s.filterMode = Texture2D.FilterMode.NEAREST; s.applyFilterMode(); s.camera.set(2.0f, 10.0f, 15.0f);
        }));
        presets.add(new Preset("12c_texture_mipmap_closeup.png", s -> {
            s.mainIndex = 2; s.camera.set(2.0f, 10.0f, 15.0f);
        }));
        presets.add(new Preset("13_texture_planet.png", s -> {
            s.camera.set(5.0f, 15.0f, 15.0f); s.lightAngleDeg = 60.0f;
        }));
        presets.add(new Preset("14_texture_torus_checker.png", s -> {
            s.mainIndex = 1; s.camera.set(5.5f, 20.0f, 25.0f);
        }));
        // (e) Shadow mapping & bias
        presets.add(new Preset("15_shadow_off.png", s -> {
            s.shadows = false; s.camera.set(10.0f, 40.0f, 30.0f);
        }));
        presets.add(new Preset("16_shadow_on.png", s -> {
            s.shadows = true; s.camera.set(10.0f, 40.0f, 30.0f);
        }));
        Consumer<VisualRealismSuite> biasView = s -> {
            s.mainIndex = 2; s.camera.set(6.5f, 40.0f, 22.0f); s.lightHeight = 4.0f; s.lightAngleDeg = 20.0f;
        };
        presets.add(new Preset("17_shadow_bias_none_acne.png", biasView.andThen(s -> {
            s.biasMode = 0; s.pcf = false;
        })));
        presets.add(new Preset("18_shadow_bias_modul_peterpanning.png", biasView.andThen(s -> {
            s.biasMode = 1; s.pcf = false;
        })));
        presets.add(new Preset("19_shadow_bias_tuned_pcf.png", biasView.andThen(s -> {
            s.biasMode = 2; s.pcf = true;
        })));
        presets.add(new Preset("19b_shadow_tuned_no_pcf.png", biasView.andThen(s -> {
            s.biasMode = 2; s.pcf = false;
        })));
        presets.add(new Preset("20_overview_house.png", s -> {
            s.mainIndex = 2; s.camera.set(9.5f, 35.0f, 24.0f);
        }));
        presets.add(new Preset("21_overview_torus.png", s -> {
            s.mainIndex = 1; s.camera.set(9.0f, 50.0f, 26.0f);
        }));

        glfwSwapInterval(0);
        createCaptureTargets(1280, 720);
        for (Preset p : presets) {
            resetState();
            animateLight = false;
            rotateObject = false;
            p.setup().accept(this);
            for (int i = 0; i < 3; i++) {
                renderFrame();
                glfwSwapBuffers(window);
                glfwPollEvents();
            }
            renderFrame();
            glFinish();
            saveScreenshot(new File(dir, p.file()));
            glfwSwapBuffers(window);
            glfwPollEvents();
            System.out.println("Saved " + p.file() + "  [" + stateSummary() + "]");
        }

        if (!skipBenchmark) {
            runBenchmark(new File(dir, "benchmark.csv"));
        }
    }

    /** FBO offscreen MSAA 4x + FBO resolve agar ukuran screenshot konsisten (tidak bergantung window manager). */
    private void createCaptureTargets(int w, int h) {
        fbWidth = w;
        fbHeight = h;
        targetFramebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, targetFramebuffer);
        int msColor = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, msColor);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, 4, GL_RGBA8, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, msColor);
        int msDepth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, msDepth);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, 4, GL_DEPTH_COMPONENT24, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, msDepth);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Capture FBO tidak lengkap");
        }

        resolveFramebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, resolveFramebuffer);
        int color = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, color);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, color);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void saveScreenshot(File out) {
        int w = fbWidth;
        int h = fbHeight;
        ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, targetFramebuffer);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFramebuffer);
        glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, resolveFramebuffer);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (x + (h - 1 - y) * w) * 4;
                int r = buf.get(i) & 0xFF;
                int g = buf.get(i + 1) & 0xFF;
                int b = buf.get(i + 2) & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        MemoryUtil.memFree(buf);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menyimpan screenshot " + out, e);
        }
    }

    private void runBenchmark(File csv) {
        record Bench(String name, Consumer<VisualRealismSuite> setup) {
        }
        List<Bench> configs = List.of(
                new Bench("Full (Blinn-Phong, Smooth, Z+Cull, Mipmap, Shadow+PCF)", s -> {
                }),
                new Bench("Phong Shading", s -> s.useBlinnPhong = false),
                new Bench("Flat Shading", s -> s.flatShading = true),
                new Bench("Back-Face Culling OFF", s -> s.culling = false),
                new Bench("Z-Buffer OFF", s -> s.depthTest = false),
                new Bench("Texture Nearest", s -> {
                    s.filterMode = Texture2D.FilterMode.NEAREST;
                    s.applyFilterMode();
                }),
                new Bench("Shadow tanpa PCF", s -> s.pcf = false),
                new Bench("Shadow Mapping OFF (single pass)", s -> s.shadows = false)
        );

        int warmup = 60;
        int frames = 600;
        int gpuFrames = 200;
        int query = glGenQueries();
        try (PrintWriter pw = new PrintWriter(csv)) {
            pw.println("konfigurasi,model,fps,frame_ms,gpu_ms");
            for (int modelIdx = 0; modelIdx < mainModels.length; modelIdx++) {
                for (Bench b : configs) {
                    resetState();
                    mainIndex = modelIdx;
                    camera.set(9.0f, 30.0f, 22.0f);
                    b.setup().accept(this);
                    for (int i = 0; i < warmup; i++) {
                        update(1.0f / 60.0f);
                        renderFrame();
                        glfwSwapBuffers(window);
                        glfwPollEvents();
                    }
                    glFinish();
                    double t0 = glfwGetTime();
                    for (int i = 0; i < frames; i++) {
                        update(1.0f / 60.0f);
                        renderFrame();
                        glfwSwapBuffers(window);
                        glfwPollEvents();
                    }
                    glFinish();
                    double elapsed = glfwGetTime() - t0;
                    double fps = frames / elapsed;

                    // Waktu eksekusi GPU murni via GL_TIME_ELAPSED query
                    long gpuNs = 0;
                    for (int i = 0; i < gpuFrames; i++) {
                        update(1.0f / 60.0f);
                        glBeginQuery(GL_TIME_ELAPSED, query);
                        renderFrame();
                        glEndQuery(GL_TIME_ELAPSED);
                        gpuNs += glGetQueryObjectui64(query, GL_QUERY_RESULT);
                        glfwSwapBuffers(window);
                        glfwPollEvents();
                    }
                    double gpuMs = gpuNs / 1.0e6 / gpuFrames;
                    String line = String.format(Locale.US, "\"%s\",%s,%.1f,%.3f,%.3f",
                            b.name(), mainModels[modelIdx].getName(), fps, 1000.0 / fps, gpuMs);
                    pw.println(line);
                    System.out.println("BENCH " + line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menulis benchmark", e);
        } finally {
            glDeleteQueries(query);
        }
    }

    private void cleanup() {
        for (Model3D m : mainModels) {
            m.cleanup();
        }
        floor.cleanup();
        propCube.cleanup();
        lightMarker.cleanup();
        textures.forEach(Texture2D::cleanup);
        shadowFBO.cleanup();
        sceneShader.cleanup();
        depthShader.cleanup();
    }
}
