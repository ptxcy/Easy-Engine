package dev.ptxy.engine.camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class SimpleCamera3D implements Camera {
    private Matrix4f projection;
    private Matrix4f viewMatrix = new Matrix4f();
    private Vector3f position = new Vector3f(0, 0, 5);

    private float yaw = (float) -Math.PI / 2; // Blickrichtung -Z
    private float pitch = 0f;

    public SimpleCamera3D(float fov, float aspect, float near, float far) {
        projection = new Matrix4f().perspective(fov, aspect, near, far);
        updateView();
    }

    public void setPosition(Vector3f pos) { position.set(pos); updateView(); }

    public Vector3f getPosition() { return position; }

    public Matrix4f getViewMatrix() { return new Matrix4f(viewMatrix); }

    public Matrix4f getProjection() { return new Matrix4f(projection); }

    public void rotate(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch += deltaPitch;
        pitch = Math.max((float) -Math.PI / 2 + 0.01f, Math.min((float) Math.PI / 2 - 0.01f, pitch));
        updateView();
    }

    private void updateView() {
        Vector3f front = getForward();
        Vector3f right = front.cross(new Vector3f(0,1,0), new Vector3f()).normalize();
        Vector3f up = right.cross(front, new Vector3f()).normalize();
        viewMatrix.identity();
        viewMatrix.lookAt(position, position.add(front, new Vector3f()), up);
    }

    public Vector3f getForward() {
        return new Vector3f(
            (float) (Math.cos(pitch) * Math.cos(yaw)),
            (float) Math.sin(pitch),
            (float) (Math.cos(pitch) * Math.sin(yaw))
        ).normalize();
    }

    public Vector3f getRight() {
        return getForward().cross(new Vector3f(0, 1, 0), new Vector3f()).normalize();
    }

    public Vector3f getUp() {
        return getRight().cross(getForward(), new Vector3f()).normalize();
    }
}
