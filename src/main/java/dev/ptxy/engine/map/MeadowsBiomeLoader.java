package dev.ptxy.engine.map;

import de.articdive.jnoise.core.api.functions.Interpolation;
import de.articdive.jnoise.generators.noise_parameters.fade_functions.FadeFunction;
import de.articdive.jnoise.pipeline.JNoise;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.objects.SceneNode;
import dev.ptxy.engine.objects.Triangle;
import dev.ptxy.engine.objects.assets.Asset;
import dev.ptxy.engine.objects.assets.AssetBuilder;
import dev.ptxy.engine.objects.assets.AssetType;
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;
import dev.ptxy.engine.shader.Texture;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MeadowsBiomeLoader {
    private int BIOME_SIZE;

    public SceneNode generateMeadows(long seed, int size) {
        BIOME_SIZE = size;

        return generateMeadowGround(seed, generateNoiseTexture(seed), size, Config.getConfigJson().getAsJsonObject("groundConfig").get("resolution").getAsInt());
    }

    private Texture generateNoiseTexture(long seed) {
        JNoise noise = JNoise.newBuilder()
            .perlin(seed, Interpolation.LINEAR, FadeFunction.QUINTIC_POLY)
            .build();

        ByteBuffer buffer = BufferUtils.createByteBuffer(BIOME_SIZE * BIOME_SIZE * 4);

        double scale = Config.getConfigJson().getAsJsonObject("groundConfig").get("noiseScale").getAsFloat();
        System.out.println(scale);
        for (int y = 0; y < BIOME_SIZE; y++) {
            for (int x = 0; x < BIOME_SIZE; x++) {

                double nx = (double)x / BIOME_SIZE * scale;
                double ny = (double)y / BIOME_SIZE * scale;

                double value = noise.evaluateNoise(nx, ny);
                value = (value + 1.0) * 0.5;
                int byteValue = (int) (value * 255.0);

                buffer.put((byte) byteValue);
                buffer.put((byte) byteValue);
                buffer.put((byte) byteValue);
                buffer.put((byte) 255);
            }
        }

        buffer.flip();

        return new Texture(BIOME_SIZE, BIOME_SIZE, buffer);
    }

    private SceneNode generateMeadowGround(long seed, Texture noise, int size, int resolution) {
        List<Asset> referenceAssets = new ArrayList<>();
        referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref").getAsset());
        referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref_two").getAsset());
        referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref_three").getAsset());

        Texture diffuseTexOne = referenceAssets.getFirst().getBaseColors().getFirst();
        Texture diffuseTexTwo = referenceAssets.get(1).getBaseColors().getFirst();
        Texture diffuseTexThree = referenceAssets.get(2).getBaseColors().getFirst();


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
