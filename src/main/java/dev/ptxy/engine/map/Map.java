package dev.ptxy.engine.map;

import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex2DVariant;
import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex3DVariant;
import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex4DVariant;
import de.articdive.jnoise.pipeline.JNoise;
import dev.ptxy.engine.config.BiomeLookUpTable;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.config.TerrainParams;

/*
 * Noise-Konfiguration für die Heightmap (fBm = Fractal Brownian Motion), Option C:
 *
 *   Ein einziges globales Simplex-Grundrauschen (heightGen) wird pro Punkt selbst zu fBm
 *   aufsummiert (statt einer fest vorkonfigurierten JNoise-octavate()-Pipeline), damit alle
 *   vier fBm-Parameter -- nicht nur Amplitude und Frequenz -- aus der Biom-Tabelle
 *   (Temperatur × Feuchtigkeit) pro Punkt bestimmt werden können:
 *
 *   AMPLITUDE   — reine Lautstärke, skaliert nur die Höhe in Metern, ändert nicht die Form.
 *   FREQUENZ    — reiner Zoom, skaliert nur wie eng/weit die Wellen liegen, ändert nicht die Form.
 *   PERSISTENCE — Amplitudenabfall pro Octave. Niedrig (~0.2-0.3): Feindetails verschwinden,
 *                 glatte/plateauartige Formen. Hoch (~0.7-0.9): raue, zerklüftete Oberfläche.
 *   LACUNARITY  — Frequenzsprung pro Octave. Niedrig (~1.0): Octaves fast identisch, fBm verliert
 *                 seine Multi-Scale-Wirkung. ~2.0 (Standard): natürliches Terrain. Hoch (3-4):
 *                 extremer Detailsprung, sehr vielschichtig.
 *
 *   Persistence/Lacunarity bestimmen den CHARAKTER der Form (glatt vs. zerklüftet), Amplitude/
 *   Frequenz nur deren Größe/Maßstab -- erst die Kombination macht Biome strukturell verschieden,
 *   nicht nur unterschiedlich groß gestreckte Kopien derselben Form.
 *
 *   REDISTRIBUTION — reshaped den NORMIERTEN fBm-Wert ([-1,1]) mit einer vorzeichenerhaltenden
 *   Potenzfunktion, BEVOR er mit Amplitude multipliziert wird: shaped = sign(v) * |v|^exponent.
 *   Ändert NICHT die Wellenlänge (das macht Frequenz) -- die Nullstellen (Grenze Tal/Gipfel)
 *   bleiben an derselben Stelle. Exponent > 1: Kurve ist nahe 0 sehr flach (Werte nahe 0 bleiben
 *   länger nahe 0 → Täler werden räumlich breiter/flacher) und nahe ±1 sehr steil (Übergang zum
 *   Gipfel ist kurz → Gipfel werden räumlich schmaler/schärfer), OHNE dass sich ihre Höhe oder ihr
 *   Abstand ändert. Exponent < 1 kehrt das um (breite Plateaus, schmale/steile Täler). 1.0 = neutral
 *   (Identität, kein Effekt). Löst genau das Problem "Täler verbreitern, ohne dass die Berge
 *   dadurch auch breiter werden" -- das kann Frequenz allein nicht, weil sie Wellenlänge und damit
 *   Tal- UND Gipfelbreite gleichzeitig skaliert.
 *
 *   Der Exponent gilt getrennt für Gipfel (v >= 0, REDISTRIBUTION) und Täler (v < 0,
 *   VALLEY_REDISTRIBUTION) -- ein einzelner symmetrischer Exponent würde bei hohem Wert nicht nur
 *   seltene, extreme Gipfel nahezu unverändert lassen, sondern genauso seltene, extreme
 *   Tal-Ausreißer (Rohrauschen nahe -1) als scharfe, unmotivierte Einbrüche stehen lassen. Ein
 *   deutlich höherer VALLEY_REDISTRIBUTION-Exponent unterdrückt auch diese seltenen tiefen
 *   Ausreißer fast vollständig (dieselbe Potenzfunktion drückt kleine/mittlere |v| überproportional
 *   Richtung 0), sodass Täler durchgehend ruhig bleiben statt vereinzelt einzubrechen.
 *
 *   ZUORDNUNG PRO PUNKT (Höhenberechnung, resolveCellBlend()): jeder Punkt vergleicht seinen
 *   Abstand zu allen AKTIVIERTEN Zellen (nicht designte Platzhalterzellen fließen nie ein).
 *   Solange die nächste Zelle deutlich näher ist als die zweitnächste, bekommt der Punkt praktisch
 *   zu 100% ihre Höhe -- jedes Biom zeigt also auf dem Großteil seiner Fläche sein reines,
 *   unverändertes Design. Erst in einem schmalen Band um die Voronoi-Grenze zur zweitnächsten
 *   Zelle (Breite CELL_BLEND_WIDTH, in Gitter-Einheiten) wird per Smoothstep übergeblendet.
 *
 *   WICHTIG: geblendet wird das FERTIGE HÖHENERGEBNIS (cellHeight()), nicht die sechs
 *   fBm-Parameter selbst. Frequenz/Persistence/Lacunarity skalieren die Koordinaten, mit denen
 *   fbm() das Rauschen abtastet -- würden sie räumlich variieren, entstünde ein Phasenfehler
 *   proportional zur Weltkoordinate mal dem Parameter-Gradienten, der sich als künstliche,
 *   wellenförmige Ausschläge genau an den Zellgrenzen zeigt (so geschehen in einer früheren
 *   Zwischenstufe, die Parameter statt Höhen blendete). Jede Zelle wird deshalb komplett
 *   unabhängig mit ihren eigenen, konstanten Parametern durchgerechnet; erst die zwei fertigen
 *   Zahlen werden linear gemischt.
 *
 *   Getrennt davon liefert getBiomeCell() weiterhin die NÄCHSTGELEGENE Zelle als harten Index
 *   (resolveCell(), Voronoi-artig, inkl. Pool-Clamping und "enabled"-Ausweichen) -- nur für
 *   Debug-Anzeige (Zellfarbe, Editor "Position übernehmen") relevant, nicht für die Höhe selbst.
 *   Bei isolierter Pool-Auswahl (Editor auf genau eine Zelle geklemmt) liefern beide Methoden
 *   konsistent exakt diese eine Zelle ohne jede Mischung.
 *
 *   heightOctaves, heightScale, tempScale, humidityScale, heightTempLapse werden live aus
 *   Config.getTerrainParams() gelesen (Editor-Panel schreibt dort hinein) -- Parameteränderungen
 *   brauchen daher keinen Map-Neubau, nur ein Seed-Wechsel tut das.
 */
