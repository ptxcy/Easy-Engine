package dev.ptxy.engine.objects;

import de.articdive.jnoise.core.api.functions.Interpolation;
import de.articdive.jnoise.generators.noise_parameters.fade_functions.FadeFunction;
import de.articdive.jnoise.pipeline.JNoise;
import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.objects.assets.Asset;
import dev.ptxy.engine.objects.assets.AssetBuilder;
import dev.ptxy.engine.objects.assets.AssetType;
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;
import dev.ptxy.engine.shader.Texture;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Ground {
    private static final List<Asset> referenceAssets = new ArrayList<>();

    private final SceneNode groundNode;

    private static final int NOISE_TEXTURE_SIZE = 512;

    public Ground(){
        throw new UnsupportedOperationException("Ground is supposed to be created via static Methods!");
    }

    private Ground(Asset asset){
        this.groundNode = new SceneNode(asset);
    }

    public static Ground generateChunkOfGround(long seed) {
        if(referenceAssets.isEmpty()) {
            referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref").getAsset());
            referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref_two").getAsset());
            referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref_three").getAsset());
        }

        Texture diffuseTexOne = referenceAssets.getFirst().getBaseColors().getFirst();
        Texture diffuseTexTwo = referenceAssets.get(1).getBaseColors().getFirst();
        Texture diffuseTexThree = referenceAssets.get(2).getBaseColors().getFirst();

        Asset groundAsset = new AssetBuilder()
                .startBuildingAssetOfType(AssetType.GROUND)
                .setId("Ground")
                .addBaseColor(diffuseTexOne)
                .addBaseColor(diffuseTexTwo)
                .addBaseColor(diffuseTexThree)
                .setNoiseTexture(generateNoiseTexture(seed))
                .setTriangleMesh(generateGroundMesh(seed))
                .build();

        return new Ground(groundAsset);
    }

    private static List<Triangle> generateGroundMesh(long seed) {

        final int resolution = 10;   // Anzahl Quads pro Seite
        final float size = 10f;      // Weltgröße
        final float step = size / resolution;

        List<Triangle> triangles = new ArrayList<>();

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

                // Triangle 1 (v0, v1, v2)
                triangles.add(new Triangle(
                        new Vector3f[]{v0, v1, v2},
                        new Vector3f[]{normal, normal, normal},
                        new Vector2f[]{uv0, uv1, uv2},
                        null,
                        null
                ));

                // Triangle 2 (v0, v2, v3)
                triangles.add(new Triangle(
                        new Vector3f[]{v0, v2, v3},
                        new Vector3f[]{normal, normal, normal},
                        new Vector2f[]{uv0, uv2, uv3},
                        null,
                        null
                ));
            }
        }

        return triangles;
    }

    private static Texture generateNoiseTexture(long seed) {
        JNoise noise = JNoise.newBuilder()
                .perlin(seed, Interpolation.LINEAR, FadeFunction.QUINTIC_POLY)
                .build();

        ByteBuffer buffer = BufferUtils.createByteBuffer(NOISE_TEXTURE_SIZE * NOISE_TEXTURE_SIZE * 4);

        double scale = 0.05;

        for (int y = 0; y < NOISE_TEXTURE_SIZE; y++) {
            for (int x = 0; x < NOISE_TEXTURE_SIZE; x++) {

                double nx = x * scale;
                double ny = y * scale;

                double value = noise.evaluateNoise(nx, ny);
                value = (value + 1.0) * 0.5; // → [0,1]

                int byteValue = (int)(value * 255.0);

                buffer.put((byte) byteValue);
                buffer.put((byte) byteValue);
                buffer.put((byte) byteValue);
                buffer.put((byte) 255);
            }
        }

        buffer.flip();

        return new Texture(NOISE_TEXTURE_SIZE, NOISE_TEXTURE_SIZE, buffer);
    }

    public void render(Matrix4f transform, SimpleCamera3D camera, DirectionalLight light){
        groundNode.render(transform,camera,light);
    }
}
