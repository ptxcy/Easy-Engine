package dev.ptxy.engine.config;

import com.google.gson.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

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

    public static JsonObject getConfigJson(){
        return CONFIG_JSON;
    }
}
