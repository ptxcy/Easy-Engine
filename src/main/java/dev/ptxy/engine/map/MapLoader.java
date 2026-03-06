package dev.ptxy.engine.map;

import de.articdive.jnoise.core.api.functions.Interpolation;
import de.articdive.jnoise.generators.noise_parameters.fade_functions.FadeFunction;
import de.articdive.jnoise.pipeline.JNoise;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.objects.SceneNode;
import dev.ptxy.engine.shader.Texture;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public class MapLoader {
    private static final int MAP_SIZE = Config.getConfigJson().getAsJsonObject("groundConfig").get("size").getAsInt();

    private static final Texture GRASS_GROUND_NOISE_TEXTURE;

    static {
        GRASS_GROUND_NOISE_TEXTURE = generateNoiseTexture(0);
    }

    public static SceneNode generateGroundForMap(long seed){
        return new GroundGenerator().generateChunkOfGround(seed,GRASS_GROUND_NOISE_TEXTURE,MAP_SIZE,1000);
    }

    private static Texture generateNoiseTexture(long seed) {
        JNoise noise = JNoise.newBuilder()
                .perlin(seed, Interpolation.LINEAR, FadeFunction.QUINTIC_POLY)
                .build();

        ByteBuffer buffer = BufferUtils.createByteBuffer(MAP_SIZE * MAP_SIZE * 4);

        double scale = Config.getConfigJson().getAsJsonObject("groundConfig").get("noiseScale").getAsFloat();
        System.out.println(scale);
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {

                double nx = (double)x / MAP_SIZE * scale;
                double ny = (double)y / MAP_SIZE * scale;

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

        return new Texture(MAP_SIZE, MAP_SIZE, buffer);
    }
}
