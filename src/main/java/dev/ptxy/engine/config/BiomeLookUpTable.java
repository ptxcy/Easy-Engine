package dev.ptxy.engine.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.stream.StreamSupport;

public record BiomeLookUpTable(
        double[][] amplitude,
        double[][] frequency,
        double[][] persistence,
        double[][] lacunarity,
        double[][] redistribution,
        double[][] valleyRedistribution,
        boolean[][] enabled) {
    public static BiomeLookUpTable fromConfig() {
        JsonObject raw = Config.getBiomsLookUpTableJsonObject();
        if (raw == null)
            throw new IllegalStateException("biomsLookUpTable missing in SceneConfig.json");
        return new BiomeLookUpTable(
                loadTable(raw, "amplitude"),
                loadTable(raw, "frequency"),
                loadTable(raw, "persistence"),
                loadTable(raw, "lacunarity"),
                loadTable(raw, "redistribution"),
                loadTable(raw, "valleyRedistribution"),
                loadBooleanTable(raw, "enabled"));
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
