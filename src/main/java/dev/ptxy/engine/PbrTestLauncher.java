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
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;
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

    // SPEED_RATE ist eine relative Änderung pro Sekunde (exponentielle Rampe für alle vier
    // Pfeiltasten einheitlich) -- 1.0f verdoppelt/halbiert moveStep/rotateStep ungefähr alle
    // 0.7s bei gehaltener Taste.
    private static final float SPEED_RATE = PLAYER_CONFIG.speedRate();

    private float moveStep = PLAYER_CONFIG.moveStep();
    private float rotateStep = (float) Math.toRadians(PLAYER_CONFIG.rotateStepDegrees());

    private boolean initiated = false;
    private long windowHandle;

    private final boolean[] debugKeyWasDown = new boolean[4];
    private boolean editorToggleKeyWasDown = false;
    private boolean infoToggleKeyWasDown = false;
    private boolean debugDumpKeyWasDown = false;

    // Richtung hatte bisher (0,-1,0) -- Sonne exakt im Zenit, senkrecht von oben. Damit ist
    // NdotL = N.y, also nur von der puren Hangneigung abhängig, nie von der Ausrichtung eines
    // Hangs zur Sonne -- ein Ost- und ein Westhang gleicher Steilheit sahen identisch hell aus,
    // das Relief wirkte deshalb flach. Mit horizontaler Komponente (schräger Sonnenstand)
    // entsteht tatsächliche Licht/Schatten-Zeichnung der Form.
    private final DirectionalLight light =
            new DirectionalLight(
                    new Vector3f(0.45f, -0.75f, 0.35f).normalize(),
                    new Vector3f(1.0f, 0.95f, 0.8f));

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

        boolean editorToggleKeyDown =
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_P) == GLFW.GLFW_PRESS;
        if (editorToggleKeyDown && !editorToggleKeyWasDown) editor.setOpen(!editor.isOpen());
        editorToggleKeyWasDown = editorToggleKeyDown;

        boolean infoToggleKeyDown =
                GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_I) == GLFW.GLFW_PRESS;
        if (infoToggleKeyDown && !infoToggleKeyWasDown) editor.setInfoOpen(!editor.isInfoOpen());
        infoToggleKeyWasDown = infoToggleKeyDown;

        // Solange der Editor offen ist, geht Tastatur-Input ans Panel statt an Kamera/Debug-
        // Modi (sonst würde z.B. Tippen in ein Zahlenfeld gleichzeitig den Spieler bewegen).
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

            int[] debugKeys = {GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4};
            for (int m = 0; m < debugKeys.length; m++) {
                boolean down = GLFW.glfwGetKey(windowHandle, debugKeys[m]) == GLFW.GLFW_PRESS;
                if (down && !debugKeyWasDown[m]) chunkManager.setDebugMode(m);
                debugKeyWasDown[m] = down;
            }

            boolean debugDumpKeyDown =
                    GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
            if (debugDumpKeyDown && !debugDumpKeyWasDown) dumpDebugInfo();
            debugDumpKeyWasDown = debugDumpKeyDown;

            player.update(windowHandle, rotateStep, deltaTime);
        }

        Vector3f lookDir = player.getCamera().getForward();
        chunkManager.update(player.getX(), player.getZ(), lookDir.x, lookDir.z);

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

    // Debug-Hilfsmittel (Taste K): schreibt Spielerposition, Kamera-Ausrichtung und ein lokales
    // Höhen-/Gewichts-Gitter um den Spieler nach /tmp/easy-engine-debug.json -- damit lassen sich
    // gemeldete Stellen (z.B. eine sichtbare Kante) anhand echter Live-Koordinaten des laufenden
    // Spiels nachvollziehen, statt die Position von außen schätzen zu müssen.
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
                sample.addProperty("rainforest", weights.rainforest());
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
        SceneNodeRegistry.preloadAssets();
        chunkManager = new ChunkManager(0);
    }

    private void renderObjects() {
        SimpleCamera3D camera = player.getCamera();
        chunkManager.renderAll(camera, light);
        chunkManager.renderVegetation(camera, light);
        editor.render();
    }

    @Override
    public void shutdown() {
        editor.shutdown();
        chunkManager.shutdown();
    }

    public PbrTestLauncher() {}

    public static void main(String[] args) {
        new Core().run(new PbrTestLauncher());
    }
}
