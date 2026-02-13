package dev.ptxy.engine.objects;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import dev.ptxy.engine.objects.assets.Asset;
import dev.ptxy.engine.objects.properties.Material;

public class Quad {

    public static Asset create(float size, Vector3f color) {
        float half = size / 2f;

        // Vertices
        Vector3f v0 = new Vector3f(-half, 0, -half); // links hinten
        Vector3f v1 = new Vector3f( half, 0, -half); // rechts hinten
        Vector3f v2 = new Vector3f(-half, 0,  half); // links vorne
        Vector3f v3 = new Vector3f( half, 0,  half); // rechts vorne

        // Normale nach oben
        Vector3f n = new Vector3f(0, 1, 0);

        // Dreiecke korrekt CCW für +Y Normal
        Triangle t1 = new Triangle(
            new Vector3f[]{v0, v2, v1}, // Drehrichtung gegen Uhrzeigersinn
            new Vector3f[]{n, n, n},
            new org.joml.Vector2f[]{new org.joml.Vector2f(), new org.joml.Vector2f(), new org.joml.Vector2f()}
        );

        Triangle t2 = new Triangle(
            new Vector3f[]{v2, v3, v1}, // CCW
            new Vector3f[]{n, n, n},
            new org.joml.Vector2f[]{new org.joml.Vector2f(), new org.joml.Vector2f(), new org.joml.Vector2f()}
        );

        // Triangle-Liste
        List<Triangle> tris = new ArrayList<>();
        tris.add(t1);
        tris.add(t2);

        // Material
        Material mat = new Material();
        mat.setAlbedo(color);
        List<Material> mats = new ArrayList<>();
        mats.add(mat);

        return new Asset("quad", tris, mats, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
}
