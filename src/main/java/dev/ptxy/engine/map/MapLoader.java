package dev.ptxy.engine.map;

import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.objects.SceneNode;

public class MapLoader {
    private static final int MAP_SIZE = Config.getMapConfigSize();

    public static SceneNode generateMap(long seed) {
        return new MeadowsBiomeLoader().generateMeadows(seed, MAP_SIZE);
    }
}
