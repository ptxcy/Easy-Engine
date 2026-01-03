package dev.ptxy.engine.demos.mesh.basic;

import dev.ptxy.engine.camera.Camera;
import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.core.GameWindow;
import dev.ptxy.engine.core.SceneRenderer;
import dev.ptxy.engine.objects.Triangle;
import dev.ptxy.engine.objects.TriangleMesh;
import dev.ptxy.engine.shader.Texture;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class BasicColorAndTextureMeshScene implements SceneRenderer {
    float aspect = (float) GameWindow.getActiveWindow().getWidth() /
        (float) GameWindow.getActiveWindow().getHeight();
    private final Camera camera = new SimpleCamera3D(
        (float)Math.toRadians(90),
        aspect,
        0.1f,
        1000f
    );

    @Override
    public void renderScene() {
        // --- Textured Mesh (Viereck) ---
        Texture texture = new Texture("");
        TriangleMesh texturedMesh = new TriangleMesh(texture);
        texturedMesh.translate(-2.5f,0,0).scale(0.005f, 0.005f, 1.0f);

        // --- Colored Mesh (Viereck aus Grün + Rot) ---
        List<Triangle> coloredTriangles = new ArrayList<>();

        // Erstes Dreieck Grün
        Vector3f[] c1 = {new Vector3f(1, -1, 0), new Vector3f(3, -1, 0), new Vector3f(1, 1, 0)};
        coloredTriangles.add(new Triangle(c1, new Vector3f(0,1,0)));

        // Zweites Dreieck Rot
        Vector3f[] c2 = {new Vector3f(3, -1, 0), new Vector3f(3, 1, 0), new Vector3f(1, 1, 0)};
        coloredTriangles.add(new Triangle(c2, new Vector3f(1,0,0)));

        TriangleMesh coloredMesh = new TriangleMesh(coloredTriangles);

        // --- Render beide Vierecke ---
        texturedMesh.render(camera);
        coloredMesh.render(camera);
    }
}
