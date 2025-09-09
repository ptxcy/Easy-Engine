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
    private final Matrix4f projection;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Vector3f position = new Vector3f(0f,0f,0f);

    public SimpleCamera2D() {
        projection = new Matrix4f().ortho2D(
            0, GameWindow.getActiveWindow().getWidth(),
            0, GameWindow.getActiveWindow().getHeight()
        );
        updateCamera();

        //Simple close Window Handling and WASD movement
        glfwSetKeyCallback(GameWindow.getActiveWindow()
            .getWindowHandle(), (window, key, scancode, action, mods) -> {
            float moveSpeed = 10.0f;

            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }

            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                switch (key) {
                    case GLFW_KEY_W:
                        position.add(0.0f, moveSpeed, 0.0f);
                        updateCamera();
                        break;
                    case GLFW_KEY_S:
                        position.add(0.0f, -moveSpeed, 0.0f);
                        updateCamera();
                        break;
                    case GLFW_KEY_A:
                        position.add(-moveSpeed, 0.0f, 0.0f);
                        updateCamera();
                        break;
                    case GLFW_KEY_D:
                        position.add(moveSpeed, 0.0f, 0.0f);
                        updateCamera();
                        break;
                }
            }
        });
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
