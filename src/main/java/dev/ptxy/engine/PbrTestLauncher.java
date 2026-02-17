package dev.ptxy.engine;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.core.Core;
import dev.ptxy.engine.core.SceneRenderer;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.light.PointLight;
import dev.ptxy.engine.objects.Ground;
import dev.ptxy.engine.objects.MovementUtility;
import dev.ptxy.engine.objects.SceneNode;
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class PbrTestLauncher implements SceneRenderer {
    private boolean initiated = false;

    private final DirectionalLight light = new DirectionalLight(new Vector3f(0f, -1f, 0f), new Vector3f(1.0f, 0.95f, 0.8f));
    private final SimpleCamera3D camera = new SimpleCamera3D((float) Math.toRadians(60f), 800f / 600f, 0.1f, 100f);

    private CameraMovement cameraMovement;

    private SceneNode grass;
    private Ground ground;

    private final List<Matrix4f> grassTransforms = new ArrayList<>();

    @Override
    public void renderScene() {
        if (!initiated) {
            initiated = true;
            cameraMovement = new CameraMovement(GLFW.glfwGetCurrentContext(), 0.05f, (float) Math.toRadians(1), camera);
            instanceObjects();
            generateGrassTransforms();
        }
        cameraMovement.handleInput();
        renderObjects();
    }

    private void instanceObjects(){
        SceneNodeRegistry.preloadAssets();
        grass = SceneNodeRegistry.instantiate("flat_grass", "Grass");
        ground = Ground.createGround(10,10,5);
    }

    private void generateGrassTransforms() {
        int grassCount = 100;
        int size = 10;
        for (int i = 0; i < grassCount; i++) {
            float x = (float) Math.random() * size;
            float z = (float) Math.random() * size;
            float y = 0.3f;

            Matrix4f transform = new Matrix4f().identity();
            transform.scale(0.5f,0.5f,0.5f);
            float rotY = (float) (Math.random() * Math.PI * 2);
            transform.rotateY(rotY);
            transform.rotateX((float) (Math.PI / 2));
            float maxDeg = 10f;
            float rotX = (float)((Math.random() * 2.0 - 1.0) * Math.toRadians(maxDeg));
            transform.rotateX(rotX);
            transform = MovementUtility.setPosition(transform, x, y, z);
            grassTransforms.add(transform);
        }
    }

    private void renderObjects(){
        ground.render(new Matrix4f().identity(), camera, light, "grass");
        for (Matrix4f transform : grassTransforms) {
            grass.render(transform, camera, light, "grass");
        }
    }

    public PbrTestLauncher() {
        camera.setPosition(new Vector3f(0f, 0f, 5f));
    }

    public static void main(String[] args) {
        new Core().run(new PbrTestLauncher());
    }
}
