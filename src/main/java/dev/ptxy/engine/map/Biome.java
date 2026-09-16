package dev.ptxy.engine.map;

import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public enum Biome {
    TUNDRA_STEPPE(0, 0, "Tundra/Steppe"),
    COOL_GRASSLAND(0, 1, "kühles Grasland"),
    TAIGA(0, 2, "Taiga (Borealer Wald)"),
    SEMI_DESERT_STEPPE(1, 0, "Halbwüste/Steppe"),
    SAVANNA_PRAIRIE(1, 1, "Savanne/Präriegrasland"),
    DECIDUOUS_FOREST(1, 2, "Laubwald"),
    HOT_DESERT(2, 0, "heiße Wüste"),
    SHRUBLAND_CHAPARRAL(2, 1, "Buschland/Chaparral"),
    RAINFOREST(2, 2, "Regenwald"),

    ALPINE(-1, -1, "Alpin/Gebirge");

    private final int row;
    private final int col;
    private final String displayName;

    Biome(int row, int col, String displayName) {
        this.row = row;
        this.col = col;
        this.displayName = displayName;
    }

    public static Biome fromCell(int row, int col) {
        for (Biome biome : values()) {
            if (biome.row == row && biome.col == col) {
                return biome;
            }
        }
        throw new IllegalArgumentException("Keine Biom-Zelle für row=" + row + ", col=" + col);
    }
}
