package dev.ptxy.engine.objects.assets;

import java.nio.file.Path;

public final class AssetPaths {
    private static final Path ASSETS_ROOT = Path.of("assets");
    private static final Path TEXTURES = ASSETS_ROOT.resolve("textures");

    private AssetPaths() {}

    public static Path texture(String name) {
        if(name == null || name.isEmpty()) return ASSETS_ROOT.resolve("textures").resolve("default.png");
        return ASSETS_ROOT.resolve("textures").resolve(name);
    }

    public static Path defaultTexture() {
        return TEXTURES.resolve("default.png");
    }

    public static Path asset(String name) {
        return ASSETS_ROOT.resolve(name);
    }
}
