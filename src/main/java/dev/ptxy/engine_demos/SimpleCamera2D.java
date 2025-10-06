package dev.ptxy.engine_demos;

import dev.ptxy.engine.GameWindow;
import dev.ptxy.engine.camera.Camera;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class SimpleCamera2D implements Camera {
    protected Matrix4f projection;
    protected Matrix4f viewMatrix = new Matrix4f();
    protected Vector3f position = new Vector3f(0f,0f,0f);
    protected Vector3f scale = new Vector3f(1f,1f,1f);

    //TODO Remove Hard Dependency To Cory
    public SimpleCamera2D() {
        this.projection = new Matrix4f().ortho2D(
            0, GameWindow.getActiveWindow().getWidth(),
            0, GameWindow.getActiveWindow().getHeight()
        );
        updateCamera();
    }

    public void updateCamera() {
        viewMatrix.identity().scale(scale).translate(-position.x, -position.y, position.z);
    }

    public void setPosition(Vector3f pos) {
        position.set(pos);
        updateCamera();
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getScale() {
        return scale;
    }

    @Override
    public Matrix4f getViewMatrix() {
        return new Matrix4f(viewMatrix);
    }

    @Override
    public Matrix4f getProjection() {
        return new Matrix4f(projection);
    }
}
