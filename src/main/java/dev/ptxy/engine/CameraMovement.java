package dev.ptxy.engine;

import dev.ptxy.engine.camera.SimpleCamera3D;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Q;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetKey;

public final class CameraMovement {
    private final SimpleCamera3D camera;
    private final long windowHandle;
    private final float moveStep;
    private final float rotateStep;

    public CameraMovement(long windowHandle, float moveStep, float rotateStep, SimpleCamera3D camera){
        this.windowHandle = windowHandle;
        this.moveStep = moveStep;
        this.rotateStep = rotateStep;
        this.camera = camera;
    }

    public void handleInput() {
        if (windowHandle == 0)
            return;

        Vector3f pos = camera.getPosition();
        Vector3f forward = camera.getForward();
        Vector3f right = camera.getRight();
        Vector3f up = new Vector3f(0, 1, 0);

        if (glfwGetKey(windowHandle, GLFW_KEY_W) == GLFW_PRESS)
            pos.add(new Vector3f(forward).mul(moveStep));
        if (glfwGetKey(windowHandle, GLFW_KEY_S) == GLFW_PRESS)
            pos.sub(new Vector3f(forward).mul(moveStep));
        if (glfwGetKey(windowHandle, GLFW_KEY_A) == GLFW_PRESS)
            pos.sub(new Vector3f(right).mul(moveStep));
        if (glfwGetKey(windowHandle, GLFW_KEY_D) == GLFW_PRESS)
            pos.add(new Vector3f(right).mul(moveStep));
        if (glfwGetKey(windowHandle, GLFW_KEY_SPACE) == GLFW_PRESS)
            pos.add(new Vector3f(up).mul(moveStep));
        if (glfwGetKey(windowHandle, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS)
            pos.sub(new Vector3f(up).mul(moveStep));

        camera.setPosition(pos);
        if (glfwGetKey(windowHandle, GLFW_KEY_Q) == GLFW_PRESS)
            camera.rotate(rotateStep, 0f);
        if (glfwGetKey(windowHandle, GLFW_KEY_E) == GLFW_PRESS)
            camera.rotate(-rotateStep, 0f);
    }
}