public final class Map {
    private JNoise heightGen;
    private JNoise tempGen;
    private JNoise humidityGen;

    public Map(long seed) {
        initGenerators(seed);
    }

    private void initGenerators(long seed) {
        heightGen = simplex(seed);
        tempGen = simplex(seed + 1);
        humidityGen = simplex(seed + 2);
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

    public double getHeight(double x, double z) {
        TerrainParams params = Config.getTerrainParams();
        BiomeLookUpTable table = Config.getBiomesLookUpTable();

        double rawTemp = getRawTemperature(x, z);
        double rawHumidity = getHumidity(x, z);

        double row = clamp(gridCoord(rawTemp), params.minRow(), params.maxRow());
        double col = clamp(gridCoord(rawHumidity), params.minCol(), params.maxCol());
        CellBlend blend = resolveCellBlend(row, col, params, table);

        double heightA = cellHeight(x, z, params, table, blend.rowA(), blend.colA());
        if (blend.weightA() >= 1.0) {
            return heightA;
        }
        double heightB = cellHeight(x, z, params, table, blend.rowB(), blend.colB());
        return blend.weightA() * heightA + (1 - blend.weightA()) * heightB;
    }

    // Höhe an (x,z) unter den FIXEN Parametern einer einzelnen Zelle -- bewusst kein Parameter
    // wird über den Raum geblendet. Frequenz/Persistence/Lacunarity fließen in die
    // Koordinaten-Skalierung von fbm() ein; würde man sie räumlich variieren lassen, entstünde
    // ein Phasenfehler proportional zur Weltkoordinate mal dem Parameter-Gradienten -- das erzeugt
    // genau die künstlichen, wellenförmigen Ausschläge, die eine reine Parameter-Interpolation an
    // Zellgrenzen zeigt. Deshalb wird stattdessen für jede beteiligte Zelle unabhängig eine
    // vollständige Höhe berechnet und erst am Ende (in getHeight()) linear zwischen den beiden
    // fertigen Zahlen überblendet -- das kann keine neue Wellenstruktur erzeugen, weil an dieser
    // Stelle keine Frequenz mehr über den Raum variiert.
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
        return shaped * amp;
    }

