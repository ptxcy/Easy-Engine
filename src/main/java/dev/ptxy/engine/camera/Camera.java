package dev.ptxy.engine.camera;

import org.joml.Matrix4f;

public interface Camera {
    Matrix4f getProjection();

    Matrix4f getViewMatrix();
}
