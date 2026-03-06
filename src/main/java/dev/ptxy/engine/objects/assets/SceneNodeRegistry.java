package dev.ptxy.engine.objects.assets;

import com.google.gson.JsonArray;
import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.gltf.GLTFLoader;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.objects.SceneNode;

import java.util.HashMap;
import java.util.Map;

public final class SceneNodeRegistry {
    private static final Map<String, SceneNode> availableNodes = new HashMap<>();
    private static final Map<String, SceneNode> activeNodes = new HashMap<>();

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

    public static void renderAllActive(SimpleCamera3D camera, DirectionalLight light) {
        for (SceneNode node : activeNodes.values()) {
            node.render(new org.joml.Matrix4f().identity(), camera, light);
        }
    }

    public static void loadAssetFromAsset(String name, Asset asset) {
        SceneNode node = new SceneNode(asset);
        availableNodes.put(name, node);
        System.out.println("[SceneNodeRegistry] Loaded prototype node from Asset: " + name);
    }

    public static void preloadAssets() {
        JsonArray arr = Config.getConfigJson().getAsJsonArray("preloadAssets");
        for (int i = 0; i < arr.size(); i++) {
            String path = arr.get(i).getAsString();
            loadAsset(path);
        }
    }
}