    // Welche der 9 Tabellenzellen an diesem Punkt tatsächlich gerendert wird (nach Pool-Clamping
    // und Enabled-Filter) -- für Anzeige/UI, nicht für die Höhenberechnung selbst (die
    // interpoliert stetig statt zu runden).
    public int[] getBiomeCell(double x, double z) {
        TerrainParams params = Config.getTerrainParams();
        BiomeLookUpTable table = Config.getBiomesLookUpTable();
        double row = clamp(gridCoord(getRawTemperature(x, z)), params.minRow(), params.maxRow());
        double col = clamp(gridCoord(getHumidity(x, z)), params.minCol(), params.maxCol());
        return resolveCell(row, col, params, table);
    }

    // Löst die tatsächlich zu verwendende Tabellenzelle auf. Bei isolierter Pool-Auswahl (Editor
    // hat auf genau eine Zelle geklemmt, minRow==maxRow && minCol==maxCol) zählt immer diese eine
    // Zelle, unabhängig vom Enabled-Status -- damit bleibt jede der 9 Zellen im Editor einzeln
    // testbar, auch wenn sie standardmäßig deaktiviert ist. Bei voller Pool-Breite ("Alle Biome")
    // wird auf die nächstgelegene AKTIVIERTE Zelle ausgewichen, falls die naiv nächstgelegene
    // Zelle deaktiviert ist -- das begrenzt die Standardwelt auf die per Config aktivierten Biome
    // (siehe "enabled" in SceneConfig.json, Scope-Entscheidung 2026-08-06 auf 3 Biome).
    private int[] resolveCell(
            double row, double col, TerrainParams params, BiomeLookUpTable table) {
        int nearestRow = clampIndex((int) Math.round(row));
        int nearestCol = clampIndex((int) Math.round(col));
        boolean isolated = params.minRow() == params.maxRow() && params.minCol() == params.maxCol();
        if (isolated || table.enabled()[nearestRow][nearestCol]) {
            return new int[] {nearestRow, nearestCol};
        }
        return nearestEnabledCell(row, col, table.enabled());
    }

    // Distanz-"Vorsprung" (in Gitter-Einheiten) der nächsten vor der zweitnächsten aktivierten
    // Zelle, ab dem ein Punkt als vollständig zur nächsten Zelle gehörend gilt (kein Restmix
    // mehr). Kleiner Wert = schmale, scharfe Übergangszone nahe der Grenze; großer Wert = breiter
    // Verlauf, der wieder näher an eine volle Tabelleninterpolation herankommt.
    private static final double CELL_BLEND_WIDTH = 0.4;

