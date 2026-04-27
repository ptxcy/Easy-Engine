package dev.ptxy.engine.config;

import com.google.gson.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

public class Config {
    private static final JsonObject CONFIG_JSON;

    static {
        InputStream is = Config.class.getResourceAsStream("/SceneConfig.json");
        if (is == null) {
            throw new IllegalStateException("SceneConfig.json not found in resources");
        }

        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            CONFIG_JSON = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException("Failed to preload SceneConfig.json", e);
        }
    }

    public static String[] getPreloadAssets() {
        return StreamSupport.stream(
                        CONFIG_JSON.get("preloadAssets").getAsJsonArray().spliterator(), false)
                .map(JsonElement::getAsString)
                .toArray(String[]::new);
    }

    public static String[] getPreloadShaders() {
        return StreamSupport.stream(
                        CONFIG_JSON.get("preloadShaders").getAsJsonArray().spliterator(), false)
                .map(JsonElement::getAsString)
                .toArray(String[]::new);
    }

    public static Integer getMapConfigSize() {
        return CONFIG_JSON.getAsJsonObject("mapConfig").get("size").getAsInt();
    }

    public static Float getMeadowsConfigSharpness() {
        return CONFIG_JSON
                .getAsJsonObject("mapConfig")
                .getAsJsonObject("meadowsConfig")
                .get("sharpness")
                .getAsFloat();
    }

    public static Integer getMeadowsConfigResolution() {
        return CONFIG_JSON
                .getAsJsonObject("mapConfig")
                .getAsJsonObject("meadowsConfig")
                .get("resolution")
                .getAsInt();
    }

    public static Float getMeadowsConfigMixStrength() {
        return CONFIG_JSON
                .getAsJsonObject("mapConfig")
                .getAsJsonObject("meadowsConfig")
                .get("mixStrength")
                .getAsFloat();
    }

    public static Integer getMeadowsConfigSize() {
        return CONFIG_JSON
                .getAsJsonObject("mapConfig")
                .getAsJsonObject("meadowsConfig")
                .get("size")
                .getAsInt();
    }

    public static Float getMeadowsConfigTextureNoiseScale() {
        return CONFIG_JSON
                .getAsJsonObject("mapConfig")
                .getAsJsonObject("meadowsConfig")
                .get("grassTextureNoiseScale")
                .getAsFloat();
    }
}
