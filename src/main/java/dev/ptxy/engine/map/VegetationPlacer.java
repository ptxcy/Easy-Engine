package dev.ptxy.engine.map;

import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.config.VegetationConfig;
import java.util.Arrays;

public final class VegetationPlacer {
    private static final int FLOATS_PER_NODE = 4;

    private VegetationPlacer() {}

    public static float[] place(
            ChunkPos pos,
            Map noiseMap,
            int chunkSize,
            float heightAmplitude,
            String typeName,
            boolean organicClumpingEnabled) {
        VegetationConfig vegConfig = Config.getVegetationConfig();
        VegetationConfig.VegetationType type = vegConfig.type(typeName);
        int density = type.gridDensity();
        long typeSeed = seedFor(typeName);

        double maxCoverage = type.maxCoveragePercent() / 100.0;

        float step = (float) chunkSize / density;
        float[] buffer = new float[density * density * FLOATS_PER_NODE];
        int count = 0;

        for (int i = 0; i < density; i++) {
            for (int j = 0; j < density; j++) {
                long globalX = (long) pos.x() * density + i;
                long globalZ = (long) pos.z() * density + j;
                double hash = hash01(globalX, globalZ, typeSeed);

                if (hash >= maxCoverage) continue;

                float wx = pos.x() * chunkSize + (i + 0.5f) * step;
                float wz = pos.z() * chunkSize + (j + 0.5f) * step;

                Map.BiomeBlend blend = noiseMap.getBiomeBlend(wx, wz);
                double coverage = blendedCoverage(type, blend);
                if (hash >= coverage) continue;

                double steepness = noiseMap.getSteepness(wx, wz, heightAmplitude);
                double steepnessFactor =
                        1.0
                                - smoothstep(
                                        vegConfig.steepnessDampenStart(),
                                        vegConfig.steepnessDampenFull(),
                                        steepness);
                double clumpFactor =
                        organicClumpingEnabled
                                ? noiseMap.getVegetationClumpFactor(wx, wz, blend)
                                : 1.0;
                if (hash >= coverage * steepnessFactor * clumpFactor) continue;

                float wy = (float) blend.height() * heightAmplitude;

                int i4 = count * FLOATS_PER_NODE;
                buffer[i4] = wx;
                buffer[i4 + 1] = wy;
                buffer[i4 + 2] = wz;
                buffer[i4 + 3] = blend.biome().ordinal();
                count++;
            }
        }

        return Arrays.copyOf(buffer, count * FLOATS_PER_NODE);
    }

    // Coverage percentages between neighboring biomes can differ by orders of magnitude (e.g.
    // savanna vs. deciduous forest tree density). Blending them arithmetically lets the denser
    // neighbor's rate dominate far into the sparser biome's territory. Blending in log space
    // instead computes a weighted geometric mean, which is the natural way to interpolate a
    // rate/density across such a range while staying just as continuous in the weights.
    private static final double MIN_COVERAGE_PERCENT = 1e-4;

    private static double blendedCoverage(
            VegetationConfig.VegetationType type, Map.BiomeBlend blend) {
        if (blend.biome() == Biome.ALPINE) {
            return type.coverage(Biome.ALPINE) / 100.0;
        }
        Map.BiomeWeights weights = blend.weights();
        double logCoverage =
                weights.tundra() * logCoveragePercent(type.coverage(Biome.TUNDRA_STEPPE))
                        + weights.savanna()
                                * logCoveragePercent(type.coverage(Biome.SAVANNA_PRAIRIE))
                        + weights.deciduousForest()
                                * logCoveragePercent(type.coverage(Biome.DECIDUOUS_FOREST));
        return Math.exp(logCoverage) / 100.0;
    }

    private static double logCoveragePercent(double coveragePercent) {
        return Math.log(Math.max(coveragePercent, MIN_COVERAGE_PERCENT));
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    private static long seedFor(String typeName) {
        return typeName.hashCode() * 0x9E3779B97F4A7C15L;
    }

    private static double hash01(long x, long z, long seed) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h ^= seed * 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return (h >>> 11) * (1.0 / (1L << 53));
    }
}
