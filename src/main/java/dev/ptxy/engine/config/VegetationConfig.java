package dev.ptxy.engine.config;

import com.google.gson.JsonObject;
import dev.ptxy.engine.map.Biome;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public record VegetationConfig(int renderDistance, Map<String, VegetationType> types) {

    public record VegetationType(
            int gridDensity, Map<Biome, Double> coveragePercent, Map<String, Double> scale) {

        public double coverage(Biome biome) {
            return coveragePercent.getOrDefault(biome, 0.0);
        }

        public double maxCoveragePercent() {
            return coveragePercent.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(0.0);
        }

        public double scale(String key) {
            Double value = scale.get(key);
            if (value == null)
                throw new IllegalStateException("Unknown vegetation scale key: " + key);
            return value;
        }
    }

    public VegetationType type(String name) {
        VegetationType type = types.get(name);
        if (type == null) throw new IllegalArgumentException("Unknown vegetation type: " + name);
        return type;
    }

    public static VegetationConfig fromConfig() {
        JsonObject raw = Config.getTerrainJsonObject().getAsJsonObject("vegetation");
        if (raw == null) throw new IllegalStateException("vegetation missing in SceneConfig.json");

        if (raw.get("renderDistance") == null)
            throw new IllegalStateException(
                    "vegetation.renderDistance missing in SceneConfig.json");
        int renderDistance = raw.get("renderDistance").getAsInt();

        JsonObject typesJson = raw.getAsJsonObject("types");
        if (typesJson == null)
            throw new IllegalStateException("vegetation.types missing in SceneConfig.json");
        Map<String, VegetationType> types = new HashMap<>();
        for (String typeName : typesJson.keySet()) {
            JsonObject typeJson = typesJson.getAsJsonObject(typeName);

            if (typeJson.get("gridDensity") == null)
                throw new IllegalStateException(
                        "vegetation.types."
                                + typeName
                                + ".gridDensity missing in SceneConfig.json");
            int gridDensity = typeJson.get("gridDensity").getAsInt();

            JsonObject coverageJson = typeJson.getAsJsonObject("coveragePercent");
            Map<Biome, Double> coverage = new EnumMap<>(Biome.class);
            for (String biomeName : coverageJson.keySet()) {
                coverage.put(Biome.valueOf(biomeName), coverageJson.get(biomeName).getAsDouble());
            }

            JsonObject scaleJson = typeJson.getAsJsonObject("scale");
            if (scaleJson == null)
                throw new IllegalStateException(
                        "vegetation.types." + typeName + ".scale missing in SceneConfig.json");
            Map<String, Double> scale = new HashMap<>();
            for (String key : scaleJson.keySet()) {
                scale.put(key, scaleJson.get(key).getAsDouble());
            }

            types.put(typeName, new VegetationType(gridDensity, coverage, scale));
        }
        return new VegetationConfig(renderDistance, types);
    }
}
