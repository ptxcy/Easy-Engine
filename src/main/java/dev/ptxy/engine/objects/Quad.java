package dev.ptxy.engine.objects;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import dev.ptxy.engine.objects.assets.Asset;
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;
import dev.ptxy.engine.objects.properties.Material;

public class Quad {

    public static Asset create(float size, Vector3f color) {
        float half = size / 2f;
        Vector3f v0 = new Vector3f(-half, 0, -half);
        Vector3f v1 = new Vector3f( half, 0, -half);
        Vector3f v2 = new Vector3f(-half, 0,  half);
        Vector3f v3 = new Vector3f( half, 0,  half);


        Vector3f n = new Vector3f(0, 1, 0);
        Triangle t1 = new Triangle(
                new Vector3f[]{v0,v1,v2},
                new Vector3f[]{n,n,n},
                new org.joml.Vector2f[]{new org.joml.Vector2f(), new org.joml.Vector2f(), new org.joml.Vector2f()}
        );

        Triangle t2 = new Triangle(
                new Vector3f[]{v2,v1,v3},
                new Vector3f[]{n,n,n},
                new org.joml.Vector2f[]{new org.joml.Vector2f(), new org.joml.Vector2f(), new org.joml.Vector2f()}
        );

        List<Triangle> tris = new ArrayList<>();
        tris.add(t1);
        tris.add(t2);
        Material mat = new Material();
        mat.setAlbedo(color);

        List<Material> mats = new ArrayList<>();
        mats.add(mat);

        return new Asset("quad", tris, mats, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
}