    // Liefert die Parameter-Mischung für die Höhenberechnung: die nächstgelegene aktivierte Zelle
    // bestimmt (fast) allein das Ergebnis, solange sie deutlich näher ist als die zweitnächste --
    // nur in einem schmalen Band um die Voronoi-Grenze zwischen den beiden wird weich (smoothstep)
    // übergeblendet. Nicht aktivierte Platzhalterzellen fließen nie ein. Bei isolierter
    // Pool-Auswahl (Editor auf genau eine Zelle geklemmt) wird -- wie bei resolveCell() -- immer
    // exakt diese eine Zelle geliefert, unabhängig vom Enabled-Status und ohne Mischung.
    private CellBlend resolveCellBlend(
            double row, double col, TerrainParams params, BiomeLookUpTable table) {
        boolean isolated = params.minRow() == params.maxRow() && params.minCol() == params.maxCol();
        if (isolated) {
            int r = clampIndex((int) Math.round(row));
            int c = clampIndex((int) Math.round(col));
            return new CellBlend(r, c, r, c, 1.0);
        }

        boolean[][] enabled = table.enabled();
        int bestRow = -1, bestCol = -1, secondRow = -1, secondCol = -1;
        double bestDist = Double.MAX_VALUE, secondDist = Double.MAX_VALUE;
        for (int r = 0; r < enabled.length; r++) {
            for (int c = 0; c < enabled[r].length; c++) {
                if (!enabled[r][c]) continue;
                double dr = row - r;
                double dc = col - c;
                double dist = Math.sqrt(dr * dr + dc * dc);
                if (dist < bestDist) {
                    secondDist = bestDist;
                    secondRow = bestRow;
                    secondCol = bestCol;
                    bestDist = dist;
                    bestRow = r;
                    bestCol = c;
                } else if (dist < secondDist) {
                    secondDist = dist;
                    secondRow = r;
                    secondCol = c;
                }
            }
        }

        if (secondRow == -1) {
            return new CellBlend(bestRow, bestCol, bestRow, bestCol, 1.0);
        }

        double t = clamp((secondDist - bestDist) / CELL_BLEND_WIDTH, 0, 1);
        double weightBest = 0.5 + 0.5 * smoothstep(t);
        return new CellBlend(bestRow, bestCol, secondRow, secondCol, weightBest);
    }

    private double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    private record CellBlend(int rowA, int colA, int rowB, int colB, double weightA) {}

    private int[] nearestEnabledCell(double row, double col, boolean[][] enabled) {
        int bestRow = 0;
        int bestCol = 0;
        double bestDist = Double.MAX_VALUE;
        for (int r = 0; r < enabled.length; r++) {
            for (int c = 0; c < enabled[r].length; c++) {
                if (!enabled[r][c]) continue;
                double dr = row - r;
                double dc = col - c;
                double dist = dr * dr + dc * dc;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestRow = r;
                    bestCol = c;
                }
            }
        }
        return new int[] {bestRow, bestCol};
    }

    private int clampIndex(int value) {
        return Math.max(0, Math.min(2, value));
    }

    // Bildet einen Klimawert ([-1,1]) auf eine Gitterposition ab (0..2 bei einer 3x3-Tabelle).
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

    private double bilinear(double[][] table, double row, double col) {
        int rows = table.length;
        int cols = table[0].length;

        int r0 = (int) Math.floor(row);
        int r1 = Math.min(r0 + 1, rows - 1);
        int c0 = (int) Math.floor(col);
        int c1 = Math.min(c0 + 1, cols - 1);

        double tr = row - r0;
        double tc = col - c0;

        double top = table[r0][c0] * (1 - tc) + table[r0][c1] * tc;
        double bottom = table[r1][c0] * (1 - tc) + table[r1][c1] * tc;
        return top * (1 - tr) + bottom * tr;
    }

    public double getTemperature(double x, double z, double worldHeight) {
        return getRawTemperature(x, z) - worldHeight * Config.getTerrainParams().heightTempLapse();
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
