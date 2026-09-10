package dev.ptxy.engine.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.stream.StreamSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public record BiomeLookUpTable(
        double[][] amplitude,
        double[][] frequency,
        double[][] persistence,
        double[][] lacunarity,
        double[][] redistribution,
        double[][] valleyRedistribution,
        double[][] targetAreaPercent,
        boolean[][] enabled) {
    private static final Logger log = LogManager.getLogger(BiomeLookUpTable.class);

    public static BiomeLookUpTable fromConfig() {
        JsonObject raw = Config.getBiomsLookUpTableJsonObject();
        if (raw == null)
            throw new IllegalStateException("biomsLookUpTable missing in SceneConfig.json");
        double[][] amplitude = loadTable(raw, "amplitude");
        double[][] targetAreaPercent = loadTable(raw, "targetAreaPercent");
        boolean[][] enabled = loadBooleanTable(raw, "enabled");
        validateTargetAreaPercent(targetAreaPercent, enabled);
        validateWorldMaxHeight(amplitude, enabled);
        return new BiomeLookUpTable(
                amplitude,
                loadTable(raw, "frequency"),
                loadTable(raw, "persistence"),
                loadTable(raw, "lacunarity"),
                loadTable(raw, "redistribution"),
                loadTable(raw, "valleyRedistribution"),
                targetAreaPercent,
                enabled);
    }

    // Höhenfärbung (shader/base/fragment.glsl) normiert jetzt auf EINEN festen Weltmaßstab
    // (SceneConfig.json terrain.worldMaxHeightMeters) statt pro Biom relativ -- Design-Vorgabe,
    // kein automatisch abgeleiteter Wert. Übersteigt eine aktivierte Zelle diesen Maßstab, clippt
    // ihre Färbung oben auf reine Schneefarbe statt eines Farbverlaufs; das hier ist nur eine
    // Warnung (keine Ausnahme), da eine leichte Überschreitung bewusst gewählt sein könnte.
    private static void validateWorldMaxHeight(double[][] amplitude, boolean[][] enabled) {
        TerrainConfig terrain = Config.getTerrainConfig();
        double worldMaxHeight = terrain.worldMaxHeightMeters();
        double heightAmplitude = terrain.heightAmplitude();
        for (int r = 0; r < enabled.length; r++) {
            for (int c = 0; c < enabled[r].length; c++) {
                if (!enabled[r][c]) continue;
                double cellMaxHeight = amplitude[r][c] * heightAmplitude;
                if (cellMaxHeight > worldMaxHeight) {
                    log.warn(
                            "Biom-Zelle ({},{}) erreicht bis zu {}m, mehr als der eingestellte"
                                    + " Weltmaßstab worldMaxHeightMeters={}m -- Höhenfärbung"
                                    + " clippt oben auf reine Schneefarbe statt eines"
                                    + " Farbverlaufs.",
                            r,
                            c,
                            cellMaxHeight,
                            worldMaxHeight);
                }
            }
        }
    }

    // FA4 (diskrete Flächenanteil-Randbedingung, siehe Map.calibrateActiveCellPositions): Die
    // Zielanteile der aktivierten Zellen müssen sich zu ~100% summieren, sonst ist die Vorgabe
    // widersprüchlich. Fail-fast beim Programmstart statt eines still falschen
    // Kalibrierungsergebnisses zur Laufzeit.
    private static void validateTargetAreaPercent(
            double[][] targetAreaPercent, boolean[][] enabled) {
        double sum = 0;
        for (int r = 0; r < enabled.length; r++) {
            for (int c = 0; c < enabled[r].length; c++) {
                if (enabled[r][c]) sum += targetAreaPercent[r][c];
            }
        }
        if (Math.abs(sum - 100.0) > 1.0) {
            throw new IllegalStateException(
                    "targetAreaPercent der aktivierten Zellen summiert sich auf "
                            + sum
                            + "%, nicht 100% (SceneConfig.json,"
                            + " biomsLookUpTable.targetAreaPercent)");
        }
    }

    private static double[][] loadTable(JsonObject raw, String key) {
        return StreamSupport.stream(raw.get(key).getAsJsonArray().spliterator(), false)
                .map(JsonElement::getAsJsonArray)
                .map(
                        arr ->
                                StreamSupport.stream(arr.spliterator(), false)
                                        .mapToDouble(JsonElement::getAsDouble)
                                        .toArray())
                .toArray(double[][]::new);
    }

    // Welche der 9 Zellen standardmäßig ("Alle Biome" im Editor-Pool) überhaupt erzeugt werden --
    // Scope-Entscheidung 2026-08-06, nur noch 3 Biome tatsächlich zu designen (siehe arbeit.tex,
    // TODOs). Isoliertes Editieren einer einzelnen Zelle im Editor ignoriert diese Einschränkung
    // bewusst (siehe Map.resolveCell), damit auch deaktivierte Zellen weiterhin einzeln testbar
    // bleiben.
    private static boolean[][] loadBooleanTable(JsonObject raw, String key) {
        return StreamSupport.stream(raw.get(key).getAsJsonArray().spliterator(), false)
                .map(JsonElement::getAsJsonArray)
                .map(
                        arr -> {
                            boolean[] row = new boolean[arr.size()];
                            for (int i = 0; i < arr.size(); i++) {
                                row[i] = arr.get(i).getAsBoolean();
                            }
                            return row;
                        })
                .toArray(boolean[][]::new);
    }
}
