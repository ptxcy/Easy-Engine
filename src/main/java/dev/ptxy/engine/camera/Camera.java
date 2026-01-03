package dev.ptxy.engine.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public interface Camera {
    Matrix4f getProjection();

    Matrix4f getViewMatrix();

    Vector3f getPosition();
}
