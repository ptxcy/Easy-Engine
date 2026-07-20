package dev.ptxy.engine;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.config.PlayerConfig;
import dev.ptxy.engine.core.Core;
import dev.ptxy.engine.core.SceneRenderer;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.map.ChunkManager;
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
    private static final PlayerConfig PLAYER_CONFIG = Config.getPlayerConfig();

    // SPEED_RATE ist eine relative Änderung pro Sekunde (exponentielle Rampe für alle vier
    // Pfeiltasten einheitlich) -- 1.0f verdoppelt/halbiert moveStep/rotateStep ungefähr alle
    // 0.7s bei gehaltener Taste.
    private static final float SPEED_RATE = PLAYER_CONFIG.speedRate();

    private float moveStep = PLAYER_CONFIG.moveStep();
    private float rotateStep = (float) Math.toRadians(PLAYER_CONFIG.rotateStepDegrees());

    private boolean initiated = false;
    private long windowHandle;

    private final boolean[] debugKeyWasDown = new boolean[4];

    private final DirectionalLight light =
            new DirectionalLight(new Vector3f(0f, -1f, 0f), new Vector3f(1.0f, 0.95f, 0.8f));

    private Player player;
    private ChunkManager chunkManager;
    private SceneNode grass;

    private final List<Matrix4f> grassTransforms = new ArrayList<>();

    @Override
    public void renderScene(float deltaTime) {
        if (!initiated) {
            initiated = true;
            windowHandle = GLFW.glfwGetCurrentContext();
            SimpleCamera3D camera =
                    new SimpleCamera3D((float) Math.toRadians(60f), 800f / 600f, 0.1f, 10000f);
            player = new Player(0f, 0f, 5f, moveStep, camera);
            instanceObjects();
            generateGrassTransforms();
        }

        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS) {
            moveStep *= (1f + SPEED_RATE * deltaTime);
            player.setMoveStep(moveStep);
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS) {
            moveStep *= (1f - SPEED_RATE * deltaTime);
            player.setMoveStep(moveStep);
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS)
            rotateStep *= (1f + SPEED_RATE * deltaTime);
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS)
            rotateStep *= (1f - SPEED_RATE * deltaTime);

        int[] debugKeys = {GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4};
        for (int m = 0; m < debugKeys.length; m++) {
            boolean down = GLFW.glfwGetKey(windowHandle, debugKeys[m]) == GLFW.GLFW_PRESS;
            if (down && !debugKeyWasDown[m]) chunkManager.setDebugMode(m);
            debugKeyWasDown[m] = down;
        }

        player.update(windowHandle, rotateStep, deltaTime);
        chunkManager.update(player.getX(), player.getZ());

        renderObjects();
    }

    private void instanceObjects() {
        log.info("Loading scene objects");
        SceneNodeRegistry.preloadAssets();
        grass = SceneNodeRegistry.instantiate("flat_grass", "Grass");
        grass.getAsset().setType(AssetType.GRASS);
        chunkManager = new ChunkManager(0);
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
        SimpleCamera3D camera = player.getCamera();
        for (Matrix4f transform : grassTransforms) {
            grass.render(transform, camera, light);
        }
        chunkManager.renderAll(camera, light);
    }

    @Override
    public void shutdown() {
        chunkManager.shutdown();
    }

    public PbrTestLauncher() {}

    public static void main(String[] args) {
        new Core().run(new PbrTestLauncher());
    }
}
