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

    public static SceneNode generateMap(long seed){
        return new MeadowsBiomeLoader().generateMeadows(seed, MAP_SIZE);
    }
}
