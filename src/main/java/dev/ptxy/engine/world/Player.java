package dev.ptxy.engine.world;

import static org.lwjgl.glfw.GLFW.*;

import dev.ptxy.engine.camera.SimpleCamera3D;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

public class Player implements WorldPosition {
    private static final Logger log = LogManager.getLogger(Player.class);

    private float x, y, z;
    private float moveStep;
    private final SimpleCamera3D camera;

    public Player(float x, float y, float z, float moveStep, SimpleCamera3D camera) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.moveStep = moveStep;
        this.camera = camera;
        camera.attachTo(this);
        log.info("Player spawned at ({}, {}, {})", x, y, z);
    }

    public void update(long windowHandle, float rotateStep, float deltaTime) {
        float yaw = camera.getYaw();
        float fx = (float) Math.cos(yaw);
        float fz = (float) Math.sin(yaw);
        float rx = -fz;
        float rz = fx;
        float step = moveStep * deltaTime;

        if (glfwGetKey(windowHandle, GLFW_KEY_W) == GLFW_PRESS) {
            x += fx * step;
            z += fz * step;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_S) == GLFW_PRESS) {
            x -= fx * step;
            z -= fz * step;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_A) == GLFW_PRESS) {
            x -= rx * step;
            z -= rz * step;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_D) == GLFW_PRESS) {
            x += rx * step;
            z += rz * step;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_SPACE) == GLFW_PRESS) y += step;
        if (glfwGetKey(windowHandle, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) y -= step;

        camera.handleInput(windowHandle, moveStep, rotateStep, deltaTime);
    }

    public SimpleCamera3D getCamera() {
        return camera;
    }

    public void setMoveStep(float moveStep) {
        this.moveStep = moveStep;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public float getZ() {
        return z;
    }

    public Vector3f getPositionVec() {
        return new Vector3f(x, y, z);
    }
}
