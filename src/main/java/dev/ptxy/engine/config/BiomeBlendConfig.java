package dev.ptxy.engine.config;

import com.google.gson.JsonObject;

public record BiomeBlendConfig(
        double alpineHeightFraction,
        double worldBlendWidthBaseMeters,
        double worldBlendWidthPerHeightMeter,
        double maxHeightGapMetersForBlendWidth,
        double borderWarpPeriodMeters,
        double borderWarpDistanceMeters) {

    public static BiomeBlendConfig fromConfig() {
        JsonObject raw = Config.getTerrainJsonObject().getAsJsonObject("biomeBlend");
        if (raw == null)
            throw new IllegalStateException("terrain.biomeBlend missing in SceneConfig.json");
        return new BiomeBlendConfig(
                raw.get("alpineHeightFraction").getAsDouble(),
                raw.get("worldBlendWidthBaseMeters").getAsDouble(),
                raw.get("worldBlendWidthPerHeightMeter").getAsDouble(),
                raw.get("maxHeightGapMetersForBlendWidth").getAsDouble(),
                raw.get("borderWarpPeriodMeters").getAsDouble(),
                raw.get("borderWarpDistanceMeters").getAsDouble());
    }
}
