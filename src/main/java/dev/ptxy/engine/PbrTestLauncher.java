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
    private boolean initiated = false;

    private final DirectionalLight light = new DirectionalLight(new Vector3f(0f, -1f, 0f), new Vector3f(5, 5, 5));
    private final SimpleCamera3D camera = new SimpleCamera3D((float) Math.toRadians(60f), 800f / 600f, 0.1f, 100f);

    private CameraMovement cameraMovement;

    private SceneNode grass;
    private SceneNode ground;

    @Override
    public void renderScene() {
        if (!initiated) {
            initiated = true;
            cameraMovement = new CameraMovement(GLFW.glfwGetCurrentContext(), 0.05f, (float) Math.toRadians(1), camera);
            instanceObjects();
        }
        cameraMovement.handleInput();
        renderObjects();
    }

    private void instanceObjects(){
        SceneNodeRegistry.preloadAssets();
        grass = SceneNodeRegistry.instantiate("grass_medium_01_mid_a_LOD0", "Grass");
        var groundAsset = dev.ptxy.engine.objects.Quad.create(20f, new org.joml.Vector3f(97 / 255f, 58 / 255f, 25 / 255f));
        SceneNodeRegistry.loadAssetFromAsset("Ground", groundAsset);
        ground = SceneNodeRegistry.instantiate("Ground", "GroundNode");
    }

    private void renderObjects(){
        grass.render(new Matrix4f().identity(), camera, light);
        grass.render(MovementUtility.setPosition(new Matrix4f().identity(), 1.0f, 0.0f, 0.0f), camera, light);
        grass.render(MovementUtility.setPosition(new Matrix4f().identity(), -1.0f, 0.0f, 0.0f), camera, light);
        grass.render(MovementUtility.setPosition(new Matrix4f().identity(), 0.0f, 0.0f, -1.0f), camera, light);
        ground.render(new Matrix4f().identity(), camera, light);
    }


    public PbrTestLauncher() {
        camera.setPosition(new Vector3f(0f, 0f, 5f));
    }

    public static void main(String[] args) {
        new Core().run(new PbrTestLauncher());
    }
}
