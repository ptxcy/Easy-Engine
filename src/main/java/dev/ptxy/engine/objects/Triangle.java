package dev.ptxy.engine.objects;

import org.joml.Vector2f;
import org.joml.Vector3f;

public class Triangle {
    //Array That Describes The Form of The Triangle
    private final Vector2f[] uvCoords = new Vector2f[3];
    //Array That Describes The Normals Of each Vertex in formData
    private final Vector3f[] normals = new Vector3f[3];
    //Array That Describes The Form of The Triangle
    private final Vector3f[] formData = new Vector3f[3];

    //Constructor with Material
    public Triangle(Vector3f[] formData, Vector3f[] normals, Vector2f[] uv) {
        if (formData == null || formData.length != 3) {
            throw new IllegalArgumentException("formData must have exactly 3 elements");
        }

        if(uv == null || uv.length != 3) {
            throw new IllegalArgumentException("uv must have exactly 3 elements");
        }

        this.formData[0] = formData[0];
        this.formData[1] = formData[1];
        this.formData[2] = formData[2];

        this.normals[0] = normals[0];
        this.normals[1] = normals[1];
        this.normals[2] = normals[2];

        this.uvCoords[0] = uv[0];
        this.uvCoords[1] = uv[1];
        this.uvCoords[2] = uv[2];
    }

    public Vector3f[] getFormData() {
        return formData;
    }

    public Vector3f[] getNormals() {
        return normals;
    }

    public Vector2f[] getUvCoords() {
        return uvCoords;
    }
}
