package dev.ptxy.engine.map;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.objects.SceneNode;
import dev.ptxy.engine.objects.Triangle;
import dev.ptxy.engine.objects.assets.Asset;
import dev.ptxy.engine.objects.assets.AssetBuilder;
import dev.ptxy.engine.objects.assets.AssetType;
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;
import dev.ptxy.engine.shader.Texture;
import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class GroundGenerator {
    private final Texture diffuseTexOne;
    private final Texture diffuseTexTwo;
    private final Texture diffuseTexThree;

    public GroundGenerator(){
        List<Asset> referenceAssets = new ArrayList<>();
        referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref").getAsset());
        referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref_two").getAsset());
        referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref_three").getAsset());

        diffuseTexOne = referenceAssets.getFirst().getBaseColors().getFirst();
        diffuseTexTwo = referenceAssets.get(1).getBaseColors().getFirst();
        diffuseTexThree = referenceAssets.get(2).getBaseColors().getFirst();
    }

    public SceneNode generateChunkOfGround(long seed, Texture noise, int size, int resolution) {
        Asset groundAsset = new AssetBuilder()
                .startBuildingAssetOfType(AssetType.GROUND)
                .setId("Ground")
                .addBaseColor(diffuseTexOne)
                .addBaseColor(diffuseTexTwo)
                .addBaseColor(diffuseTexThree)
                .setNoiseTexture(noise)
                .setTriangleMesh(generateGroundMesh(seed,size,resolution))
                .build();

        return new SceneNode(groundAsset);
    }

    private static List<Triangle> generateGroundMesh(long seed, int size, int resolution) {
        List<Triangle> triangles = new ArrayList<>();

        final float step = (float) size / resolution;
        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {

                float x0 = x * step;
                float x1 = (x + 1) * step;

                float z0 = z * step;
                float z1 = (z + 1) * step;

                // Positionen
                Vector3f v0 = new Vector3f(x0, 0f, z0);
                Vector3f v1 = new Vector3f(x1, 0f, z0);
                Vector3f v2 = new Vector3f(x1, 0f, z1);
                Vector3f v3 = new Vector3f(x0, 0f, z1);

                // UVs
                Vector2f uv0 = new Vector2f((float)x / resolution, (float)z / resolution);
                Vector2f uv1 = new Vector2f((float)(x+1) / resolution, (float)z / resolution);
                Vector2f uv2 = new Vector2f((float)(x+1) / resolution, (float)(z+1) / resolution);
                Vector2f uv3 = new Vector2f((float)x / resolution, (float)(z+1) / resolution);

                Vector3f normal = new Vector3f(0f, 1f, 0f);

                triangles.add(new Triangle(
                        new Vector3f[]{v0, v2, v1},
                        new Vector3f[]{normal, normal, normal},
                        new Vector2f[]{uv0, uv2, uv1}
                ));

                triangles.add(new Triangle(
                        new Vector3f[]{v0, v3, v2},
                        new Vector3f[]{normal, normal, normal},
                        new Vector2f[]{uv0, uv3, uv2}
                ));
            }
        }

        return triangles;
    }
}
