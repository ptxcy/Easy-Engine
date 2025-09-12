package dev.ptxy.engine.camera;

import dev.ptxy.engine.GameWindow;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;

public class SimpleCamera2D {
    //TODO Remove Hard Dependency To Cory
    private final Matrix4f projection;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Vector3f position = new Vector3f(0f,0f,0f);
    private final Vector3f scale = new Vector3f(1f,1f,1f);

    public SimpleCamera2D() {
        projection = new Matrix4f().ortho2D(
            0, GameWindow.getActiveWindow().getWidth(),
            0, GameWindow.getActiveWindow().getHeight()
        );
        updateCamera();
    }

    public void updateCamera() {
        viewMatrix.identity().scale(scale).translate(-position.x, -position.y, 0f);
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

    public Matrix4f getViewMatrix() {
        return new Matrix4f(viewMatrix);
    }

    public Matrix4f getProjection() {
        return new Matrix4f(projection);
    }
}
