package dev.ptxy.engine;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.core.Core;
import dev.ptxy.engine.core.SceneRenderer;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.map.MapLoader;
import dev.ptxy.engine.objects.MovementUtility;
import dev.ptxy.engine.objects.SceneNode;
import dev.ptxy.engine.objects.assets.AssetType;
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;
import dev.ptxy.engine.world.Player;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class PbrTestLauncher implements SceneRenderer {
    private static final Logger log = LogManager.getLogger(PbrTestLauncher.class);

    private static final float ROTATE_STEP = (float) Math.toRadians(1);
    private static final float SPEED_STEP = 0.005f;
    private static final float MIN_SPEED = 0.005f;
    private static final float MAX_SPEED = 0.5f;

    private float moveStep = 0.05f;

    private boolean initiated = false;
    private long windowHandle;

    private final DirectionalLight light =
            new DirectionalLight(new Vector3f(0f, -1f, 0f), new Vector3f(1.0f, 0.95f, 0.8f));
    private final SimpleCamera3D camera =
            new SimpleCamera3D((float) Math.toRadians(60f), 800f / 600f, 0.1f, 10000f);

    private Player player;
    private SceneNode grass;
    private SceneNode ground;

    private final List<Matrix4f> grassTransforms = new ArrayList<>();

    @Override
    public void renderScene() {
        if (!initiated) {
            initiated = true;
            windowHandle = GLFW.glfwGetCurrentContext();
            player = new Player(0f, 0f, 5f, moveStep);
            camera.attachTo(player);
            instanceObjects();
            generateGrassTransforms();
        }

        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS) {
            moveStep = Math.min(moveStep + SPEED_STEP, MAX_SPEED);
            player.setMoveStep(moveStep);
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS) {
            moveStep = Math.max(moveStep - SPEED_STEP, MIN_SPEED);
            player.setMoveStep(moveStep);
        }

        player.update(windowHandle, camera.getYaw());
        camera.handleInput(windowHandle, moveStep, ROTATE_STEP);

        renderObjects();
    }

    private void instanceObjects() {
        log.info("Loading scene objects");
        SceneNodeRegistry.preloadAssets();
        grass = SceneNodeRegistry.instantiate("flat_grass", "Grass");
        grass.getAsset().setType(AssetType.GRASS);
        ground = MapLoader.generateMap(0);
    }

    private void generateGrassTransforms() {
        int grassCount = 100;
        log.debug("Generating {} grass instances", grassCount);
        int size = 10;
        for (int i = 0; i < grassCount; i++) {
            float x = (float) Math.random() * size;
            float z = (float) Math.random() * size;
            float y = 0.3f;

            Matrix4f transform = new Matrix4f().identity();
            transform.scale(0.5f, 0.5f, 0.5f);
            float rotY = (float) (Math.random() * Math.PI * 2);
            transform.rotateY(rotY);
            transform.rotateX((float) (Math.PI / 2));
            float maxDeg = 10f;
            float rotX = (float) ((Math.random() * 2.0 - 1.0) * Math.toRadians(maxDeg));
            transform.rotateX(rotX);
            transform = MovementUtility.setPosition(transform, x, y, z);
            grassTransforms.add(transform);
        }
    }

    private void renderObjects() {
        for (Matrix4f transform : grassTransforms) {
            grass.render(transform, camera, light);
        }
        ground.render(new Matrix4f().identity(), camera, light);
    }

    public PbrTestLauncher() {}

    public static void main(String[] args) {
        new Core().run(new PbrTestLauncher());
    }
}
