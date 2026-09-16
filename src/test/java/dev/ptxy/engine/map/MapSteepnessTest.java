package dev.ptxy.engine.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapSteepnessTest {

    @Test
    void steepnessStaysWithinNormalizedRange() {
        Map map = new Map(5L);
        for (double x = -20_000; x <= 20_000; x += 3333.0) {
            double steepness = map.getSteepness(x, x * 0.3, 100.0f);
            assertTrue(
                    steepness >= 0.0 && steepness <= 1.0,
                    "Steepness außerhalb [0,1] bei x=" + x + ": " + steepness);
        }
    }

    @Test
    void steepnessIsDeterministicForSamePosition() {
        Map map = new Map(5L);
        double first = map.getSteepness(1234.0, -5678.0, 100.0f);
        double second = map.getSteepness(1234.0, -5678.0, 100.0f);
        assertEquals(first, second);
    }

    @Test
    void tundraIsSteeperThanSavannaOnAverage() {
        Map map = new Map(5L);
        double tundraSteepnessSum = 0;
        int tundraSamples = 0;
        double savannaSteepnessSum = 0;
        int savannaSamples = 0;

        for (double x = -50_000; x <= 50_000; x += 777.0) {
            for (double z = -50_000; z <= 50_000; z += 5555.0) {
                int[] cell = map.getBiomeCell(x, z);
                double steepness = map.getSteepness(x, z, 100.0f);
                if (cell[0] == 0 && cell[1] == 0) {
                    tundraSteepnessSum += steepness;
                    tundraSamples++;
                } else if (cell[0] == 1 && cell[1] == 1) {
                    savannaSteepnessSum += steepness;
                    savannaSamples++;
                }
            }
        }

        assertTrue(tundraSamples > 0 && savannaSamples > 0, "Stichprobe traf nicht beide Biome");
        double avgTundra = tundraSteepnessSum / tundraSamples;
        double avgSavanna = savannaSteepnessSum / savannaSamples;
        assertTrue(
                avgTundra > avgSavanna,
                "Tundra-Steilheit ("
                        + avgTundra
                        + ") war nicht größer als Savanne-Steilheit ("
                        + avgSavanna
                        + ")");
    }
}
