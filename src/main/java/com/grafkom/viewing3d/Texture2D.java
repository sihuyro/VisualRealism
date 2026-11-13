package com.grafkom.viewing3d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.*;

/**
 * Loader tekstur 2D (PNG/JPG) berbasis STBImage dengan dukungan dua mode filtering:
 * Nearest Neighbor dan Bilinear Mipmapped (trilinear: GL_LINEAR_MIPMAP_LINEAR).
 */
public class Texture2D {

    public enum FilterMode {
        NEAREST,
        BILINEAR_MIPMAPPED
    }

    private final int textureID;
    private final String name;
    private int width;
    private int height;

    /**
     * @param path path resource classpath (mis. "/textures/brick.png") atau path file pada disk.
     */
    public Texture2D(String path) {
        this.name = path;
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);

        // Texture Wrap Parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        // Baris pertama gambar = v tertinggi (OpenGL memulai v = 0 dari bawah)
        stbi_set_flip_vertically_on_load(true);

        ByteBuffer encoded = readBytes(path);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer data = stbi_load_from_memory(encoded, w, h, channels, 4);
            if (data == null) {
                throw new IllegalStateException("Failed to load texture: " + path + " (" + stbi_failure_reason() + ")");
            }
            width = w.get(0);
            height = h.get(0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
            glGenerateMipmap(GL_TEXTURE_2D); // piramida mipmap 1/2, 1/4, 1/8, ...
            stbi_image_free(data);
        } finally {
            MemoryUtil.memFree(encoded);
        }
        setFilterMode(FilterMode.BILINEAR_MIPMAPPED);
    }

    private static ByteBuffer readBytes(String path) {
        byte[] bytes;
        try (InputStream in = Texture2D.class.getResourceAsStream(path)) {
            if (in != null) {
                bytes = in.readAllBytes();
            } else {
                bytes = Files.readAllBytes(Path.of(path));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membaca tekstur " + path, e);
        }
        ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
        buf.put(bytes).flip();
        return buf;
    }

    public void setFilterMode(FilterMode mode) {
        glBindTexture(GL_TEXTURE_2D, textureID);
        if (mode == FilterMode.NEAREST) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        } else {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureID);
    }

    public String getName() {
        return name;
    }

    public void cleanup() {
        glDeleteTextures(textureID);
    }
}
