package camera;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class SimpleCamera {
    private Matrix4f projection = new Matrix4f().ortho2D(0, GameWindow.getActiveWindow().getWidth(), 0, GameWindow.getActiveWindow().getHeight());
    private Vector3f position;
    private Vector3f target;
    private Vector3f up;
    private Matrix4f viewMatrix;

    public SimpleCamera() {
        position = new Vector3f(0.0f, 0.0f, 3.0f);
        target = new Vector3f(0.0f, 0.0f, 0.0f);
        up = new Vector3f(0.0f, 1.0f, 0.0f);
        viewMatrix = new Matrix4f();
        updateViewMatrix();
    }

    private void updateViewMatrix() {
        viewMatrix.identity();
        viewMatrix.lookAt(position, target, up);
    }

    public void setPosition(Vector3f position) {
        this.position.set(position);
        updateViewMatrix();
    }

    public void setTarget(Vector3f target) {
        this.target.set(target);
        updateViewMatrix();
    }

    public void setUp(Vector3f up) {
        this.up.set(up);
        updateViewMatrix();
    }

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public Vector3f getTarget() {
        return new Vector3f(target);
    }

    public Vector3f getUp() {
        return new Vector3f(up);
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f(viewMatrix);
    }
}