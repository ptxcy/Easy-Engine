package dev.ptxy.engine.map;

import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex2DVariant;
import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex3DVariant;
import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex4DVariant;
import de.articdive.jnoise.pipeline.JNoise;
import dev.ptxy.engine.config.BiomeLookUpTable;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.config.TerrainParams;
import java.util.Arrays;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 *   Zelle wird per Softmax übergeblendet. Die Bandbreite ist in ECHTEN WELTMETERN definiert
 *   (WORLD_BLEND_WIDTH_BASE/_PER_HEIGHT_METER) und wird per lokalem Gradienten aus dem
 *   Klima-Gitterraum umgerechnet -- siehe resolveCellBlend() für den Grund (Gitter-Distanz allein
 *   entspricht je nach Ort völlig unterschiedlich vielen Weltmetern).
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
 *   Ein zweiter Versuch blendete NUR Amplitude/Redistribution stetig, hielt Frequenz/
 *   Persistence/Lacunarity aber auf dem Wert der jeweils nächstgelegenen Zelle fest (in der
 *   Annahme, das vermeide den Phasenfehler, weil an keiner Stelle mehr eine Frequenz räumlich
 *   variiert). Das war NOCH SCHLECHTER als der ursprüngliche Schnitt: Die Frequenz wechselt an
 *   der Stelle, an der die nächstgelegene Zelle kippt, HART -- und weil die gemischte Amplitude
 *   entlang dieser gesamten Kippkurve etwa konstant bleibt (~0.5·(ampA+ampB)), springt der
 *   Rauschwert an JEDEM Vertex-Paar, das die Kurve überquert, zwischen zwei bei stark
 *   unterschiedlicher Frequenz abgetasteten, praktisch unkorrelierten Werten -- sichtbar als
 *   durchgehende, fast senkrechte Wand entlang der ganzen Kurve statt eines Einzelpunkts.
 *   Zurückgebaut; die aktuelle Fassung (zwei komplette Höhen mischen) ist zwar stellenweise
 *   steiler als ideal, aber überall stetig.
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
    private static final Logger log = LogManager.getLogger(Map.class);

    private JNoise heightGen;
    private JNoise tempGen;
    private JNoise humidityGen;
    private long seed;

    // Gewicht pro Tabellenzelle für die Nächste-Zelle-Zuordnung (resolveCell/resolveCellBlend) --
    // per Default 0 für alle Zellen (unveränderter Euklidischer Voronoi), für aktivierte Zellen
    // aber von calibrateAreaWeights() so gesetzt, dass die in SceneConfig.json geforderten
    // Flächenanteile (FA4) erzwungen werden. Verschiebt NICHT die Zellen-Position selbst (die
    // bleibt exakt Zeile/Spalte, siehe Amplitude/Frequenz-Tabellen) -- die gewichtete Zuordnung
    // score = distanz² - gewicht entspricht einem Power-Diagramm (Laguerre-Voronoi) statt eines
    // reinen Abstands-Voronoi.
    private double[][] areaWeight;

    public Map(long seed) {
        this.seed = seed;
        initGenerators(seed);
        calibrateAreaWeights();
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

    // Stichprobenzahl für die Flächenanteil-Kalibrierung -- rein statistisch (Simplex-Rauschen
    // ist nicht periodisch), der Standardfehler liegt bei diesem N für realistische Zielanteile
    // klar unter AREA_CALIBRATION_TOLERANCE.
    private static final int AREA_CALIBRATION_SAMPLES = 200_000;
    // Sample-Bereich in Weltkoordinaten -- um Größenordnungen breiter als die Rauschskala
    // (tempScale/humidityScale), damit die Stichprobe die tatsächliche, asymptotische
    // Wertverteilung des Rauschfelds wiedergibt statt nur einen einzelnen lokalen Ausschnitt.
    private static final double AREA_CALIBRATION_DOMAIN_HALF_WIDTH = 2_000_000.0;
    private static final int AREA_CALIBRATION_MAX_ITERATIONS = 200;
    private static final double AREA_CALIBRATION_TOLERANCE = 0.005;
    private static final double AREA_CALIBRATION_INITIAL_STEP = 4.0;
    private static final double AREA_CALIBRATION_STEP_DECAY = 0.97;

    // FA4 (diskrete Flächenanteil-Randbedingung): erzwingt den in SceneConfig.json
    // (biomsLookUpTable.targetAreaPercent) hinterlegten Flächenanteil pro aktiviertem Biom --
    // läuft automatisch bei jeder Map-Erzeugung, unabhängig vom Debug-Editor (TerrainParams/
    // TerrainEditorGui betreffen nur die dortige Pool-Klemmung, nicht diese Kalibrierung).
    //
    // Eine erste Fassung verschob dazu die POSITION jeder Zelle im Klimaraum (Lloyd-artige
    // Relaxation, Zellen wandern von einem gemeinsamen Zentroid weg/zu ihm hin). Das erwies sich
    // als instabil: da der Zentroid selbst der Mittelwert der sich bewegenden Zellen ist,
    // entsteht eine Rückkopplung -- bei stark ungleichen Zielanteilen (hier 15/25/60%) liefen
    // Zellen auseinander statt zu konvergieren, teils bis zur Kollision zweier Zellen auf
    // demselben Punkt. Ersetzt durch ein Power-Diagramm (Laguerre-Voronoi): die Zellpositionen
    // bleiben exakt bei ihrer Zeile/Spalte fixiert, stattdessen bekommt jede Zelle ein Gewicht
    // (areaWeight), das den Zuordnungs-Score distanz²-gewicht verschiebt. Da sich dabei nichts
    // Geometrisches mehr bewegt, gibt es keine Rückkopplung mehr -- jede Zelle konvergiert
    // unabhängig gegen ihren Zielanteil (siehe nearestWeightedCell()).
    private void calibrateAreaWeights() {
        BiomeLookUpTable table = Config.getBiomesLookUpTable();
        boolean[][] enabled = table.enabled();
        double[][] targetPercent = table.targetAreaPercent();
        areaWeight = new double[enabled.length][enabled[0].length];

        int cellCount = 0;
        for (boolean[] row : enabled) for (boolean b : row) if (b) cellCount++;
        if (cellCount < 2) {
            return; // eine einzelne aktivierte Zelle deckt per Definition 100% ab, nichts zu tun
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
            sampleRow[i] = clamp(gridCoord(getRawTemperature(x, z)), 0, 2);
            sampleCol[i] = clamp(gridCoord(getHumidity(x, z)), 0, 2);
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
        TerrainParams params = Config.getTerrainParams();
        BiomeLookUpTable table = Config.getBiomesLookUpTable();

        double rawTemp = getRawTemperature(x, z);
        double rawHumidity = getHumidity(x, z);

        double row = clamp(gridCoord(rawTemp), params.minRow(), params.maxRow());
        double col = clamp(gridCoord(rawHumidity), params.minCol(), params.maxCol());
        CellBlend blend = resolveCellBlend(row, col, x, z, params, table);

        double height = 0;
        for (int i = 0; i < blend.rows().length; i++) {
            double w = blend.weights()[i];
            // Unterhalb dieser Schwelle trägt die Zelle praktisch nichts mehr bei -- spart die
            // vergleichsweise teure fbm()-Auswertung (mehrere Octaves) für Zellen, die ohnehin
            // fast keinen sichtbaren Einfluss mehr haben.
            if (w < 1e-4) continue;
            height += w * cellHeight(x, z, params, table, blend.rows()[i], blend.cols()[i]);
        }
        return height;
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
    //
    // Rückbau eines Versuchs (Frequenz auf die nächstgelegene Zelle fixieren, nur Amplitude/
    // Redistribution stetig blenden): Das erzeugte etwas noch Schlimmeres als den ursprünglichen
    // Schnitt -- eine echte SPRUNGSTELLE entlang der GESAMTEN Kurve, an der die nächstgelegene
    // Zelle wechselt (nicht nur an einem Punkt). Nahe dieser Kurve bleibt die gemischte Amplitude
    // über eine ganze Strecke etwa konstant bei ~0.5*(ampA+ampB), während der zugrunde liegende
    // Rauschwert an jedem Vertex-Paar, das die Kurve überquert, zwischen zwei bei stark
    // unterschiedlicher Frequenz abgetasteten (praktisch unkorrelierten) Werten hin- und
    // herspringt -- sichtbar als durchgehende, fast senkrechte Wand statt eines Punktfehlers.
    // Die aktuelle Fassung (zwei komplette Höhen mischen) ist zwar an einzelnen Stellen im Band
    // steiler als ideal, aber wenigstens überall stetig -- keine Sprungstelle.
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

    // Bruchteil von heightAmplitude, ab dem ein Punkt unabhängig von Temperatur/Feuchte als
    // Alpin/Gebirge klassifiziert wird -- MUSS mit der Höhenfärbung im Fragment-Shader
    // übereinstimmen (shader/base/fragment.glsl: ab h=worldPos.y/heightAmplitude >= 0.62 ist die
    // Gipfel-Grau-Farbe voll erreicht, smoothstep(0.56, 0.62, h)). Bewusst als Bruchteil statt
    // fixer Meterzahl, damit die Alpin-Grenze automatisch mitskaliert, wenn heightAmplitude
    // (SceneConfig.json, der einzige globale Höhen-Hebel) angepasst wird -- vorher war hier ein
    // hartkodiertes 49.6 (= 0.62 * 80.0), das nach einer heightAmplitude-Änderung stillschweigend
    // nicht mehr zur Gipfelfarbe gepasst hätte.
    private static final double ALPINE_HEIGHT_FRACTION = 0.62;

    // Biom-Klassifikation inkl. Höhen-Override: Alpin hat bewusst KEINEN eigenen Eintrag in der
    // BiomeLookUpTable -- die Höhe selbst kommt unverändert aus der normalen Generierung
    // (getHeight(), inkl. Zellen-Blending). Erst das FERTIGE Ergebnis wird gegen den Höhen-
    // Threshold geprüft; nur die Klassifikation (nicht die Geländeform) weicht dann auf Alpin aus.
    public Biome getBiome(double x, double z) {
        double heightAmplitude = Config.getTerrainParams().heightAmplitude();
        double worldHeight = getHeight(x, z) * heightAmplitude;
        if (worldHeight >= ALPINE_HEIGHT_FRACTION * heightAmplitude) {
            return Biome.ALPINE;
        }
        int[] cell = getBiomeCell(x, z);
        return Biome.fromCell(cell[0], cell[1]);
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

    // Kontinuierliches Gewicht (0..1, Summe genau 1) je der drei designten Zellen, exakt dieselbe
    // Zuordnung samt Übergangsband, die getHeight() bereits für die GEOMETRIE verwendet (siehe
    // resolveCellBlend()). Für die Terrain-Einfärbung gedacht.
    //
    // Eine erste Fassung übertrug stattdessen "cellA"/"cellB" (diskrete Zell-IDs der
    // nächsten/zweitnächsten Zelle) plus ein gemeinsames weightA als GLSL-Vertex-Attribute --
    // cellA/cellB liefen dabei zwangsläufig als "flat" (Indizes lassen sich nicht sinnvoll
    // interpolieren), weightA aber normal interpoliert. Das brach genau dort, wo zwei Vertices
    // eines Dreiecks sich uneinig sind, WELCHE Zelle die nächste ist (was in der gesamten
    // Übergangszone laufend passiert, nicht nur bei großen Höhenunterschieden): OpenGL übernimmt
    // für "flat"-Attribute des ganzen Dreiecks nur den Wert des "provoking vertex", das
    // interpolierte weightA bezog sich an den übrigen Vertices dann auf die FALSCHE Zellenpaarung
    // -- sichtbar als harter Schnitt. Der Fix vermeidet jede diskrete, pro-Dreieck geltende
    // Zell-Zuordnung: jede der drei Zellen bekommt ihr eigenes, überall stetiges Gewicht, keines
    // davon ist "flat", also interpoliert die GPU sie überall korrekt.
    public record BiomeWeights(double tundra, double savanna, double rainforest) {}

    public BiomeWeights getBiomeWeights(double x, double z) {
        TerrainParams params = Config.getTerrainParams();
        BiomeLookUpTable table = Config.getBiomesLookUpTable();
        double row = clamp(gridCoord(getRawTemperature(x, z)), params.minRow(), params.maxRow());
        double col = clamp(gridCoord(getHumidity(x, z)), params.minCol(), params.maxCol());
        CellBlend blend = resolveCellBlend(row, col, x, z, params, table);

        double[] weightByCell = new double[9]; // Index = row*3+col, wie getBiomeCell()
        for (int i = 0; i < blend.rows().length; i++) {
            weightByCell[blend.rows()[i] * 3 + blend.cols()[i]] += blend.weights()[i];
        }
        return new BiomeWeights(weightByCell[0], weightByCell[4], weightByCell[8]);
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
        boolean isolated = params.minRow() == params.maxRow() && params.minCol() == params.maxCol();
        if (isolated) {
            // Debug-Sonderfall (Editor-Pool auf eine einzelne Zelle geklemmt): ignoriert bewusst
            // Enabled-Status UND Kalibrierung, damit auch nicht designte Platzhalterzellen im
            // Editor einzeln inspizierbar bleiben.
            return new int[] {clampIndex((int) Math.round(row)), clampIndex((int) Math.round(col))};
        }
        return nearestEnabledCell(row, col, table.enabled());
    }

    // Übergangsbreite in ECHTEN WELTMETERN (nicht in Gitter-Einheiten!) -- eine frühere Fassung
    // maß die Breite direkt im Klima-Gitterraum, aber wie viele Weltmeter einer Gitter-Einheit
    // entsprechen, hängt vom LOKALEN Gradienten des Temperatur-/Feuchtigkeitsrauschens ab, der
    // nicht konstant ist. An Stellen, wo sich das Klima schnell ändert, entsprach dieselbe
    // Gitterbreite dann nur wenigen Weltmetern statt der beabsichtigten Hunderten -- sichtbar als
    // scharfkantiger Canyon mit praktisch senkrechten Wänden, nicht als Hang. Siehe
    // resolveCellBlend(), das die Gitter-Distanz jetzt per lokalem Gradienten in Weltmeter
    // umrechnet, BEVOR sie gegen diese Breite geprüft wird.
    // Zwei frühere Fassungen leiteten die Breite aus dem STATISCHEN Tabellenwert für den
    // größtmöglichen Amplituden-Unterschied ab (Tundra 10.0 gegen Savanne/Regenwald 0.3, Gap
    // 9.7), einheitlich für die ganze Karte. Bei Faktor 200 ergab das rund 2040 Weltmeter --
    // per Scan bestätigt zu groß, die Klimazonen selbst sind oft nur 100-700m breit, Tundras
    // Form mischte sich spürbar bis tief in die Nachbarbiome. Bei Faktor 5 (rund 68m) kamen die
    // Biome zwar wieder klar getrennt zurück, aber GENAU DORT, wo Tundras Rauschen an ihrem
    // eigenen Rand tatsächlich einen hohen Gipfel trifft, reichte die feste kleine Breite nicht,
    // um den realen Höhenunterschied zu verteilen -- sichtbar als durchgehende, fast senkrechte
    // Felswand entlang eines ganzen Bergrückens, nicht nur an einem einzelnen Punkt (Screenshot-
    // Report). Der Tabellenwert 9.7 ist ein WORST CASE, der so gut wie nie eintritt -- an den
    // meisten Stellen des Randes liegt Tundras tatsächlicher Höhenwert weit unter ihrem Maximum.
    // Jetzt wird deshalb pro Punkt die ECHTE, an dieser Stelle abgetastete Höhendifferenz
    // zwischen nächster und jeweiliger Nachbarzelle verwendet (siehe resolveCellBlend()), statt
    // eines für die ganze Karte fixen Tabellenwerts -- schmal, wo die Zellen an dieser Stelle
    // ohnehin ähnlich hoch sind, automatisch breiter nur genau dort, wo der reale Unterschied
    // groß ist.
    private static final double WORLD_BLEND_WIDTH_BASE = 20.0;
    private static final double WORLD_BLEND_WIDTH_PER_HEIGHT_METER = 1.0;
    // Schrittweite (Weltmeter) für den Differenzenquotienten, mit dem der lokale Gradient von
    // "Gitter-Abstands-Vorsprung pro Weltmeter" geschätzt wird -- klein genug für eine lokale
    // Schätzung, groß genug, um nicht im Rauschen der Gleitkomma-Arithmetik unterzugehen.
    private static final double GRADIENT_SAMPLE_STEP = 5.0;

    // Liefert die Parameter-Mischung für die Höhenberechnung. Bewusst KEINE diskrete "Top-2"-
    // Auswahl (nächste + zweitnächste Zelle) mehr -- jede aktivierte Zelle bekommt immer ein
    // eigenes, überall stetiges Gewicht (Softmax über den Power-Score, siehe unten), auch wenn es
    // meist verschwindend klein ist. Grund für den Umbau: Bei 3 aktivierten Zellen gibt es nicht
    // nur eine äußere Grenze (nächste Zelle wechselt), sondern auch eine INNERE Grenze mitten in
    // einem Bioms Fläche, an der nur die ZWEITNÄCHSTE Zelle wechselt (z.B. von Tundra zu
    // Regenwald, während Savanne durchgehend die nächste bleibt). Eine Top-2-Auswahl blendet an
    // dieser inneren Grenze zwangsläufig abrupt von "Savanne+Tundra" auf "Savanne+Regenwald" um --
    // und weil Tundras und Regenwalds Höhe an dieser Stelle unabhängig voneinander sind (siehe
    // cellHeight()), sprang die Höhe dort hart, obwohl es äußerlich wie das Innere eines einzigen
    // Bioms aussieht. Mit einer echten Softmax-Gewichtung über ALLE aktivierten Zellen gibt es
    // diese Fallunterscheidung nicht mehr: jede Zelle trägt immer bei, auch wenn nur mit einem
    // verschwindenden Bruchteil, und ihr Anteil wächst/schrumpft stetig, ganz ohne einen Punkt, an
    // dem sich umschaltet, WELCHE Zellen überhaupt teilnehmen. Bei isolierter Pool-Auswahl (Editor
    // auf genau eine Zelle geklemmt) wird -- wie bei resolveCell() -- immer exakt diese eine
    // Zelle geliefert, unabhängig vom Enabled-Status und ohne Mischung.
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
            return new CellBlend(new int[] {r}, new int[] {c}, new double[] {1.0});
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
                // FA4: areaWeight verschiebt nur, WELCHE Zelle als nächste gilt (Power-Diagramm),
                // s. calibrateAreaWeights() -- die Positionen selbst bleiben unverändert bei
                // Zeile/Spalte.
                cellRow[idx] = r;
                cellCol[idx] = c;
                score[idx] = powerScore(row, col, r, c);
                if (score[idx] < score[nearestIdx]) nearestIdx = idx;
                idx++;
            }
        }

        if (count == 1) {
            return new CellBlend(cellRow, cellCol, new double[] {1.0});
        }

        // Höhe JEDER beteiligten Zelle an DIESEM Punkt, einmal vorab berechnet -- Grundlage für
        // den Mittelwert unten. Referenz für die Breiten-Berechnung ist bewusst NICHT die Höhe
        // der jeweils nächstgelegenen Zelle (siehe Ausbau-Notiz unten), deshalb wird hier über
        // ALLE Zellen gemittelt statt nur nearestIdx auszuwerten.
        double[] cellHeightAtPoint = new double[count];
        double heightSum = 0;
        for (int i = 0; i < count; i++) {
            cellHeightAtPoint[i] = cellHeight(x, z, params, table, cellRow[i], cellCol[i]);
            heightSum += cellHeightAtPoint[i];
        }
        double meanHeight = heightSum / count;

        double[] weight = new double[count];
        double sum = 0;
        for (int i = 0; i < count; i++) {
            if (i == nearestIdx) {
                weight[i] = 1.0; // exp(0), die nächstgelegene Zelle ist die Referenz (Gap = 0)
            } else {
                // Gap MUSS über denselben Power-Score gemessen werden, der auch die Rangfolge
                // bestimmt -- NICHT über die reine geometrische Distanz. Eine frühere Fassung
                // nutzte geometricDistance() hier, in der Annahme, das halte das Blend-Verhalten
                // unabhängig von der Flächenkalibrierung. Bug dabei: Am Punkt, an dem zwei Zellen
                // um den Rang konkurrieren, sind per Definition ihre POWER-SCORES gleich -- ihre
                // reinen DISTANZEN aber nicht, weil sich ihre FA4-Gewichte unterscheiden.
                double gap = score[i] - score[nearestIdx];

                // Lokale Rate (Score-Einheiten pro Weltmeter) NUR aus dem eigenen Score-Feld der
                // KANDIDATEN-Zelle geschätzt, nicht aus der Paarung (nächste Zelle, Kandidat).
                // Eine frühere Fassung differenzierte genau dieses Paar per gridGapAt() -- das
                // Problem: sobald die nächstgelegene Zelle selbst kippt (z.B. Savanne zu
                // Regenwald), wechselt damit auch das Paar, über das differenziert wird. score[i]
                // minus score[nearestIdx] bleibt an diesem Kipppunkt zwar stetig (beide Scores sind
                // dort per Definition gleich), aber Savannes und Regenwalds Score-Feld haben als
                // Funktion der Weltposition unterschiedliche Steigungen -- die geschätzte RATE
                // sprang deshalb an genau diesem Punkt, obwohl der Gap selbst es nicht tat, und
                // erzeugte über worldGap=gap/rate einen echten Sprung im Gewicht (Screenshot-
                // Report: 48m auf 62m Höhe in unter einem Meter, exakt am Kipppunkt). score[i]
                // selbst ist dagegen eine von der nächstgelegenen Zelle komplett unabhängige,
                // überall stetige Funktion -- ihre eigene Steigung hat keinen Kipppunkt.
                double scoreIHere = score[i];
                double scoreIDx =
                        scoreAt(x + GRADIENT_SAMPLE_STEP, z, cellRow[i], cellCol[i], params);
                double scoreIDz =
                        scoreAt(x, z + GRADIENT_SAMPLE_STEP, cellRow[i], cellCol[i], params);
                double gradX = (scoreIDx - scoreIHere) / GRADIENT_SAMPLE_STEP;
                double gradZ = (scoreIDz - scoreIHere) / GRADIENT_SAMPLE_STEP;
                double localRate = Math.max(Math.sqrt(gradX * gradX + gradZ * gradZ), 1e-6);

                double worldGap = gap / localRate;

                // Breite hängt von der ECHTEN, an diesem Punkt abgetasteten Höhendifferenz ab,
                // nicht vom statischen Tabellen-Maximum -- siehe Kommentar bei
                // WORLD_BLEND_WIDTH_BASE. Meistens klein (ähnliche Höhen nahe der Grundfläche
                // beider Zellen), automatisch groß nur dort, wo eine Zelle hier tatsächlich einen
                // hohen Ausschlag hat.
                //
                // WICHTIG: die Abweichung wird gegen den MITTELWERT über ALLE beteiligten Zellen
                // gemessen, NICHT gegen die Höhe der jeweils nächstgelegenen Zelle. Eine erste
                // Fassung nutzte nearestHeight als Referenz -- das erzeugte einen neuen, subtilen
                // Bruch genau dort, wo die nächstgelegene Zelle selbst wechselt (z.B. Savanne zu
                // Regenwald): score[nearest] ist an diesem Kipppunkt zwar für beide Kandidaten
                // gleich (daher ist "gap" dort stetig), aber Savannes und Regenwalds tatsächliche
                // Höhe an diesem Punkt sind zwei unabhängige, unkorrelierte Zahlen -- die Breite
                // einer DRITTEN Zelle (hier Tundra) sprang deshalb an genau diesem Punkt, weil sich
                // ihre Referenzhöhe schlagartig von Savannes auf Regenwalds Wert umstellte. Genau
                // dieses Muster (Screenshot-Report: Sprung von 48m auf 62m in unter einem Meter,
                // exakt am Savanne/Regenwald-Kipppunkt) war der eigentliche Bug. Der Mittelwert
                // über alle Zellen ist dagegen eine stetige Funktion ohne jede diskrete Auswahl --
                // kein Kipppunkt, an dem er selbst springt.
                double heightGapMeters =
                        Math.abs(cellHeightAtPoint[i] - meanHeight) * params.heightAmplitude();
                double localWidth =
                        WORLD_BLEND_WIDTH_BASE
                                + WORLD_BLEND_WIDTH_PER_HEIGHT_METER * heightGapMeters;

                weight[i] = Math.exp(-worldGap / localWidth);
            }
            sum += weight[i];
        }
        for (int i = 0; i < count; i++) {
            weight[i] /= sum;
        }

        return new CellBlend(cellRow, cellCol, weight);
    }

    // Power-Score EINER Zelle an einer ANDEREN Weltposition -- reine Hilfsfunktion für den
    // Differenzenquotienten in resolveCellBlend(), bewusst nur für eine einzelne Zelle statt für
    // ein Paar (siehe dortiger Kommentar zum Grund).
    private double scoreAt(double x, double z, int r, int c, TerrainParams params) {
        double row = clamp(gridCoord(getRawTemperature(x, z)), params.minRow(), params.maxRow());
        double col = clamp(gridCoord(getHumidity(x, z)), params.minCol(), params.maxCol());
        return powerScore(row, col, r, c);
    }

    private double powerScore(double row, double col, int r, int c) {
        double dr = row - r;
        double dc = col - c;
        return dr * dr + dc * dc - areaWeight[r][c];
    }

    private record CellBlend(int[] rows, int[] cols, double[] weights) {}

    private int[] nearestEnabledCell(double row, double col, boolean[][] enabled) {
        int bestRow = 0;
        int bestCol = 0;
        double bestScore = Double.MAX_VALUE;
        for (int r = 0; r < enabled.length; r++) {
            for (int c = 0; c < enabled[r].length; c++) {
                if (!enabled[r][c]) continue;
                double score = powerScore(row, col, r, c);
                if (score < bestScore) {
                    bestScore = score;
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

    // FA2 (Geländesteigung als Einflussfaktor): 1 - N.y der lokalen Oberflächennormale, per
    // zentraler Differenz aus dem Höhenfeld selbst hergeleitet -- exakt dieselbe Definition wie
    // in shader/base/fragment.glsl (dort per Screen-Space-Derivative statt Differenzenquotient,
    // aber identisches Ergebnis), damit Vegetations-Ausschluss und der visuelle Fels-Blend
    // konsistent an derselben Stelle greifen. 0 = eben, nahe 1 = senkrecht.
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
