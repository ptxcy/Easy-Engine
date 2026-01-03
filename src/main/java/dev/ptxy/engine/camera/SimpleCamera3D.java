package dev.ptxy.engine.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class SimpleCamera3D implements Camera {
    private Matrix4f projection;
    private Matrix4f viewMatrix = new Matrix4f();
    private Vector3f position = new Vector3f(0, 0, 5);
    private Vector3f rotation = new Vector3f(0, 0, 0);

    public SimpleCamera3D(float fov, float aspect, float near, float far) {
        projection = new Matrix4f().perspective(fov, aspect, near, far);
        updateView();
    }

    public void updateView() {
        viewMatrix.identity()
            .rotateX(rotation.x)
            .rotateY(rotation.y)
            .rotateZ(rotation.z)
            .translate(-position.x, -position.y, -position.z);
    }

    public void setPosition(Vector3f pos) { position.set(pos); updateView(); }

    public void setRotation(Vector3f rot) { rotation.set(rot); updateView(); }

    @Override
    public Matrix4f getViewMatrix() { return new Matrix4f(viewMatrix); }

    @Override
    public Matrix4f getProjection() { return new Matrix4f(projection); }
}
