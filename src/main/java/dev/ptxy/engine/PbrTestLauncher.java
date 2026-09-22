package dev.ptxy.engine;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.config.PlayerConfig;
import dev.ptxy.engine.core.Core;
import dev.ptxy.engine.core.SceneRenderer;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.map.ChunkManager;
import dev.ptxy.engine.map.Map;
import dev.ptxy.engine.ui.TerrainEditorGui;
import dev.ptxy.engine.world.Player;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class PbrTestLauncher implements SceneRenderer {
    private static final Logger log = LogManager.getLogger(PbrTestLauncher.class);
    private static final PlayerConfig PLAYER_CONFIG = Config.getPlayerConfig();

    private static final float SPEED_RATE = PLAYER_CONFIG.speedRate();

    private float moveStep = PLAYER_CONFIG.moveStep();
    private float rotateStep = (float) Math.toRadians(PLAYER_CONFIG.rotateStepDegrees());

    private boolean initiated = false;
    private long windowHandle;

    private final KeyEdge editorToggleKey = new KeyEdge(GLFW.GLFW_KEY_P);
    private final KeyEdge infoToggleKey = new KeyEdge(GLFW.GLFW_KEY_I);
    private final KeyEdge debugDumpKey = new KeyEdge(GLFW.GLFW_KEY_K);
    private final KeyEdge debugStateKey = new KeyEdge(GLFW.GLFW_KEY_L);
    private final KeyEdge[] debugModeKeys = {
        new KeyEdge(GLFW.GLFW_KEY_1),
        new KeyEdge(GLFW.GLFW_KEY_2),
        new KeyEdge(GLFW.GLFW_KEY_3),
        new KeyEdge(GLFW.GLFW_KEY_4)
    };

    private final DirectionalLight light =
            new DirectionalLight(
                    new Vector3f(0.45f, -0.75f, 0.35f).normalize(),
                    new Vector3f(1.0f, 0.88f, 0.66f));

    private Player player;
    private ChunkManager chunkManager;
    private TerrainEditorGui editor;

    @Override
    public void renderScene(float deltaTime) {
        if (!initiated) {
            initiated = true;
            windowHandle = GLFW.glfwGetCurrentContext();
            SimpleCamera3D camera =
                    new SimpleCamera3D((float) Math.toRadians(60f), 800f / 600f, 0.1f, 10000f);
            player = new Player(0f, 0f, 5f, moveStep, camera);
            instanceObjects();
            editor = new TerrainEditorGui(windowHandle, chunkManager, player);
        }

        if (editorToggleKey.pressed(windowHandle)) editor.setOpen(!editor.isOpen());
        if (infoToggleKey.pressed(windowHandle)) editor.setInfoOpen(!editor.isInfoOpen());

        if (!editor.isOpen()) {
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

            for (int m = 0; m < debugModeKeys.length; m++) {
                if (debugModeKeys[m].pressed(windowHandle)) chunkManager.setDebugMode(m);
            }

            if (debugDumpKey.pressed(windowHandle)) dumpDebugInfo();
            if (debugStateKey.pressed(windowHandle)) chunkManager.logDebugState();

            player.update(windowHandle, rotateStep, deltaTime);
        }

        chunkManager.update(player.getX(), player.getZ(), player.getCamera());

        renderObjects();
    }

    @Override
    public void beforePollEvents() {
        if (editor != null) editor.beginInput();
    }

    @Override
    public void afterPollEvents() {
        if (editor != null) editor.endInput();
    }

    @Override
    public void onFps(int fps) {
        if (editor != null) editor.setFps(fps);
    }

    private static final double DEBUG_DUMP_HALF_EXTENT = 150.0;
    private static final double DEBUG_DUMP_STEP = 5.0;

    private void dumpDebugInfo() {
        Map noiseMap = chunkManager.getNoiseMap();
        float heightAmplitude = Config.getTerrainParams().heightAmplitude();
        SimpleCamera3D camera = player.getCamera();
        Vector3f forward = camera.getForward();

        JsonObject root = new JsonObject();
        JsonObject playerJson = new JsonObject();
        playerJson.addProperty("x", player.getX());
        playerJson.addProperty("y", player.getY());
        playerJson.addProperty("z", player.getZ());
        root.add("player", playerJson);

        JsonObject cameraJson = new JsonObject();
        cameraJson.addProperty("yaw", camera.getYaw());
        JsonObject forwardJson = new JsonObject();
        forwardJson.addProperty("x", forward.x);
        forwardJson.addProperty("y", forward.y);
        forwardJson.addProperty("z", forward.z);
        cameraJson.add("forward", forwardJson);
        root.add("camera", cameraJson);

        root.addProperty("heightAmplitude", heightAmplitude);
        root.addProperty("stepMeters", DEBUG_DUMP_STEP);
        root.addProperty("halfExtentMeters", DEBUG_DUMP_HALF_EXTENT);

        JsonArray samples = new JsonArray();
        for (double dx = -DEBUG_DUMP_HALF_EXTENT;
                dx <= DEBUG_DUMP_HALF_EXTENT;
                dx += DEBUG_DUMP_STEP) {
            for (double dz = -DEBUG_DUMP_HALF_EXTENT;
                    dz <= DEBUG_DUMP_HALF_EXTENT;
                    dz += DEBUG_DUMP_STEP) {
                double wx = player.getX() + dx;
                double wz = player.getZ() + dz;
                double height = noiseMap.getHeight(wx, wz) * heightAmplitude;
                Map.BiomeWeights weights = noiseMap.getBiomeWeights(wx, wz);
                int[] cell = noiseMap.getBiomeCell(wx, wz);

                JsonObject sample = new JsonObject();
                sample.addProperty("x", wx);
                sample.addProperty("z", wz);
                sample.addProperty("height", height);
                sample.addProperty("tundra", weights.tundra());
                sample.addProperty("savanna", weights.savanna());
                sample.addProperty("deciduousForest", weights.deciduousForest());
                sample.addProperty("cellRow", cell[0]);
                sample.addProperty("cellCol", cell[1]);
                samples.add(sample);
            }
        }
        root.add("samples", samples);

        try (Writer writer =
                new FileWriter("/tmp/easy-engine-debug.json", StandardCharsets.UTF_8)) {
            new GsonBuilder().create().toJson(root, writer);
            log.info("Debug-Dump geschrieben nach /tmp/easy-engine-debug.json");
        } catch (IOException e) {
            log.error("Debug-Dump fehlgeschlagen", e);
        }
    }

    private void instanceObjects() {
        log.info("Loading scene objects");
        chunkManager = new ChunkManager(0);
    }

    private void renderObjects() {
        SimpleCamera3D camera = player.getCamera();
        chunkManager.renderAll(camera, light);
        chunkManager.renderVegetation(camera, light, (float) GLFW.glfwGetTime());
        editor.render();
    }

    @Override
    public void shutdown() {
        if (editor != null) editor.shutdown();
        if (chunkManager != null) chunkManager.shutdown();
    }

    public PbrTestLauncher() {}

    public static void main(String[] args) {
        new Core().run(new PbrTestLauncher());
    }
}
