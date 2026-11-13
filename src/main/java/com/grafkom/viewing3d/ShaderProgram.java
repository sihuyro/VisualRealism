package com.grafkom.viewing3d;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

/**
 * Enkapsulasi program GLSL (vertex + fragment shader) beserta cache lokasi uniform.
 */
public class ShaderProgram {

    private final int programID;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    public ShaderProgram(String vertexResource, String fragmentResource) {
        int vs = compile(GL_VERTEX_SHADER, readResource(vertexResource), vertexResource);
        int fs = compile(GL_FRAGMENT_SHADER, readResource(fragmentResource), fragmentResource);

        programID = glCreateProgram();
        glAttachShader(programID, vs);
        glAttachShader(programID, fs);
        glLinkProgram(programID);
        if (glGetProgrami(programID, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Gagal link shader: " + glGetProgramInfoLog(programID));
        }
        glDetachShader(programID, vs);
        glDetachShader(programID, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String source, String name) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Gagal compile " + name + ":\n" + glGetShaderInfoLog(id));
        }
        return id;
    }

    private static String readResource(String path) {
        try (InputStream in = ShaderProgram.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource shader tidak ditemukan: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membaca shader " + path, e);
        }
    }

    public void use() {
        glUseProgram(programID);
    }

    private int loc(String name) {
        return uniformCache.computeIfAbsent(name, n -> glGetUniformLocation(programID, n));
    }

    public void setBool(String name, boolean value) {
        glUniform1i(loc(name), value ? 1 : 0);
    }

    public void setInt(String name, int value) {
        glUniform1i(loc(name), value);
    }

    public void setFloat(String name, float value) {
        glUniform1f(loc(name), value);
    }

    public void setVec3(String name, Vector3f v) {
        glUniform3f(loc(name), v.x, v.y, v.z);
    }

    public void setMat4(String name, Matrix4f m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(loc(name), false, m.get(stack.mallocFloat(16)));
        }
    }

    public void setMat3(String name, Matrix3f m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix3fv(loc(name), false, m.get(stack.mallocFloat(9)));
        }
    }

    public void cleanup() {
        glDeleteProgram(programID);
    }
}
