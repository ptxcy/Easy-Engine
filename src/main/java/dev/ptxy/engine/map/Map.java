package dev.ptxy.engine.map;

import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex2DVariant;
import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex3DVariant;
import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex4DVariant;
import de.articdive.jnoise.modules.octavation.fractal_functions.FractalFunction;
import de.articdive.jnoise.pipeline.JNoise;

/*
 * Noise configuration:
 *   HEIGHT_OCTAVES   — Anzahl der fBm-Schichten; mehr = feineres Detail, teurer
 *   HEIGHT_LACUNARITY — Frequenzmultiplikator pro Octave; 2.0 = jede Octave doppelt so fein
 *   HEIGHT_PERSISTENCE — Amplitudenabfall pro Octave; 0.5 = jede Octave halb so stark
 *   HEIGHT_SCALE     — Skaliert die Eingangskoordinaten; kleiner = größere Terrain-Features
 *   TEMP/HUMIDITY_SCALE — Gleiche Logik, aber großflächiger (Biom-Zonen)
 *   evaluateNoise gibt Werte in ca. [-1, 1] zurück
 */
public final class Map {
    private static final int HEIGHT_OCTAVES = 5;
    private static final double HEIGHT_LACUNARITY = 2.0;
    private static final double HEIGHT_PERSISTENCE = 0.5;
    private static final double HEIGHT_SCALE = 0.003;

    private static final double TEMP_SCALE = 0.001;
    private static final double HUMIDITY_SCALE = 0.001;

    private JNoise heightGen;
    private JNoise tempGen;
    private JNoise humidityGen;

    public Map(long seed) {
        initGenerators(seed);
    }

    private void initGenerators(long seed) {
        heightGen =
                JNoise.newBuilder()
                        .fastSimplex(seed, Simplex2DVariant.CLASSIC, Simplex3DVariant.CLASSIC, Simplex4DVariant.CLASSIC)
                        .octavate(HEIGHT_OCTAVES, HEIGHT_PERSISTENCE, HEIGHT_LACUNARITY, FractalFunction.FBM, true)
                        .scale(HEIGHT_SCALE)
                        .build();

        tempGen =
                JNoise.newBuilder()
                        .fastSimplex(seed + 1, Simplex2DVariant.CLASSIC, Simplex3DVariant.CLASSIC, Simplex4DVariant.CLASSIC)
                        .scale(TEMP_SCALE)
                        .build();

        humidityGen =
                JNoise.newBuilder()
                        .fastSimplex(seed + 2, Simplex2DVariant.CLASSIC, Simplex3DVariant.CLASSIC, Simplex4DVariant.CLASSIC)
                        .scale(HUMIDITY_SCALE)
                        .build();
    }

    public double getHeight(double x, double z) {
        return heightGen.evaluateNoise(x, z);
    }

    public double getTemperature(double x, double z) {
        return tempGen.evaluateNoise(x, z);
    }

    public double getHumidity(double x, double z) {
        return humidityGen.evaluateNoise(x, z);
    }
}
