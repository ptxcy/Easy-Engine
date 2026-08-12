package dev.ptxy.engine.config;

import com.google.gson.JsonObject;

public record TerrainConfig(
        int chunkSize,
        int chunkResolution,
        int renderDistance,
        float heightAmplitude,
        int workerThreads,
        int heightOctaves,
        double heightScale,
        double tempScale,
        double humidityScale,
        double heightTempLapse) {

    public static TerrainConfig fromConfig() {
        JsonObject raw = Config.getTerrainJsonObject();
        JsonObject noise = raw.getAsJsonObject("noise");
        return new TerrainConfig(
                raw.get("chunkSize").getAsInt(),
                raw.get("chunkResolution").getAsInt(),
                raw.get("renderDistance").getAsInt(),
                raw.get("heightAmplitude").getAsFloat(),
                raw.get("workerThreads").getAsInt(),
                noise.get("heightOctaves").getAsInt(),
                noise.get("heightScale").getAsDouble(),
                noise.get("tempScale").getAsDouble(),
                noise.get("humidityScale").getAsDouble(),
                noise.get("heightTempLapse").getAsDouble());
    }
}
