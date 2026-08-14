package dev.ptxy.engine.map;

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
    // Kein Temp/Feuchte-Gitterplatz (row/col -1) -- wird nicht über resolveCell()/fromCell()
    // erreicht, sondern als Höhen-Override in Map.getBiome() unabhängig von der Zelle vergeben.
    ALPINE(-1, -1, "Alpin/Gebirge");

    private final int row;
    private final int col;
    private final String displayName;

    Biome(int row, int col, String displayName) {
        this.row = row;
        this.col = col;
        this.displayName = displayName;
    }

    public int row() {
        return row;
    }

    public int col() {
        return col;
    }

    public String displayName() {
        return displayName;
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
