package dev.ptxy.engine.config;

import com.google.gson.JsonObject;

public record WindowConfig(String title, int width, int height, boolean vsync) {

    public static WindowConfig fromConfig() {
        JsonObject raw = Config.getWindowJsonObject();
        return new WindowConfig(
                raw.get("title").getAsString(),
                raw.get("width").getAsInt(),
                raw.get("height").getAsInt(),
                raw.get("vsync").getAsBoolean());
    }
}
