package dev.ptxy.engine.world;

import static org.lwjgl.glfw.GLFW.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

public class Player implements WorldPosition {
    private static final Logger log = LogManager.getLogger(Player.class);

    private float x, y, z;
    private float moveStep;

    public Player(float x, float y, float z, float moveStep) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.moveStep = moveStep;
        log.info("Player spawned at ({}, {}, {})", x, y, z);
    }

    public void update(long windowHandle, float cameraYaw) {
        float fx = (float) Math.cos(cameraYaw);
        float fz = (float) Math.sin(cameraYaw);
        float rx = -fz;
        float rz = fx;

        if (glfwGetKey(windowHandle, GLFW_KEY_W) == GLFW_PRESS) {
            x += fx * moveStep;
            z += fz * moveStep;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_S) == GLFW_PRESS) {
            x -= fx * moveStep;
            z -= fz * moveStep;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_A) == GLFW_PRESS) {
            x -= rx * moveStep;
            z -= rz * moveStep;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_D) == GLFW_PRESS) {
            x += rx * moveStep;
            z += rz * moveStep;
        }
        if (glfwGetKey(windowHandle, GLFW_KEY_SPACE) == GLFW_PRESS) y += moveStep;
        if (glfwGetKey(windowHandle, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) y -= moveStep;
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

    public void setMoveStep(float moveStep) {
        this.moveStep = moveStep;
    }

    public Vector3f getPositionVec() {
        return new Vector3f(x, y, z);
    }
}
