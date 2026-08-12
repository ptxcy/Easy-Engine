package dev.ptxy.engine.config;

import com.google.gson.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

public final class Config {
    private static final JsonObject CONFIG_JSON;
    private static final WindowConfig WINDOW_CONFIG;
    private static final TerrainConfig TERRAIN_CONFIG;
    private static final PlayerConfig PLAYER_CONFIG;
    private static final BiomeLookUpTable BIOMES_LOOK_UP_TABLE;
    private static final TerrainParams TERRAIN_PARAMS;

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

        WINDOW_CONFIG = WindowConfig.fromConfig();
        TERRAIN_CONFIG = TerrainConfig.fromConfig();
        PLAYER_CONFIG = PlayerConfig.fromConfig();
        BIOMES_LOOK_UP_TABLE = BiomeLookUpTable.fromConfig();
        TERRAIN_PARAMS = TerrainParams.fromTerrainConfig(TERRAIN_CONFIG);
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

    public static JsonObject getWindowJsonObject() {
        return CONFIG_JSON.getAsJsonObject("window");
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

    public static WindowConfig getWindowConfig() {
        return WINDOW_CONFIG;
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

    // Mutable Live-Parameter -- Map/ChunkManager lesen dieselbe Instanz, die der Editor
    // beschreibt. Anders als TERRAIN_CONFIG (Record, unveränderlich) ist das hier der Hebel
    // für Laufzeit-Regenerierung ohne App-Neustart.
    public static TerrainParams getTerrainParams() {
        return TERRAIN_PARAMS;
    }

    // Schreibt die aktuellen TerrainParams + BiomeLookUpTable-Werte zurück in die ursprüngliche
    // SceneConfig.json auf der Festplatte (Editor-"Speichern"-Button) -- damit Werte, die im
    // Terrain-Editor getunt wurden, den App-Neustart überleben und committed/gepusht werden
    // können. Alles andere in der Datei (player, preloadAssets/-Shaders, chunkSize/Resolution/
    // renderDistance/workerThreads) bleibt unverändert, da CONFIG_JSON schon das komplette
    // ursprüngliche Dokument ist und nur die von TerrainParams besessenen Felder überschrieben
    // werden. Gibt den Zielpfad zurück; wirft, wenn er nicht ermittelbar ist oder das Schreiben
    // fehlschlägt (vom Aufrufer für eine Status-Anzeige zu fangen).
    public static String saveTerrainParams() {
        JsonObject terrain = getTerrainJsonObject();
        terrain.addProperty("heightAmplitude", TERRAIN_PARAMS.heightAmplitude());

        JsonObject noise = terrain.getAsJsonObject("noise");
        noise.addProperty("heightOctaves", TERRAIN_PARAMS.octaves());
        noise.addProperty("heightScale", TERRAIN_PARAMS.heightScale());
        noise.addProperty("tempScale", TERRAIN_PARAMS.tempScale());
        noise.addProperty("humidityScale", TERRAIN_PARAMS.humidityScale());
        noise.addProperty("heightTempLapse", TERRAIN_PARAMS.heightTempLapse());

        JsonObject biomeTable = getBiomsLookUpTableJsonObject();
        biomeTable.add("amplitude", toJsonArray(BIOMES_LOOK_UP_TABLE.amplitude()));
        biomeTable.add("frequency", toJsonArray(BIOMES_LOOK_UP_TABLE.frequency()));
        biomeTable.add("persistence", toJsonArray(BIOMES_LOOK_UP_TABLE.persistence()));
        biomeTable.add("lacunarity", toJsonArray(BIOMES_LOOK_UP_TABLE.lacunarity()));
        biomeTable.add("redistribution", toJsonArray(BIOMES_LOOK_UP_TABLE.redistribution()));
        biomeTable.add(
                "valleyRedistribution", toJsonArray(BIOMES_LOOK_UP_TABLE.valleyRedistribution()));

        String path = System.getProperty("scene.config.source");
        if (path == null) {
            throw new IllegalStateException(
                    "System property 'scene.config.source' nicht gesetzt -- nur beim Start via"
                            + " './gradlew runEngine' gesetzt.");
        }

        try (Writer writer =
                new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(CONFIG_JSON, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Speichern nach " + path + " fehlgeschlagen", e);
        }
        return path;
    }

    private static JsonArray toJsonArray(double[][] table) {
        JsonArray outer = new JsonArray();
        for (double[] row : table) {
            JsonArray inner = new JsonArray();
            for (double v : row) inner.add(v);
            outer.add(inner);
        }
        return outer;
    }
}
