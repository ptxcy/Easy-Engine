package dev.ptxy.engine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BiomeTest {

    @Test
    void ordinalsUsedByVegetationFragmentShaderStayStable() {
        assertEquals(0, Biome.TUNDRA_STEPPE.ordinal());
        assertEquals(4, Biome.SAVANNA_PRAIRIE.ordinal());
        assertEquals(5, Biome.DECIDUOUS_FOREST.ordinal());
        assertEquals(9, Biome.ALPINE.ordinal());
    }

    @Test
    void fromCellReturnsMatchingBiome() {
        assertEquals(Biome.SAVANNA_PRAIRIE, Biome.fromCell(1, 1));
    }

    @Test
    void fromCellThrowsForUnknownCell() {
        assertThrows(IllegalArgumentException.class, () -> Biome.fromCell(5, 5));
    }
}
