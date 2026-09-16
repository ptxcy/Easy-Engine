package dev.ptxy.engine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.ptxy.engine.map.Biome;
import org.junit.jupiter.api.Test;

class VegetationConfigTest {

    @Test
    void typeThrowsForUnknownName() {
        VegetationConfig config = Config.getVegetationConfig();

        assertThrows(IllegalArgumentException.class, () -> config.type("does-not-exist"));
    }

    @Test
    void coverageDefaultsToZeroForUnlistedBiome() {
        VegetationConfig config = Config.getVegetationConfig();
        VegetationConfig.VegetationType grass = config.type("grass");

        assertEquals(0.0, grass.coverage(Biome.HOT_DESERT));
    }
}
