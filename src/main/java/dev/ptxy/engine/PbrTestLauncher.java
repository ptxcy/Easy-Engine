package dev.ptxy.engine;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.core.Core;
import dev.ptxy.engine.core.SceneRenderer;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.light.PointLight;
import dev.ptxy.engine.objects.MovementUtility;
import dev.ptxy.engine.objects.SceneNode;
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import static org.lwjgl.glfw.GLFW.*;

public class PbrTestLauncher implements SceneRenderer {
    private final DirectionalLight light = new DirectionalLight(new Vector3f(0f, -1f, 0f), new Vector3f(5, 5, 5));
    private final SimpleCamera3D camera = new SimpleCamera3D((float) Math.toRadians(60f), 800f / 600f, 0.1f, 100f);
    private long windowHandle;
    private final float moveStep = 0.05f;
    private final float rotateStep = (float) Math.toRadians(1);
    private boolean initiated = false;

    private SceneNode grass;
    private SceneNode ground;

    @Override
    public void renderScene() {
        if (windowHandle == 0)
            windowHandle = GLFW.glfwGetCurrentContext();
        if (!initiated) {
            initiated = true;

            SceneNodeRegistry.preloadAssets();
            grass = SceneNodeRegistry.instantiate("grass_medium_01_mid_a_LOD0", "Grass");
            var groundAsset = dev.ptxy.engine.objects.Quad.create(20f, new org.joml.Vector3f(97 / 255f, 58 / 255f, 25 / 255f));
            SceneNodeRegistry.loadAssetFromAsset("Ground", groundAsset);
            ground = SceneNodeRegistry.instantiate("Ground", "GroundNode");
        }

        grass.render(new Matrix4f().identity(), camera, light);
        grass.render(MovementUtility.setPosition(new Matrix4f().identity(), 1.0f, 0.0f, 0.0f), camera, light);
        grass.render(MovementUtility.setPosition(new Matrix4f().identity(), -1.0f, 0.0f, 0.0f), camera, light);
        grass.render(MovementUtility.setPosition(new Matrix4f().identity(), 0.0f, 0.0f, -1.0f), camera, light);

        ground.render(new Matrix4f().identity(), camera, light);
        handleInput();
    }

    private void handleInput() {
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

    public PbrTestLauncher() {
        camera.setPosition(new Vector3f(0f, 0f, 5f));
    }

    public static void main(String[] args) {
        new Core().run(new PbrTestLauncher());
    }
}
