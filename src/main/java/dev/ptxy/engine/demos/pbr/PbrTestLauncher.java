package dev.ptxy.engine.demos.pbr;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.core.Core;
import dev.ptxy.engine.core.SceneRenderer;
import dev.ptxy.engine.objects.MasterMesh;
import dev.ptxy.engine.objects.Triangle;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class PbrTestLauncher implements SceneRenderer {
    PointLight light = new PointLight(new Vector3f(0f, 1f, 0f), new Vector3f(5, 5, 5));
    private List<MasterMesh> triangles;
    private final SimpleCamera3D camera = new SimpleCamera3D((float) Math.toRadians(60f), 800f / 600f, 0.1f, 100f);
    private long windowHandle;

    private final float moveStep = 0.05f;
    private final float rotateStep = (float) Math.toRadians(1);

    public PbrTestLauncher() {
        camera.setPosition(new Vector3f(0f, 0f, 5f));
    }

    @Override
    public void renderScene( ) {
        if (windowHandle == 0) windowHandle = GLFW.glfwGetCurrentContext();

        handleInput();

        if (triangles == null) {
            String absolutChessPath = "/Users/pkl/git clones/Easy-Engine/src/main/resources/dev/ptxy/engine/objects/grass_medium_01_4k.gltf";
            triangles = new ArrayList<>();
            triangles.add(GLTFLoader.loadScene(absolutChessPath));

            // --- Flacher Boden ---
            float size = 50f;
            Vector3f v0 = new Vector3f(-size, 0, -size);
            Vector3f v1 = new Vector3f(size, 0, -size);
            Vector3f v2 = new Vector3f(size, 0, size);
            Vector3f v3 = new Vector3f(-size, 0, size);

            Vector3f normal = new Vector3f(0, 1, 0);
            Vector2f uv0 = new Vector2f(0,0);
            Vector2f uv1 = new Vector2f(1,0);
            Vector2f uv2 = new Vector2f(1,1);
            Vector2f uv3 = new Vector2f(0,1);

            Triangle t1 = new Triangle(new Vector3f[]{v0,v1,v2}, new Vector3f[]{normal,normal,normal}, new Vector2f[]{uv0,uv1,uv2});
            Triangle t2 = new Triangle(new Vector3f[]{v0,v2,v3}, new Vector3f[]{normal,normal,normal}, new Vector2f[]{uv0,uv2,uv3});

            MasterMesh floor = new MasterMesh(List.of(t1,t2));

            // Material braun, reichhaltig
            Material floorMat = new Material();
            floorMat.setAlbedo(new Vector3f(0.45f, 0.30f, 0.15f)); // dunkles, sattes Braun
            floorMat.setMetallic(0f);    // keine Spiegelung
            floorMat.setRoughness(0.8f); // leicht rau, realistisch
            floorMat.setAo(1f);          // volle Umgebungseinfluss
            floor.setMaterial(floorMat);

            triangles.add(floor);
        }

        for (MasterMesh tri : triangles) {
            tri.renderAllRecursive(camera, light);
        }
    }

    private void handleInput() {
        if (windowHandle == 0) return;

        Vector3f pos = camera.getPosition();
        Vector3f forward = camera.getForward();
        Vector3f right = camera.getRight();
        Vector3f up = new Vector3f(0,1,0);

        // Bewegung
        if (glfwGetKey(windowHandle, GLFW_KEY_W) == GLFW_PRESS) pos.add(new Vector3f(forward).mul(moveStep));
        if (glfwGetKey(windowHandle, GLFW_KEY_S) == GLFW_PRESS) pos.sub(new Vector3f(forward).mul(moveStep));
        if (glfwGetKey(windowHandle, GLFW_KEY_A) == GLFW_PRESS) pos.sub(new Vector3f(right).mul(moveStep));
        if (glfwGetKey(windowHandle, GLFW_KEY_D) == GLFW_PRESS) pos.add(new Vector3f(right).mul(moveStep));
        if (glfwGetKey(windowHandle, GLFW_KEY_SPACE) == GLFW_PRESS) pos.add(new Vector3f(up).mul(moveStep));
        if (glfwGetKey(windowHandle, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) pos.sub(new Vector3f(up).mul(moveStep));

        camera.setPosition(pos);

        // Rotation Q/E
        if (glfwGetKey(windowHandle, GLFW_KEY_Q) == GLFW_PRESS) camera.rotate(rotateStep, 0f);
        if (glfwGetKey(windowHandle, GLFW_KEY_E) == GLFW_PRESS) camera.rotate(-rotateStep, 0f);
    }

    public static void main(String[] args) {
        new Core().run(new PbrTestLauncher());
    }
}
