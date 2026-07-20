package dev.ptxy.engine.config;

import com.google.gson.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

public final class Config {
    private static final JsonObject CONFIG_JSON;
    private static final TerrainConfig TERRAIN_CONFIG;
    private static final PlayerConfig PLAYER_CONFIG;
    private static final BiomeLookUpTable BIOMES_LOOK_UP_TABLE;

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

        TERRAIN_CONFIG = TerrainConfig.fromConfig();
        PLAYER_CONFIG = PlayerConfig.fromConfig();
        BIOMES_LOOK_UP_TABLE = BiomeLookUpTable.fromConfig();
    }

    private Config() {
        throw new IllegalStateException("Utility class");
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

    public static JsonObject getTerrainJsonObject() {
        return CONFIG_JSON.getAsJsonObject("terrain");
    }

    public static JsonObject getPlayerJsonObject() {
        return CONFIG_JSON.getAsJsonObject("player");
    }

    public static JsonObject getBiomsLookUpTableJsonObject() {
        return getTerrainJsonObject().getAsJsonObject("biomsLookUpTable");
    }

    public static TerrainConfig getTerrainConfig() {
        return TERRAIN_CONFIG;
    }

    public static PlayerConfig getPlayerConfig() {
        return PLAYER_CONFIG;
    }

    public static BiomeLookUpTable getBiomesLookUpTable() {
        return BIOMES_LOOK_UP_TABLE;
    }
}
