package com.grafkom.viewing3d;

import org.joml.Vector3f;

/**
 * Mesh 3D bermateri: memegang dua versi mesh (Flat & Smooth), tekstur, dan parameter material.
 */
public class Model3D {

    private final String name;
    private final Mesh flatMesh;
    private final Mesh smoothMesh;
    private final Texture2D texture;
    private final Vector3f color;
    private final Vector3f textureTint;
    private final float specularStrength;
    private final float shininess;

    public Model3D(String name, MeshBuilder builder, Texture2D texture,
                   Vector3f color, Vector3f textureTint, float specularStrength, float shininess) {
        this.name = name;
        this.flatMesh = new Mesh(builder.buildFlat());
        this.smoothMesh = new Mesh(builder.buildSmooth());
        this.texture = texture;
        this.color = new Vector3f(color);
        this.textureTint = new Vector3f(textureTint);
        this.specularStrength = specularStrength;
        this.shininess = shininess;
    }

    public void draw(boolean flatShading) {
        (flatShading ? flatMesh : smoothMesh).draw();
    }

    public String getName() {
        return name;
    }

    public Texture2D getTexture() {
        return texture;
    }

    public Vector3f getColor() {
        return color;
    }

    public Vector3f getTextureTint() {
        return textureTint;
    }

    public float getSpecularStrength() {
        return specularStrength;
    }

    public float getShininess() {
        return shininess;
    }

    public int getTriangleCount() {
        return smoothMesh.getTriangleCount();
    }

    public void cleanup() {
        flatMesh.cleanup();
        smoothMesh.cleanup();
    }
}
