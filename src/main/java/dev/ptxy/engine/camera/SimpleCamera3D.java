package dev.ptxy.engine.camera;

import static org.lwjgl.glfw.GLFW.*;

import dev.ptxy.engine.world.WorldPosition;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class SimpleCamera3D {
    private static final Logger log = LogManager.getLogger(SimpleCamera3D.class);

    private static final float ROTATION_REST_THRESHOLD = 0.05f;
    private static final float ROTATION_REST_TIME = 0.25f;

    private final Matrix4f projection;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Vector3f position = new Vector3f(0, 0, 5);

    @Getter private float yaw = (float) -Math.PI / 2;

    private float pitch = 0f;
    @Getter private boolean rotating = false;
    private float rotationRestTimer = 0f;

    private WorldPosition attachment = null;

    public SimpleCamera3D(float fov, float aspect, float near, float far) {
        projection = new Matrix4f().perspective(fov, aspect, near, far);
        updateView();
        log.debug(
                "Camera created (fov={}°, near={}, far={})", (int) Math.toDegrees(fov), near, far);
    }

    public void attachTo(WorldPosition target) {
        this.attachment = target;
        log.info("Camera attached to {}", target.getClass().getSimpleName());
        updateView();
    }

    public void handleInput(long windowHandle, float rotateStep, float deltaTime) {
        float previousYaw = yaw;
        if (glfwGetKey(windowHandle, GLFW_KEY_Q) == GLFW_PRESS) rotate(-rotateStep * deltaTime, 0f);
        if (glfwGetKey(windowHandle, GLFW_KEY_E) == GLFW_PRESS) rotate(rotateStep * deltaTime, 0f);
        updateView();
        updateRotationState(previousYaw, deltaTime);
    }

    private void updateRotationState(float previousYaw, float deltaTime) {
        if (deltaTime <= 0f) return;
        if (Math.abs(yaw - previousYaw) / deltaTime > ROTATION_REST_THRESHOLD) {
            rotating = true;
            rotationRestTimer = 0f;
            return;
        }
        rotationRestTimer += deltaTime;
        if (rotationRestTimer >= ROTATION_REST_TIME) {
            rotating = false;
        }
    }

    public void rotate(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch += deltaPitch;
        pitch = Math.clamp(pitch, (float) -Math.PI / 2 + 0.01f, (float) Math.PI / 2 - 0.01f);
        updateView();
    }

    private void updateView() {
        Vector3f pos = resolvePosition();
        Vector3f front = getForward();
        Vector3f right = front.cross(new Vector3f(0, 1, 0), new Vector3f()).normalize();
        Vector3f up = right.cross(front, new Vector3f()).normalize();
        viewMatrix.identity();
        viewMatrix.lookAt(pos, pos.add(front, new Vector3f()), up);
    }

    private Vector3f resolvePosition() {
        return attachment != null
                ? new Vector3f(attachment.getX(), attachment.getY(), attachment.getZ())
                : new Vector3f(position);
    }

    public Vector3f getForward() {
        return new Vector3f(
                        (float) (Math.cos(pitch) * Math.cos(yaw)),
                        (float) Math.sin(pitch),
                        (float) (Math.cos(pitch) * Math.sin(yaw)))
                .normalize();
    }

    public Vector3f getPosition() {
        return resolvePosition();
    }

    public Matrix4f getViewMatrix() {
        return new Matrix4f(viewMatrix);
    }

    public Matrix4f getProjection() {
        return new Matrix4f(projection);
    }
}
