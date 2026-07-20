package dev.ptxy.engine.map;

import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex2DVariant;
import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex3DVariant;
import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex4DVariant;
import de.articdive.jnoise.modules.octavation.fractal_functions.FractalFunction;
import de.articdive.jnoise.pipeline.JNoise;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.config.TerrainConfig;

/*
 * Noise-Konfiguration für die Heightmap (fBm = Fractal Brownian Motion):
 *
 *   HEIGHT_OCTAVES    — Anzahl übergelagerter Noise-Schichten.
 *                       Weniger (2–3): weiche, runde Hügel ohne Kleinstdetails.
 *                       Mehr (6–8):   scharfe Felskanten, Erosionsrillen, raues Terrain.
 *
 *   HEIGHT_LACUNARITY — Frequenzmultiplikator von Octave zu Octave.
 *                       2.0 (Standard): jede Schicht doppelt so fein → natürliches Terrain.
 *                       Höher (3–4):   extremes Detailgefälle, sehr zerklüftet.
 *                       Niedriger (1.5): weichere Übergänge zwischen den Schichten.
 *
 *   HEIGHT_PERSISTENCE — Amplitudenabfall pro Octave (0–1).
 *                       0.5 (Standard): jede Schicht halb so stark → ausgewogene Struktur.
 *                       Höher (0.7–0.9): Feindetails werden dominanter → sehr raue Oberfläche.
 *                       Niedriger (0.2–0.3): Feindetails kaum sichtbar → glatte, breite Berge.
 *
 *   HEIGHT_SCALE      — Streckt/staucht die Landschaft horizontal (skaliert Eingangskoordinaten).
 *                       Kleiner (0.001): riesige, weitläufige Gebirge und breite Täler.
 *                       Größer (0.01):  enge, dicht gepackte Hügel, schnelle Höhenwechsel.
 *                       → Hauptregler für "wie weit auseinander liegen Berge und Täler".
 *
 *   TEMP/HUMIDITY_SCALE — Gleiche Streck/Stauch-Logik, aber für Biom-Zonen.
 *                       Sehr klein halten damit Biome großflächig und nicht fleckig wirken.
 *
 *   evaluateNoise gibt Werte in ca. [-1, 1] zurück; HEIGHT_AMPLITUDE in ChunkManager
 *   skaliert das auf tatsächliche Welteinheiten (Höhe der Berge in Metern).
 */
public final class Map {
    private static final TerrainConfig TERRAIN = Config.getTerrainConfig();

    private static final int HEIGHT_OCTAVES = TERRAIN.heightOctaves();
    private static final double HEIGHT_LACUNARITY = TERRAIN.heightLacunarity();
    private static final double HEIGHT_PERSISTENCE = TERRAIN.heightPersistence();
    private static final double HEIGHT_SCALE = TERRAIN.heightScale();
    private static final double TEMP_SCALE = TERRAIN.tempScale();
    private static final double HUMIDITY_SCALE = TERRAIN.humidityScale();

    // Öffentlich, damit ChunkManager denselben Wert als Shader-Uniform setzen kann (Debug-Modus
    // "Biom-Zelle" muss die Rohtemperatur vor dem Höhenabzug rekonstruieren, siehe vertex.glsl).
    public static final double HEIGHT_TEMP_LAPSE = TERRAIN.heightTempLapse();

    private JNoise heightGen;
    private JNoise tempGen;
    private JNoise humidityGen;

    public Map(long seed) {
        initGenerators(seed);
    }

    private void initGenerators(long seed) {
        heightGen =
                JNoise.newBuilder()
                        .fastSimplex(
                                seed,
                                Simplex2DVariant.CLASSIC,
                                Simplex3DVariant.CLASSIC,
                                Simplex4DVariant.CLASSIC)
                        .octavate(
                                HEIGHT_OCTAVES,
                                HEIGHT_PERSISTENCE,
                                HEIGHT_LACUNARITY,
                                FractalFunction.FBM,
                                true)
                        .scale(HEIGHT_SCALE)
                        .build();

        tempGen =
                JNoise.newBuilder()
                        .fastSimplex(
                                seed + 1,
                                Simplex2DVariant.CLASSIC,
                                Simplex3DVariant.CLASSIC,
                                Simplex4DVariant.CLASSIC)
                        .scale(TEMP_SCALE)
                        .build();

        humidityGen =
                JNoise.newBuilder()
                        .fastSimplex(
                                seed + 2,
                                Simplex2DVariant.CLASSIC,
                                Simplex3DVariant.CLASSIC,
                                Simplex4DVariant.CLASSIC)
                        .scale(HUMIDITY_SCALE)
                        .build();
    }

    public double getHeight(double x, double z) {
        double rawTemp = rawTemperature(x, z);
        double rawHumidity = getHumidity(x, z);

        var table = Config.getBiomesLookUpTable();
        double amp = bilinear(table.amplitude(), rawTemp, rawHumidity);
        double freq = bilinear(table.frequency(), rawTemp, rawHumidity);

        return heightGen.evaluateNoise(x * freq, z * freq) * amp;
    }

    private double bilinear(double[][] table, double T, double H) {
        int rows = table.length;
        int cols = table[0].length;

        double row = (T + 1) / 2.0 * (rows - 1);
        double col = (H + 1) / 2.0 * (cols - 1);

        int r0 = (int) Math.floor(row);
        int r1 = Math.min(r0 + 1, rows - 1);
        int c0 = (int) Math.floor(col);
        int c1 = Math.min(c0 + 1, cols - 1);

        double tr = row - r0;
        double th = col - c0;

        double top = table[r0][c0] * (1 - th) + table[r0][c1] * th;
        double bottom = table[r1][c0] * (1 - th) + table[r1][c1] * th;
        return top * (1 - tr) + bottom * tr;
    }

    public double getTemperature(double x, double z, double worldHeight) {
        return rawTemperature(x, z) - worldHeight * HEIGHT_TEMP_LAPSE;
    }

    private double rawTemperature(double x, double z) {
        return tempGen.evaluateNoise(x, z);
    }

    public double getHumidity(double x, double z) {
        return humidityGen.evaluateNoise(x, z);
    }
}
