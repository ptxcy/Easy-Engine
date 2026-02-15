package dev.ptxy.engine.objects;

import org.joml.Vector2f;
import org.joml.Vector3f;

public record Triangle(
        Vector3f[] formData,
        Vector3f[] normals,
        Vector2f[] uvCoords,
        Vector3f[] tangents,
        Vector3f[] bitangents
) {
    public Triangle {
        if (formData == null || formData.length != 3)
            throw new IllegalArgumentException("formData must have exactly 3 elements");
        if (normals == null || normals.length != 3)
            throw new IllegalArgumentException("normals must have exactly 3 elements");
        if (uvCoords == null || uvCoords.length != 3)
            throw new IllegalArgumentException("uvCoords must have exactly 3 elements");
    }
}
