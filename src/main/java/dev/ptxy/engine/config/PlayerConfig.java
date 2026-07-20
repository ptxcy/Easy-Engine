package dev.ptxy.engine.config;

import com.google.gson.JsonObject;

public record PlayerConfig(float moveStep, float rotateStepDegrees, float speedRate) {

    public static PlayerConfig fromConfig() {
        JsonObject raw = Config.getPlayerJsonObject();
        return new PlayerConfig(
                raw.get("moveStep").getAsFloat(),
                raw.get("rotateStepDegrees").getAsFloat(),
                raw.get("speedRate").getAsFloat());
    }
}
