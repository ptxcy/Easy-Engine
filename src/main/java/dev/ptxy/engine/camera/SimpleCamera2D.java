package dev.ptxy.engine.camera;

import dev.ptxy.engine.GameWindow;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class SimpleCamera2D {
    private final Matrix4f projection;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Vector3f position = new Vector3f(0f,0f,0f);

    public SimpleCamera2D() {
        projection = new Matrix4f().ortho2D(
            0, GameWindow.getActiveWindow().getWidth(),
            0, GameWindow.getActiveWindow().getHeight()
        );
        updateCamera();
    }

    public void updateCamera() {
        viewMatrix.identity().scale(1.0f,1.0f,1f).translate(-position.x, -position.y, 0f);
    }

    public void setPosition(Vector3f pos) {
        position.set(pos);
        updateCamera();
    }

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f(viewMatrix);
    }

    public Matrix4f getProjection() {
        return new Matrix4f(projection);
    }
}
