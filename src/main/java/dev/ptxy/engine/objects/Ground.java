package dev.ptxy.engine.objects;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.objects.assets.Asset;
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;
import dev.ptxy.engine.objects.properties.Material;
import dev.ptxy.engine.shader.Texture;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class Ground {
    private final SceneNode groundNode;

    public Ground(Asset asset){
        this.groundNode = new SceneNode(asset);
    }

    public void render(Matrix4f transform, SimpleCamera3D camera, DirectionalLight light){
        groundNode.render(transform,camera,light);
    }

    public void render(Matrix4f transform, SimpleCamera3D camera, DirectionalLight light, String shaderName){
        groundNode.render(transform,camera,light,shaderName);
    }

    public static Ground createGround(float width, float height, int divider) {
        String id = "Ground";

        List<Triangle> triangles = new ArrayList<>();

        // GLTF reference
        SceneNode groundReference = SceneNodeRegistry.getAvailable("grass_ground");
        Asset referenceAsset = groundReference.getAsset();
        List<Material> materials = referenceAsset.getMaterials();
        List<Texture> baseColors = referenceAsset.getBaseColors();
        List<Texture> metallicRoughness = referenceAsset.getMetallicRoughness();
        List<Texture> normalMaps = referenceAsset.getNormalMaps();

        // Schrittgröße pro Rasterfeld
        float stepX = width / divider;
        float stepZ = height / divider;

        // UV-Faktor pro Rasterfeld
        float uvStep = 1.0f / divider;

        for (int z = 0; z < divider; z++) {
            for (int x = 0; x < divider; x++) {

                // Eckpunkte des Feldes
                Vector3f v00 = new Vector3f(x * stepX, 0, z * stepZ);
                Vector3f v10 = new Vector3f((x + 1) * stepX, 0, z * stepZ);
                Vector3f v01 = new Vector3f(x * stepX, 0, (z + 1) * stepZ);
                Vector3f v11 = new Vector3f((x + 1) * stepX, 0, (z + 1) * stepZ);

                // UVs
                Vector2f uv00 = new Vector2f(0, 0);
                Vector2f uv10 = new Vector2f(1, 0);
                Vector2f uv01 = new Vector2f(0, 1);
                Vector2f uv11 = new Vector2f(1, 1);

                Vector3f normal = new Vector3f(0, 1, 0);

                triangles.add(createTileTriangle(v00, v01, v10, uv00, uv01, uv10, normal));
                triangles.add(createTileTriangle(v10, v01, v11, uv10, uv01, uv11, normal));
            }
        }

        return new Ground(
                new Asset(id, triangles, materials, baseColors, metallicRoughness, normalMaps)
        );
    }

    private static Triangle createTileTriangle(
            Vector3f v0, Vector3f v1, Vector3f v2,
            Vector2f uv0, Vector2f uv1, Vector2f uv2,
            Vector3f normal
    ) {
        Vector3f edge1 = new Vector3f();
        v1.sub(v0, edge1);
        Vector3f edge2 = new Vector3f();
        v2.sub(v0, edge2);
        Vector2f deltaUV1 = new Vector2f();
        uv1.sub(uv0, deltaUV1);
        Vector2f deltaUV2 = new Vector2f();
        uv2.sub(uv0, deltaUV2);

        float f = 1.0f / (deltaUV1.x * deltaUV2.y - deltaUV2.x * deltaUV1.y);
        Vector3f tangent = new Vector3f();
        tangent.x = f * (deltaUV2.y * edge1.x - deltaUV1.y * edge2.x);
        tangent.y = f * (deltaUV2.y * edge1.y - deltaUV1.y * edge2.y);
        tangent.z = f * (deltaUV2.y * edge1.z - deltaUV1.y * edge2.z);
        tangent.normalize();
        Vector3f bitangent = new Vector3f();
        tangent.set(1,0,0);
        bitangent.set(0,0,1);
        Vector3f[] tangents = new Vector3f[]{tangent, tangent, tangent};
        Vector3f[] bitangents = new Vector3f[]{bitangent, bitangent, bitangent};

        return new Triangle(
                new Vector3f[]{v0, v1, v2},
                new Vector3f[]{normal, normal, normal},
                new Vector2f[]{uv0, uv1, uv2},
                tangents,
                bitangents
        );
    }
}
