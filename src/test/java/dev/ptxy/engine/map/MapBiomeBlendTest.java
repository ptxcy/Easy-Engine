package dev.ptxy.engine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapBiomeBlendTest {

    private static final double EPSILON = 1e-9;

    @Test
    void weightsAreNonNegativeAndSumToOne() {
        Map map = new Map(17L);
        for (double x = -20_000; x <= 20_000; x += 1777.0) {
            double z = x * 0.6;
            Map.BiomeWeights w = map.getBiomeWeights(x, z);
            assertTrue(
                    w.tundra() >= -EPSILON && w.savanna() >= -EPSILON && w.rainforest() >= -EPSILON,
                    "negatives Gewicht bei x=" + x + ": " + w);
            assertEquals(1.0, w.tundra() + w.savanna() + w.rainforest(), 1e-6, "Summe bei x=" + x);
        }
    }

    @Test
    void nearestCellFromGetBiomeCellHasHighestWeight() {
        Map map = new Map(17L);
        for (double x = -20_000; x <= 20_000; x += 1319.0) {
            double z = x * 0.6;
            int[] cell = map.getBiomeCell(x, z);
            Map.BiomeWeights w = map.getBiomeWeights(x, z);
            double weightOfNearest =
                    switch (cell[0] * 3 + cell[1]) {
                        case 0 -> w.tundra();
                        case 4 -> w.savanna();
                        case 8 -> w.rainforest();
                        default -> 0.0;
                    };
            double maxWeight = Math.max(w.tundra(), Math.max(w.savanna(), w.rainforest()));
            assertEquals(
                    maxWeight,
                    weightOfNearest,
                    1e-9,
                    "Gewicht der nächsten Zelle bei x=" + x + " war nicht das größte");
        }
    }

    @Test
    void sampleTerrainMatchesIndividualAccessors() {
        Map map = new Map(17L);
        for (double x = -20_000; x <= 20_000; x += 1531.0) {
            double z = x * 0.6;
            Map.TerrainSample sample = map.sampleTerrain(x, z);

            assertEquals(map.getHeight(x, z), sample.height(), EPSILON, "Höhe bei x=" + x);

            Map.BiomeWeights weights = map.getBiomeWeights(x, z);
            assertEquals(
                    weights.tundra(),
                    sample.weights().tundra(),
                    EPSILON,
                    "Tundra-Gewicht bei x=" + x);
            assertEquals(
                    weights.savanna(),
                    sample.weights().savanna(),
                    EPSILON,
                    "Savanne-Gewicht bei x=" + x);
            assertEquals(
                    weights.rainforest(),
                    sample.weights().rainforest(),
                    EPSILON,
                    "Regenwald-Gewicht bei x=" + x);

            int[] cell = map.getBiomeCell(x, z);
            assertEquals(cell[0], sample.nearestCell()[0], "Zeile bei x=" + x);
            assertEquals(cell[1], sample.nearestCell()[1], "Spalte bei x=" + x);
        }
    }
}
