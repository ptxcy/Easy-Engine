package dev.ptxy.engine.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.stream.StreamSupport;

public record BiomeLookUpTable(double[][] amplitude, double[][] frequency) {
    public static BiomeLookUpTable fromConfig() {
        JsonObject raw = Config.getBiomsLookUpTableJsonObject();
        if (raw == null)
            throw new IllegalStateException("biomsLookUpTable missing in SceneConfig.json");
        return new BiomeLookUpTable(loadTable(raw, "amplitude"), loadTable(raw, "frequency"));
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
}
