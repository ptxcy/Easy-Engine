package dev.ptxy.engine.objects.assets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ptxy.engine.gltf.GLTFLoader;
import dev.ptxy.engine.camera.Camera;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.light.PointLight;
import dev.ptxy.engine.objects.SceneNode;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class SceneNodeRegistry {

    private static final Map<String, SceneNode> availableNodes = new HashMap<>();
    private static final Map<String, SceneNode> activeNodes = new HashMap<>();

    /**
     * Lädt ein GLTF Asset und registriert die SceneNodes.
     */
    public static void loadAsset(String path) {
        Map<String, SceneNode> nodes = GLTFLoader.loadSceneNodes(path);
        nodes.forEach((name, node) -> {
            availableNodes.put(name, node);
            System.out.println("[SceneNodeRegistry] Loaded prototype node: " + name);
        });
    }

    public static SceneNode getAvailable(String name) {
        SceneNode node = availableNodes.get(name);
        if (node == null) throw new RuntimeException("Available Node not found: " + name);
        return node;
    }

    public static SceneNode instantiate(String name, String instanceId) {
        SceneNode proto = getAvailable(name);
        SceneNode instance = proto.cloneNode();
        activeNodes.put(instanceId, instance);
        System.out.println("[SceneNodeRegistry] Activated node: " + instanceId + " (from prototype: " + name + ")");
        return instance;
    }

    public static void deactivate(String instanceId) {
        if (!activeNodes.containsKey(instanceId)) {
            throw new RuntimeException("Active Node not found: " + instanceId);
        }
        activeNodes.remove(instanceId);
        System.out.println("[SceneNodeRegistry] Deactivated node: " + instanceId);
    }

    public static boolean isActive(String instanceId) {
        return activeNodes.containsKey(instanceId);
    }

    public static void renderAllActive(Camera camera, DirectionalLight light) {
        for (SceneNode node : activeNodes.values()) {
            node.render(new org.joml.Matrix4f().identity(), camera, light);
        }
    }

    /**
     * Registriert ein bereits vorhandenes Asset als verfügbaren SceneNode.
     *
     * @param name Name für die Node
     * @param asset Das Asset, das als Node verfügbar sein soll
     */
    public static void loadAssetFromAsset(String name, Asset asset) {
        SceneNode node = new SceneNode(asset);
        availableNodes.put(name, node);
        System.out.println("[SceneNodeRegistry] Loaded prototype node from Asset: " + name);
    }

    /**
     * Lädt alle Assets aus resources/SceneConfig.json
     */
    public static void preloadAssets() {
        //TODO This needs to be multithreaded to reduce setup times GLFW Context must be set
        try (Reader reader = new InputStreamReader(
                SceneNodeRegistry.class.getResourceAsStream("/SceneConfig.json"),
                StandardCharsets.UTF_8
        )) {
            if (reader == null) throw new RuntimeException("SceneConfig.json not found in resources!");

            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            JsonArray arr = json.getAsJsonArray("preloadAssets");

            for (int i = 0; i < arr.size(); i++) {
                String path = arr.get(i).getAsString();
                loadAsset(path);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to preload assets from SceneConfig.json", e);
        }
    }
}
