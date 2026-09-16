package dev.ptxy.engine.map;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AreaConstraintCalibrationTest {

    @Test
    void calibratedClimateCellAreaMatchesConfiguredTargetPercentages() {
        Map map = new Map(123L);
        int tundra = 0;
        int savanna = 0;
        int rainforest = 0;
        int gridSize = 400;
        double step = 5000.0;
        double origin = -gridSize * step / 2;
        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                double x = origin + i * step;
                double z = origin + j * step;
                int[] cell = map.getBiomeCell(x, z);
                if (cell[0] == 0 && cell[1] == 0) tundra++;
                else if (cell[0] == 1 && cell[1] == 1) savanna++;
                else if (cell[0] == 2 && cell[1] == 2) rainforest++;
            }
        }
        int total = gridSize * gridSize;
        double tundraPct = 100.0 * tundra / total;
        double savannaPct = 100.0 * savanna / total;
        double rainforestPct = 100.0 * rainforest / total;

        assertTrue(
                Math.abs(tundraPct - 15.0) < 3.0,
                "Tundra/Steppe-Klimaanteil war " + tundraPct + "%, erwartet ~15%");
        assertTrue(
                Math.abs(savannaPct - 40.0) < 3.0,
                "Savanne-Klimaanteil war " + savannaPct + "%, erwartet ~40%");
        assertTrue(
                Math.abs(rainforestPct - 45.0) < 3.0,
                "Regenwald-Klimaanteil war " + rainforestPct + "%, erwartet ~45%");
    }

    @Test
    void calibrationIsDeterministicForSameSeed() {
        Map first = new Map(99L);
        Map second = new Map(99L);
        for (double x = -10_000; x <= 10_000; x += 1234.0) {
            int[] a = first.getBiomeCell(x, x * 0.7);
            int[] b = second.getBiomeCell(x, x * 0.7);
            assertTrue(a[0] == b[0] && a[1] == b[1], "Klassifikation bei x=" + x + " weicht ab");
        }
    }
}
