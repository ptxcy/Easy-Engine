package dev.ptxy.engine.map;

import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex2DVariant;
import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex3DVariant;
import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex4DVariant;
import de.articdive.jnoise.pipeline.JNoise;
import dev.ptxy.engine.config.BiomeBlendConfig;
import dev.ptxy.engine.config.BiomeLookUpTable;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.config.TerrainParams;
import dev.ptxy.engine.config.VegetationConfig;
import java.util.Arrays;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Map {
    private static final Logger log = LogManager.getLogger(Map.class);

    private final JNoise heightGen;
    private final JNoise tempGen;
    private final JNoise humidityGen;
    private final JNoise borderWarpXGen;
    private final JNoise borderWarpZGen;
    private final long seed;

    // One independent noise field per biome (own seed; frequency read live from
    // vegetation.clumpScale in SceneConfig.json on every call, not cached, so debug-panel edits
    // take effect immediately) instead of a single field shared by every biome — otherwise tuning
    // one biome's clump scale (e.g. bigger, sparser tree groves for savanna) would also reshape
    // tundra's and deciduous forest's clumping.
    private final JNoise tundraClumpGen;
    private final JNoise savannaClumpGen;
    private final JNoise deciduousClumpGen;
    private final JNoise alpineClumpGen;

    private final double[][] areaWeight;

    public Map(long seed) {
        this.seed = seed;
        this.heightGen = simplex(seed);
        this.tempGen = simplex(seed + 1);
        this.humidityGen = simplex(seed + 2);
        this.borderWarpXGen = simplex(seed + 3);
        this.borderWarpZGen = simplex(seed + 4);
        this.tundraClumpGen = simplex(seed + 5);
        this.savannaClumpGen = simplex(seed + 6);
        this.deciduousClumpGen = simplex(seed + 7);
        this.alpineClumpGen = simplex(seed + 8);
        this.areaWeight = calibrateAreaWeights();
    }

    /**
     * Blends each of the three main biomes' own clump noise by the same weights used for
     * height/color blending, so the clumping pattern stays continuous across biome borders instead
     * of hard-switching noise fields at the boundary. Alpine has no blend partner (see
     * blendedCoverage in VegetationPlacer), so it is used as-is with no mixing.
     */
    public double getVegetationClumpFactor(double x, double z, BiomeBlend blend) {
        VegetationConfig vegConfig = Config.getVegetationConfig();
        if (blend.biome() == Biome.ALPINE) {
            return clumpFactorFor(alpineClumpGen, vegConfig.clumpScale(Biome.ALPINE), x, z);
        }
        BiomeWeights w = blend.weights();
        return w.tundra()
                        * clumpFactorFor(
                                tundraClumpGen, vegConfig.clumpScale(Biome.TUNDRA_STEPPE), x, z)
                + w.savanna()
                        * clumpFactorFor(
                                savannaClumpGen, vegConfig.clumpScale(Biome.SAVANNA_PRAIRIE), x, z)
                + w.deciduousForest()
                        * clumpFactorFor(
                                deciduousClumpGen,
                                vegConfig.clumpScale(Biome.DECIDUOUS_FOREST),
                                x,
                                z);
    }

    private static double clumpFactorFor(JNoise gen, double scale, double x, double z) {
        VegetationConfig vegConfig = Config.getVegetationConfig();
        double raw = (gen.evaluateNoise(x * scale, z * scale) + 1.0) * 0.5;
        return smoothstep(vegConfig.clumpContrastLow(), vegConfig.clumpContrastHigh(), raw);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    private double warpedX(double x, double z) {
        BiomeBlendConfig blendConfig = Config.getBiomeBlendConfig();
        double scale = 1.0 / blendConfig.borderWarpPeriodMeters();
        return x
                + borderWarpXGen.evaluateNoise(x * scale, z * scale)
                        * blendConfig.borderWarpDistanceMeters();
    }

    private double warpedZ(double x, double z) {
        BiomeBlendConfig blendConfig = Config.getBiomeBlendConfig();
        double scale = 1.0 / blendConfig.borderWarpPeriodMeters();
        return z
                + borderWarpZGen.evaluateNoise(x * scale, z * scale)
                        * blendConfig.borderWarpDistanceMeters();
    }

    private JNoise simplex(long seed) {
        return JNoise.newBuilder()
                .fastSimplex(
                        seed,
                        Simplex2DVariant.CLASSIC,
                        Simplex3DVariant.CLASSIC,
                        Simplex4DVariant.CLASSIC)
                .build();
    }

    private static final int AREA_CALIBRATION_SAMPLES = 200_000;

    private static final double AREA_CALIBRATION_DOMAIN_HALF_WIDTH = 2_000_000.0;
    private static final int AREA_CALIBRATION_MAX_ITERATIONS = 200;
    private static final double AREA_CALIBRATION_TOLERANCE = 0.005;
    private static final double AREA_CALIBRATION_INITIAL_STEP = 4.0;
    private static final double AREA_CALIBRATION_STEP_DECAY = 0.97;

    private double[][] calibrateAreaWeights() {
        BiomeLookUpTable table = Config.getBiomesLookUpTable();
        boolean[][] enabled = table.enabled();
        double[][] targetPercent = table.targetAreaPercent();
        double[][] areaWeight = new double[enabled.length][enabled[0].length];

        int cellCount = 0;
        for (boolean[] row : enabled) for (boolean b : row) if (b) cellCount++;
        if (cellCount < 2) {
            return areaWeight;
        }

        int[] cellRow = new int[cellCount];
        int[] cellCol = new int[cellCount];
        double[] targetFraction = new double[cellCount];
        int idx = 0;
        for (int r = 0; r < enabled.length; r++) {
            for (int c = 0; c < enabled[r].length; c++) {
                if (!enabled[r][c]) continue;
                cellRow[idx] = r;
                cellCol[idx] = c;
                targetFraction[idx] = targetPercent[r][c] / 100.0;
                idx++;
            }
        }

        double[] sampleRow = new double[AREA_CALIBRATION_SAMPLES];
        double[] sampleCol = new double[AREA_CALIBRATION_SAMPLES];
        Random rnd = new Random(seed);
        for (int i = 0; i < AREA_CALIBRATION_SAMPLES; i++) {
            double x = (rnd.nextDouble() * 2 - 1) * AREA_CALIBRATION_DOMAIN_HALF_WIDTH;
            double z = (rnd.nextDouble() * 2 - 1) * AREA_CALIBRATION_DOMAIN_HALF_WIDTH;
            double wx = warpedX(x, z);
            double wz = warpedZ(x, z);
            sampleRow[i] = clamp(gridCoord(getRawTemperature(wx, wz)), 0, 2);
            sampleCol[i] = clamp(gridCoord(getHumidity(wx, wz)), 0, 2);
        }

        double[] weight = new double[cellCount];
        double stepSize = AREA_CALIBRATION_INITIAL_STEP;
        int[] counts = new int[cellCount];
        double lastMaxAbsError = Double.MAX_VALUE;
        int iterationsUsed = 0;
        for (int iteration = 0; iteration < AREA_CALIBRATION_MAX_ITERATIONS; iteration++) {
            iterationsUsed = iteration + 1;
            Arrays.fill(counts, 0);
            for (int s = 0; s < AREA_CALIBRATION_SAMPLES; s++) {
                counts[nearestWeightedCell(sampleRow[s], sampleCol[s], cellRow, cellCol, weight)]++;
            }

            lastMaxAbsError = 0;
            for (int i = 0; i < cellCount; i++) {
                double actualFraction = (double) counts[i] / AREA_CALIBRATION_SAMPLES;
                double error = targetFraction[i] - actualFraction;
                lastMaxAbsError = Math.max(lastMaxAbsError, Math.abs(error));
                weight[i] += stepSize * error;
            }

            if (lastMaxAbsError < AREA_CALIBRATION_TOLERANCE) {
                break;
            }
            stepSize *= AREA_CALIBRATION_STEP_DECAY;
        }

        if (lastMaxAbsError >= AREA_CALIBRATION_TOLERANCE) {
            log.warn(
                    "Flächenanteil-Kalibrierung nach {} Iterationen nicht auf {} konvergiert,"
                            + " größte Abweichung {} -- Zielanteile evtl. zu extrem für die"
                            + " Zellenzahl.",
                    iterationsUsed,
                    AREA_CALIBRATION_TOLERANCE,
                    lastMaxAbsError);
        }

        for (int i = 0; i < cellCount; i++) {
            areaWeight[cellRow[i]][cellCol[i]] = weight[i];
        }
        return areaWeight;
    }

    private int nearestWeightedCell(
            double row, double col, int[] cellRow, int[] cellCol, double[] weight) {
        int best = 0;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < cellRow.length; i++) {
            double dr = row - cellRow[i];
            double dc = col - cellCol[i];
            double score = dr * dr + dc * dc - weight[i];
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    public double getHeight(double x, double z) {
        return sampleTerrain(x, z).height();
    }

    public record TerrainSample(
            double height,
            BiomeWeights weights,
            int[] nearestCell,
            double rawTemperature,
            double humidity) {}

    public TerrainSample sampleTerrain(double x, double z) {
        TerrainParams params = Config.getTerrainParams();
        BiomeLookUpTable table = Config.getBiomesLookUpTable();

        double rawTemp = getRawTemperature(x, z);
        double humidity = getHumidity(x, z);
        double wx = warpedX(x, z);
        double wz = warpedZ(x, z);
        double row = clamp(gridCoord(getRawTemperature(wx, wz)), params.minRow(), params.maxRow());
        double col = clamp(gridCoord(getHumidity(wx, wz)), params.minCol(), params.maxCol());
        CellBlend blend = resolveCellBlend(row, col, x, z, params, table);

        return new TerrainSample(
                blendedHeight(blend),
                biomeWeightsOf(blend),
                nearestCellOf(blend),
                rawTemp,
                humidity);
    }

    private double blendedHeight(CellBlend blend) {
        double height = 0;
        for (int i = 0; i < blend.weights().length; i++) {
            height += blend.weights()[i] * blend.heights()[i];
        }
        return height;
    }

    private int[] nearestCellOf(CellBlend blend) {
        int bestIdx = 0;
        for (int i = 1; i < blend.weights().length; i++) {
            if (blend.weights()[i] > blend.weights()[bestIdx]) bestIdx = i;
        }
        return new int[] {blend.rows()[bestIdx], blend.cols()[bestIdx]};
    }

    private double cellHeight(
            double x, double z, TerrainParams params, BiomeLookUpTable table, int row, int col) {
        double amp = table.amplitude()[row][col];
        double freq = table.frequency()[row][col];
        double persistence = table.persistence()[row][col];
        double lacunarity = table.lacunarity()[row][col];
        double redistribution = table.redistribution()[row][col];
        double valleyRedistribution = table.valleyRedistribution()[row][col];

        double normalized =
                fbm(x, z, params.octaves(), persistence, lacunarity, params.heightScale() * freq);
        double exponent = normalized >= 0 ? redistribution : valleyRedistribution;
        double shaped = Math.signum(normalized) * Math.pow(Math.abs(normalized), exponent);
        double height = shaped * amp;

        if (table.depthCapEnabled()[row][col]) {
            double capNormalized = table.depthCapMeters()[row][col] / params.heightAmplitude();
            height = Math.max(height, capNormalized);
        }

        return height;
    }

    public Biome getBiome(double x, double z) {
        return getBiomeAndHeight(x, z).biome();
    }

    public record BiomeAndHeight(Biome biome, double height) {}

    public BiomeAndHeight getBiomeAndHeight(double x, double z) {
        BiomeBlend blend = getBiomeBlend(x, z);
        return new BiomeAndHeight(blend.biome(), blend.height());
    }

    public record BiomeBlend(Biome biome, double height, BiomeWeights weights) {}

    public BiomeBlend getBiomeBlend(double x, double z) {
        TerrainSample sample = sampleTerrain(x, z);
        double heightAmplitude = Config.getTerrainParams().heightAmplitude();
        double worldHeight = sample.height() * heightAmplitude;
        Biome biome;
        if (worldHeight >= Config.getBiomeBlendConfig().alpineHeightFraction() * heightAmplitude) {
            biome = Biome.ALPINE;
        } else {
            int[] cell = sample.nearestCell();
            biome = Biome.fromCell(cell[0], cell[1]);
        }
        return new BiomeBlend(biome, sample.height(), sample.weights());
    }

    public int[] getBiomeCell(double x, double z) {
        return sampleTerrain(x, z).nearestCell();
    }

    public record BiomeWeights(double tundra, double savanna, double deciduousForest) {}

    public BiomeWeights getBiomeWeights(double x, double z) {
        return sampleTerrain(x, z).weights();
    }

    private BiomeWeights biomeWeightsOf(CellBlend blend) {
        double[] weightByCell = new double[9];
        for (int i = 0; i < blend.rows().length; i++) {
            weightByCell[blend.rows()[i] * 3 + blend.cols()[i]] += blend.weights()[i];
        }
        return new BiomeWeights(weightByCell[0], weightByCell[4], weightByCell[5]);
    }

    private static final double GRADIENT_SAMPLE_STEP = 5.0;

    private CellBlend resolveCellBlend(
            double row,
            double col,
            double x,
            double z,
            TerrainParams params,
            BiomeLookUpTable table) {
        boolean isolated = params.minRow() == params.maxRow() && params.minCol() == params.maxCol();
        if (isolated) {
            int r = clampIndex((int) Math.round(row));
            int c = clampIndex((int) Math.round(col));
            double height = cellHeight(x, z, params, table, r, c);
            return new CellBlend(
                    new int[] {r}, new int[] {c}, new double[] {1.0}, new double[] {height});
        }

        boolean[][] enabled = table.enabled();
        int count = 0;
        for (boolean[] enabledRow : enabled) {
            for (boolean b : enabledRow) {
                if (b) count++;
            }
        }

        int[] cellRow = new int[count];
        int[] cellCol = new int[count];
        double[] score = new double[count];
        int idx = 0;
        int nearestIdx = 0;
        for (int r = 0; r < enabled.length; r++) {
            for (int c = 0; c < enabled[r].length; c++) {
                if (!enabled[r][c]) continue;

                cellRow[idx] = r;
                cellCol[idx] = c;
                score[idx] = powerScore(row, col, r, c);
                if (score[idx] < score[nearestIdx]) nearestIdx = idx;
                idx++;
            }
        }

        double[] cellHeightAtPoint = new double[count];
        double heightSum = 0;
        for (int i = 0; i < count; i++) {
            cellHeightAtPoint[i] = cellHeight(x, z, params, table, cellRow[i], cellCol[i]);
            heightSum += cellHeightAtPoint[i];
        }

        if (count == 1) {
            return new CellBlend(cellRow, cellCol, new double[] {1.0}, cellHeightAtPoint);
        }

        double meanHeight = heightSum / count;

        BiomeBlendConfig blendConfig = Config.getBiomeBlendConfig();
        double[] weight = new double[count];
        double sum = 0;
        for (int i = 0; i < count; i++) {
            if (i == nearestIdx) {
                weight[i] = 1.0;
            } else {

                double gap = score[i] - score[nearestIdx];

                double scoreIHere = score[i];
                double scoreIDx =
                        scoreAt(x + GRADIENT_SAMPLE_STEP, z, cellRow[i], cellCol[i], params);
                double scoreIDz =
                        scoreAt(x, z + GRADIENT_SAMPLE_STEP, cellRow[i], cellCol[i], params);
                double gradX = (scoreIDx - scoreIHere) / GRADIENT_SAMPLE_STEP;
                double gradZ = (scoreIDz - scoreIHere) / GRADIENT_SAMPLE_STEP;
                double localRate = Math.max(Math.sqrt(gradX * gradX + gradZ * gradZ), 1e-6);

                double worldGap = gap / localRate;

                double heightGapMeters =
                        Math.min(
                                Math.abs(cellHeightAtPoint[i] - meanHeight)
                                        * params.heightAmplitude(),
                                blendConfig.maxHeightGapMetersForBlendWidth());
                double localWidth =
                        blendConfig.worldBlendWidthBaseMeters()
                                + blendConfig.worldBlendWidthPerHeightMeter() * heightGapMeters;

                weight[i] = Math.exp(-worldGap / localWidth);
            }
            sum += weight[i];
        }
        for (int i = 0; i < count; i++) {
            weight[i] /= sum;
        }

        return new CellBlend(cellRow, cellCol, weight, cellHeightAtPoint);
    }

    private double scoreAt(double x, double z, int r, int c, TerrainParams params) {
        double wx = warpedX(x, z);
        double wz = warpedZ(x, z);
        double row = clamp(gridCoord(getRawTemperature(wx, wz)), params.minRow(), params.maxRow());
        double col = clamp(gridCoord(getHumidity(wx, wz)), params.minCol(), params.maxCol());
        return powerScore(row, col, r, c);
    }

    private double powerScore(double row, double col, int r, int c) {
        double dr = row - r;
        double dc = col - c;
        return dr * dr + dc * dc - areaWeight[r][c];
    }

    private record CellBlend(int[] rows, int[] cols, double[] weights, double[] heights) {}

    private int clampIndex(int value) {
        return Math.max(0, Math.min(2, value));
    }

    private double gridCoord(double value) {
        return (value + 1) / 2.0 * 2;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double fbm(
            double x,
            double z,
            int octaves,
            double persistence,
            double lacunarity,
            double baseFreq) {
        double total = 0;
        double amplitude = 1;
        double frequency = baseFreq;
        double maxAmplitude = 0;

        for (int i = 0; i < octaves; i++) {
            total += heightGen.evaluateNoise(x * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        return maxAmplitude == 0 ? 0 : total / maxAmplitude;
    }

    private static final double STEEPNESS_SAMPLE_STEP = 0.5;

    public double getSteepness(double x, double z, float heightAmplitude) {
        double hLeft = getHeight(x - STEEPNESS_SAMPLE_STEP, z) * heightAmplitude;
        double hRight = getHeight(x + STEEPNESS_SAMPLE_STEP, z) * heightAmplitude;
        double hDown = getHeight(x, z - STEEPNESS_SAMPLE_STEP) * heightAmplitude;
        double hUp = getHeight(x, z + STEEPNESS_SAMPLE_STEP) * heightAmplitude;
        double dhdx = (hRight - hLeft) / (2 * STEEPNESS_SAMPLE_STEP);
        double dhdz = (hUp - hDown) / (2 * STEEPNESS_SAMPLE_STEP);
        double normalY = 1.0 / Math.sqrt(1 + dhdx * dhdx + dhdz * dhdz);
        return 1.0 - normalY;
    }

    public double getRawTemperature(double x, double z) {
        double scale = Config.getTerrainParams().tempScale();
        return tempGen.evaluateNoise(x * scale, z * scale);
    }

    public double getHumidity(double x, double z) {
        double scale = Config.getTerrainParams().humidityScale();
        return humidityGen.evaluateNoise(x * scale, z * scale);
    }
}
