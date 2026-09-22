package dev.ptxy.engine.ui;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nuklear.Nuklear.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import dev.ptxy.engine.config.BiomeLookUpTable;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.config.TerrainParams;
import dev.ptxy.engine.config.VegetationConfig;
import dev.ptxy.engine.map.Biome;
import dev.ptxy.engine.map.ChunkManager;
import dev.ptxy.engine.map.VegetationController;
import dev.ptxy.engine.world.Player;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.BufferUtils;
import org.lwjgl.nuklear.NkAllocator;
import org.lwjgl.nuklear.NkBuffer;
import org.lwjgl.nuklear.NkColor;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.NkDrawCommand;
import org.lwjgl.nuklear.NkDrawNullTexture;
import org.lwjgl.nuklear.NkDrawVertexLayoutElement;
import org.lwjgl.nuklear.NkMouse;
import org.lwjgl.nuklear.NkRect;
import org.lwjgl.nuklear.NkUserFont;
import org.lwjgl.nuklear.NkUserFontGlyph;
import org.lwjgl.nuklear.NkVec2;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.system.MemoryStack;

public final class TerrainEditorGui {
    private static final int BUFFER_INITIAL_SIZE = 4 * 1024;
    private static final int MAX_VERTEX_BUFFER = 512 * 1024;
    private static final int MAX_ELEMENT_BUFFER = 128 * 1024;
    private static final int FONT_HEIGHT = 16;
    private static final int BITMAP_SIZE = 1024;

    private static final NkDrawVertexLayoutElement.Buffer VERTEX_LAYOUT =
            NkDrawVertexLayoutElement.create(4)
                    .position(0)
                    .attribute(NK_VERTEX_POSITION)
                    .format(NK_FORMAT_FLOAT)
                    .offset(0)
                    .position(1)
                    .attribute(NK_VERTEX_TEXCOORD)
                    .format(NK_FORMAT_FLOAT)
                    .offset(8)
                    .position(2)
                    .attribute(NK_VERTEX_COLOR)
                    .format(NK_FORMAT_R8G8B8A8)
                    .offset(16)
                    .position(3)
                    .attribute(NK_VERTEX_ATTRIBUTE_COUNT)
                    .format(NK_FORMAT_COUNT)
                    .offset(0)
                    .flip();

    private final long windowHandle;
    private final ChunkManager chunkManager;
    private final Player player;

    private final NkAllocator allocator;
    private final NkContext ctx = NkContext.create();
    private final NkUserFont defaultFont = NkUserFont.create();
    private final NkBuffer cmds = NkBuffer.create();
    private final NkDrawNullTexture nullTexture = NkDrawNullTexture.create();

    private int vbo;
    private int vao;
    private int ebo;
    private int program;
    private int vertexShader;
    private int fragmentShader;
    private int uniformTex;
    private int uniformProj;
    private int fontTexId;

    private int width;
    private int height;
    private int displayWidth;
    private int displayHeight;

    @Getter @Setter private boolean open = false;

    @Getter @Setter private boolean infoOpen = true;

    @Setter private int fps = 0;

    private String saveStatus = "";

    private int selectedBiome = ALL_BIOMES_INDEX;

    private int editBiome = 8;

    private final NumberField amplitudeField = new NumberField();
    private final NumberField frequencyField = new NumberField();
    private final NumberField persistenceField = new NumberField();
    private final NumberField lacunarityField = new NumberField();
    private final NumberField redistributionField = new NumberField();
    private final NumberField valleyRedistributionField = new NumberField();
    private final NumberField octavesField = new NumberField();
    private final NumberField heightScaleField = new NumberField();
    private final NumberField heightAmplitudeField = new NumberField();
    private final NumberField tempScaleField = new NumberField();
    private final NumberField humidityScaleField = new NumberField();
    private final NumberField heightTempLapseField = new NumberField();

    private final NumberField grassCoverageField = new NumberField();
    private final NumberField treeCoverageField = new NumberField();
    private final NumberField rockCoverageField = new NumberField();
    private final NumberField clumpScaleField = new NumberField();

    private final NumberField grassWidthMinField = new NumberField();
    private final NumberField grassWidthMaxField = new NumberField();
    private final NumberField grassHeightMinField = new NumberField();
    private final NumberField grassHeightMaxField = new NumberField();
    private final NumberField treeHeightMinField = new NumberField();
    private final NumberField treeHeightMaxField = new NumberField();
    private final NumberField treeRadiusMinField = new NumberField();
    private final NumberField treeRadiusMaxField = new NumberField();
    private final NumberField rockHeightMinField = new NumberField();
    private final NumberField rockHeightMaxField = new NumberField();
    private final NumberField rockRadiusMinField = new NumberField();
    private final NumberField rockRadiusMaxField = new NumberField();

    private ByteBuffer ttf;

    public TerrainEditorGui(long windowHandle, ChunkManager chunkManager, Player player) {
        this.windowHandle = windowHandle;
        this.chunkManager = chunkManager;
        this.player = player;
        this.allocator =
                NkAllocator.create()
                        .alloc((handle, old, size) -> nmemAllocChecked(size))
                        .mfree((handle, ptr) -> nmemFree(ptr));

        setupInput();
        nk_init(ctx, allocator, null);
        setupClipboard();
        setupGl();
        setupFont();
        nk_style_set_font(ctx, defaultFont);
    }

    public void beginInput() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);

            glfwGetWindowSize(windowHandle, w, h);
            width = w.get(0);
            height = h.get(0);

            glfwGetFramebufferSize(windowHandle, w, h);
            displayWidth = w.get(0);
            displayHeight = h.get(0);
        }
        nk_input_begin(ctx);
    }

    public void endInput() {
        NkMouse mouse = ctx.input().mouse();
        if (mouse.grab()) {
            glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
        } else if (mouse.grabbed()) {
            float prevX = mouse.prev().x();
            float prevY = mouse.prev().y();
            glfwSetCursorPos(windowHandle, prevX, prevY);
            mouse.pos().x(prevX);
            mouse.pos().y(prevY);
        } else if (mouse.ungrab()) {
            glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }
        nk_input_end(ctx);
    }

    public void render() {
        if (open) {
            layout();
        }
        if (infoOpen) {
            infoLayout();
        }
        draw();
    }

    private static final String[] BIOME_NAMES = {
        "Tundra/Steppe", "kühles Grasland", "Taiga (Borealer Wald)",
        "Halbwüste/Steppe", "Savanne/Präriegrasland", "Laubwald",
        "heiße Wüste", "Buschland/Chaparral", "Regenwald"
    };
    private static final int ALL_BIOMES_INDEX = BIOME_NAMES.length;
    private static final String[] POOL_ITEMS = buildPoolItems();
    private static final String POOL_ITEMS_JOINED = String.join("\0", POOL_ITEMS);

    private static String[] buildPoolItems() {
        String[] items = java.util.Arrays.copyOf(BIOME_NAMES, BIOME_NAMES.length + 1);
        items[ALL_BIOMES_INDEX] = "Alle Biome (kompletter Mix)";
        return items;
    }

    private void infoLayout() {
        int[] cell = chunkManager.getNoiseMap().getBiomeCell(player.getX(), player.getZ());
        String biome = BIOME_NAMES[cell[0] * 3 + cell[1]];

        try (MemoryStack stack = stackPush()) {
            NkRect rect = NkRect.malloc(stack);
            if (nk_begin(
                    ctx,
                    "Info",
                    nk_rect(10, 10, 240, 68, rect),
                    NK_WINDOW_BORDER | NK_WINDOW_NO_SCROLLBAR)) {
                nk_layout_row_dynamic(ctx, 18, 1);
                nk_label(ctx, "FPS: " + fps, NK_TEXT_LEFT);
                nk_layout_row_dynamic(ctx, 18, 1);
                nk_label(ctx, "Biom: " + biome, NK_TEXT_LEFT);
            }
            nk_end(ctx);
        }
    }

    private void layout() {
        TerrainParams params = Config.getTerrainParams();
        BiomeLookUpTable table = Config.getBiomesLookUpTable();

        try (MemoryStack stack = stackPush()) {
            NkRect rect = NkRect.malloc(stack);
            float panelWidth = 340;
            float panelX = Math.max(10, width - panelWidth - 10);

            if (nk_begin(
                    ctx,
                    "Terrain Editor",
                    nk_rect(panelX, 20, panelWidth, height - 40, rect),
                    NK_WINDOW_BORDER | NK_WINDOW_MOVABLE | NK_WINDOW_SCALABLE | NK_WINDOW_TITLE)) {

                nk_layout_row_dynamic(ctx, 18, 1);
                nk_label(ctx, "Pool (was die Welt gerade zeigt)", NK_TEXT_LEFT);
                nk_layout_row_dynamic(ctx, 25, 1);
                try (MemoryStack comboStack = stackPush()) {
                    selectedBiome =
                            nk_combo_string(
                                    ctx,
                                    POOL_ITEMS_JOINED,
                                    selectedBiome,
                                    POOL_ITEMS.length,
                                    22,
                                    NkVec2.malloc(comboStack).set(nk_widget_width(ctx), 200));
                }
                if (selectedBiome == ALL_BIOMES_INDEX) {

                    params.minRow(0);
                    params.maxRow(2);
                    params.minCol(0);
                    params.maxCol(2);
                } else {
                    int poolRow = selectedBiome / 3;
                    int poolCol = selectedBiome % 3;
                    params.minRow(poolRow);
                    params.maxRow(poolRow);
                    params.minCol(poolCol);
                    params.maxCol(poolCol);
                    editBiome = selectedBiome;
                }
                int row = editBiome / 3;
                int col = editBiome % 3;

                nk_layout_row_dynamic(ctx, 30, 1);
                if (nk_button_label(ctx, "Position übernehmen")) {
                    double rawTemp =
                            chunkManager
                                    .getNoiseMap()
                                    .getRawTemperature(player.getX(), player.getZ());
                    double rawHumidity =
                            chunkManager.getNoiseMap().getHumidity(player.getX(), player.getZ());
                    selectedBiome =
                            clampCellIndex((int) Math.round((rawTemp + 1) / 2.0 * 2)) * 3
                                    + clampCellIndex((int) Math.round((rawHumidity + 1) / 2.0 * 2));
                    editBiome = selectedBiome;
                }

                sectionDivider();
                nk_layout_row_dynamic(ctx, 18, 1);
                nk_label(ctx, "NUR " + BIOME_NAMES[editBiome] + " - Form-Regler", NK_TEXT_LEFT);
                table.amplitude()[row][col] =
                        amplitudeField.draw(
                                "Amplitude:",
                                "Wert 0 bis 3",
                                "Wie hoch die Berge in diesem Biom werden -- höher bedeutet"
                                        + " größere Höhenunterschiede, niedriger eine flachere"
                                        + " Landschaft. Ändert nur die Größe, nicht die Form.",
                                table.amplitude()[row][col],
                                0,
                                3);
                table.frequency()[row][col] =
                        frequencyField.draw(
                                "Frequenz:",
                                "Wert 0.1 bis 5",
                                "Wie eng Hügel und Täler beieinander liegen -- hoch: viele kleine"
                                        + " Hügel dicht gedrängt. Niedrig: wenige breite, weit"
                                        + " auseinanderliegende Berge. Ändert nur den Maßstab,"
                                        + " nicht die Form.",
                                table.frequency()[row][col],
                                0.1,
                                5);
                table.persistence()[row][col] =
                        persistenceField.draw(
                                "Persistence:",
                                "Wert 0.05 bis 0.95",
                                "Wie glatt oder zerklüftet die Oberfläche wirkt -- niedrig: sanfte,"
                                        + " plateauartige Hügel wie Dünen. Hoch: raue, zerklüftete"
                                        + " Felsen mit vielen kleinen Unebenheiten.",
                                table.persistence()[row][col],
                                0.05,
                                0.95);
                table.lacunarity()[row][col] =
                        lacunarityField.draw(
                                "Lacunarity:",
                                "Wert 1 bis 4",
                                "Wie stark sich grobe und feine Unebenheiten unterscheiden --"
                                        + " niedrig: wirkt flach, kaum Feindetail. Um 2: natürlich"
                                        + " wirkendes Terrain. Hoch: extrem zerrissen, viele"
                                        + " Detailebenen übereinander.",
                                table.lacunarity()[row][col],
                                1,
                                4);
                table.redistribution()[row][col] =
                        redistributionField.draw(
                                "Redistribution (Gipfel):",
                                "Wert 0.2 bis 5, neutral bei 1",
                                "Wirkt nur auf Gipfel (Werte über der Grundfläche). Verbreitert"
                                    + " flache Bereiche und verschmälert/verschärft Gipfel, ohne"
                                    + " Abstand oder Höhe zu ändern (das machen Frequenz bzw."
                                    + " Amplitude). Über 1: schmale, scharfe Gipfel auf breiter"
                                    + " Fläche. Unter 1: umgekehrt -- breite Plateaus. Genau 1:"
                                    + " kein Effekt.",
                                table.redistribution()[row][col],
                                0.2,
                                5);
                table.valleyRedistribution()[row][col] =
                        valleyRedistributionField.draw(
                                "Redistribution (Täler):",
                                "Wert 0.2 bis 8, neutral bei 1",
                                "Dieselbe Formel wie oben, aber nur für Täler (Werte unter der"
                                    + " Grundfläche) -- unabhängig von Gipfeln einstellbar. Hoch"
                                    + " (z.B. 5-8): auch seltene, tiefe Rausch-Ausreißer werden"
                                    + " fast komplett unterdrückt -- keine unmotivierten Einbrüche/"
                                    + " Senken mehr, Täler bleiben durchgehend ruhig. Genau 1: kein"
                                    + " Effekt (symmetrisch zu Gipfeln).",
                                table.valleyRedistribution()[row][col],
                                0.2,
                                8);

                sectionDivider();
                nk_layout_row_dynamic(ctx, 18, 1);
                nk_label(ctx, "NUR " + BIOME_NAMES[editBiome] + " - Vegetation", NK_TEXT_LEFT);
                Biome editBiomeEnum = Biome.fromCell(row, col);
                VegetationConfig vegConfig = Config.getVegetationConfig();
                VegetationConfig.VegetationType grassType = vegConfig.type("grass");
                VegetationConfig.VegetationType treeType = vegConfig.type("tree");
                VegetationConfig.VegetationType rockType = vegConfig.type("rock");

                grassType
                        .coveragePercent()
                        .put(
                                editBiomeEnum,
                                grassCoverageField.draw(
                                        "Gras-Bedeckung %:",
                                        "Wert 0 bis 100",
                                        "Wie viel Prozent der Fläche in diesem Biom mit Gras"
                                                + " bedeckt ist. Wirkt erst nach REGENERATE.",
                                        grassType.coverage(editBiomeEnum),
                                        0,
                                        100));
                treeType.coveragePercent()
                        .put(
                                editBiomeEnum,
                                treeCoverageField.draw(
                                        "Baum-Bedeckung %:",
                                        "Wert 0 bis 5",
                                        "Wie viel Prozent der Fläche in diesem Biom mit Bäumen"
                                                + " bedeckt ist -- typische Werte liegen weit unter"
                                                + " 1%. Wirkt erst nach REGENERATE.",
                                        treeType.coverage(editBiomeEnum),
                                        0,
                                        5));
                rockType.coveragePercent()
                        .put(
                                editBiomeEnum,
                                rockCoverageField.draw(
                                        "Fels-Bedeckung %:",
                                        "Wert 0 bis 5",
                                        "Wie viel Prozent der Fläche in diesem Biom mit Felsen"
                                                + " bedeckt ist. Wirkt erst nach REGENERATE.",
                                        rockType.coverage(editBiomeEnum),
                                        0,
                                        5));

                boolean clumpingActiveForBiome =
                        editBiomeEnum == Biome.TUNDRA_STEPPE
                                || editBiomeEnum == Biome.SAVANNA_PRAIRIE
                                || editBiomeEnum == Biome.DECIDUOUS_FOREST;
                if (clumpingActiveForBiome) {
                    vegConfig.clumpScale().merge(editBiomeEnum, 0.004, (old, def) -> old);
                    vegConfig
                            .clumpScale()
                            .put(
                                    editBiomeEnum,
                                    clumpScaleField.draw(
                                            "Clump-Skala:",
                                            "Wert 0.0001 bis 0.02",
                                            "Wie großflächig sich Vegetation zu dichteren/"
                                                    + "lichteren Flecken zusammenballt -- kleiner:"
                                                    + " große, weiträumige Büschel-Zonen. Größer:"
                                                    + " viele kleine, eng beieinanderliegende"
                                                    + " Flecken. Wirkt erst nach REGENERATE.",
                                            vegConfig.clumpScale().get(editBiomeEnum),
                                            0.0001,
                                            0.02));
                } else {
                    nk_layout_row_dynamic(ctx, 18, 1);
                    nk_label(
                            ctx, "(Clumping nur für Tundra/Savanne/Regenwald aktiv)", NK_TEXT_LEFT);
                }

                sectionDivider();
                nk_layout_row_dynamic(ctx, 18, 1);
                nk_label(ctx, "ALLE BIOME - Globale Regler", NK_TEXT_LEFT);
                params.octaves(
                        (int)
                                Math.round(
                                        octavesField.draw(
                                                "Octaves:",
                                                "Wert 1 bis 8 (ganzzahlig)",
                                                "Wie viele Detailebenen übereinandergelegt werden"
                                                    + " -- wenige: weiche, runde Hügel ohne"
                                                    + " Kleinstdetails. Viele: scharfe Felskanten"
                                                    + " und Erosionsrillen.",
                                                params.octaves(),
                                                1,
                                                8)));
                params.heightScale(
                        heightScaleField.draw(
                                "Height Scale:",
                                "Wert 0.001 bis 0.2",
                                "Wie weit Berge und Täler insgesamt auseinanderliegen -- klein:"
                                        + " riesige, weitläufige Gebirgszüge. Groß: enge, schnell"
                                        + " wechselnde Hügellandschaft.",
                                params.heightScale(),
                                0.001,
                                0.2));
                params.heightAmplitude(
                        (float)
                                heightAmplitudeField.draw(
                                        "Height Amp.:",
                                        "Wert 1 bis 300",
                                        "Wie hoch die höchsten Berge insgesamt werden, in"
                                                + " Welteinheiten/Metern.",
                                        params.heightAmplitude(),
                                        1,
                                        300));
                params.tempScale(
                        tempScaleField.draw(
                                "Temp Scale:",
                                "Wert 0.00001 bis 0.01",
                                "Wie großflächig die Temperaturzonen sind -- klein halten, sonst"
                                        + " wirken Biome fleckig statt als große, zusammenhängende"
                                        + " Zonen.",
                                params.tempScale(),
                                0.00001,
                                0.01));
                params.humidityScale(
                        humidityScaleField.draw(
                                "Humid. Scale:",
                                "Wert 0.00001 bis 0.01",
                                "Wie großflächig die Feuchtezonen sind -- klein halten, sonst"
                                        + " wirken Biome fleckig statt als große, zusammenhängende"
                                        + " Zonen.",
                                params.humidityScale(),
                                0.00001,
                                0.01));
                params.heightTempLapse(
                        heightTempLapseField.draw(
                                "Temp Lapse:",
                                "Wert 0 bis 0.05",
                                "Wie stark es mit der Höhe kälter wird -- höher bedeutet:"
                                        + " Bergspitzen kippen schneller in die kalte Ecke der"
                                        + " Biom-Tabelle (Richtung Zeile 0).",
                                params.heightTempLapse(),
                                0,
                                0.05));

                sectionDivider();
                nk_layout_row_dynamic(ctx, 18, 1);
                nk_label(
                        ctx,
                        "VEGETATION - Globale Regler (alle Biome, sofort wirksam)",
                        NK_TEXT_LEFT);
                grassType
                        .scale()
                        .put(
                                "widthMin",
                                grassWidthMinField.draw(
                                        "Gras Breite min:",
                                        "Wert 0.01 bis 0.3",
                                        "Minimale Halmbreite in Weltmetern.",
                                        grassType.scale("widthMin"),
                                        0.01,
                                        0.3));
                grassType
                        .scale()
                        .put(
                                "widthMax",
                                grassWidthMaxField.draw(
                                        "Gras Breite max:",
                                        "Wert 0.01 bis 0.3",
                                        "Maximale Halmbreite in Weltmetern.",
                                        grassType.scale("widthMax"),
                                        0.01,
                                        0.3));
                grassType
                        .scale()
                        .put(
                                "heightMin",
                                grassHeightMinField.draw(
                                        "Gras Höhe min:",
                                        "Wert 0.1 bis 3",
                                        "Minimale Halmhöhe in Weltmetern.",
                                        grassType.scale("heightMin"),
                                        0.1,
                                        3));
                grassType
                        .scale()
                        .put(
                                "heightMax",
                                grassHeightMaxField.draw(
                                        "Gras Höhe max:",
                                        "Wert 0.1 bis 3",
                                        "Maximale Halmhöhe in Weltmetern.",
                                        grassType.scale("heightMax"),
                                        0.1,
                                        3));
                treeType.scale()
                        .put(
                                "heightMin",
                                treeHeightMinField.draw(
                                        "Baum Höhe min:",
                                        "Wert 1 bis 30",
                                        "Minimale Baumhöhe in Weltmetern.",
                                        treeType.scale("heightMin"),
                                        1,
                                        30));
                treeType.scale()
                        .put(
                                "heightMax",
                                treeHeightMaxField.draw(
                                        "Baum Höhe max:",
                                        "Wert 1 bis 30",
                                        "Maximale Baumhöhe in Weltmetern.",
                                        treeType.scale("heightMax"),
                                        1,
                                        30));
                treeType.scale()
                        .put(
                                "radiusMin",
                                treeRadiusMinField.draw(
                                        "Baum Radius min:",
                                        "Wert 0.2 bis 5",
                                        "Minimaler Kronenradius-Faktor.",
                                        treeType.scale("radiusMin"),
                                        0.2,
                                        5));
                treeType.scale()
                        .put(
                                "radiusMax",
                                treeRadiusMaxField.draw(
                                        "Baum Radius max:",
                                        "Wert 0.2 bis 5",
                                        "Maximaler Kronenradius-Faktor.",
                                        treeType.scale("radiusMax"),
                                        0.2,
                                        5));
                rockType.scale()
                        .put(
                                "heightMin",
                                rockHeightMinField.draw(
                                        "Fels Höhe min:",
                                        "Wert 0.1 bis 3",
                                        "Minimale Felshöhe in Weltmetern.",
                                        rockType.scale("heightMin"),
                                        0.1,
                                        3));
                rockType.scale()
                        .put(
                                "heightMax",
                                rockHeightMaxField.draw(
                                        "Fels Höhe max:",
                                        "Wert 0.1 bis 3",
                                        "Maximale Felshöhe in Weltmetern.",
                                        rockType.scale("heightMax"),
                                        0.1,
                                        3));
                rockType.scale()
                        .put(
                                "radiusMin",
                                rockRadiusMinField.draw(
                                        "Fels Radius min:",
                                        "Wert 0.1 bis 3",
                                        "Minimaler Felsradius-Faktor.",
                                        rockType.scale("radiusMin"),
                                        0.1,
                                        3));
                rockType.scale()
                        .put(
                                "radiusMax",
                                rockRadiusMaxField.draw(
                                        "Fels Radius max:",
                                        "Wert 0.1 bis 3",
                                        "Maximaler Felsradius-Faktor.",
                                        rockType.scale("radiusMax"),
                                        0.1,
                                        3));

                sectionDivider();
                nk_layout_row_dynamic(ctx, 24, 1);
                try (MemoryStack vegStack = stackPush()) {
                    ByteBuffer vegetationActive =
                            vegStack.bytes((byte) (chunkManager.isVegetationEnabled() ? 1 : 0));
                    if (nk_checkbox_label(ctx, "Vegetation rendern", vegetationActive)) {
                        chunkManager.setVegetationEnabled(vegetationActive.get(0) != 0);
                    }
                }

                VegetationController vegetationController = chunkManager.getVegetationController();
                nk_layout_row_dynamic(ctx, 24, 1);
                try (MemoryStack clumpStack = stackPush()) {
                    ByteBuffer clumpingActive =
                            clumpStack.bytes(
                                    (byte)
                                            (vegetationController.isOrganicClumpingEnabled()
                                                    ? 1
                                                    : 0));
                    if (nk_checkbox_label(ctx, "Organisches Clumping", clumpingActive)) {
                        vegetationController.setOrganicClumpingEnabled(clumpingActive.get(0) != 0);
                    }
                }

                nk_layout_row_dynamic(ctx, 24, 1);
                try (MemoryStack windStack = stackPush()) {
                    ByteBuffer windActive =
                            windStack.bytes((byte) (vegetationController.isWindEnabled() ? 1 : 0));
                    if (nk_checkbox_label(ctx, "Wind-Animation", windActive)) {
                        vegetationController.setWindEnabled(windActive.get(0) != 0);
                    }
                }

                nk_layout_row_dynamic(ctx, 24, 1);
                try (MemoryStack rockStack = stackPush()) {
                    ByteBuffer rocksActive =
                            rockStack.bytes((byte) (vegetationController.isRocksEnabled() ? 1 : 0));
                    if (nk_checkbox_label(ctx, "Steine rendern", rocksActive)) {
                        vegetationController.setRocksEnabled(rocksActive.get(0) != 0);
                    }
                }

                nk_layout_row_dynamic(ctx, 30, 1);
                if (nk_button_label(ctx, "REGENERATE")) {
                    chunkManager.regenerate();
                }

                nk_layout_row_dynamic(ctx, 30, 1);
                if (nk_button_label(ctx, "Speichern (überschreibt SceneConfig.json)")) {
                    try {
                        Config.saveConfig();
                        saveStatus = "Gespeichert -- SceneConfig.json aktualisiert.";
                    } catch (RuntimeException e) {
                        saveStatus = "Fehler beim Speichern: " + e.getMessage();
                    }
                }
                if (!saveStatus.isEmpty()) {

                    nk_layout_row_dynamic(ctx, 90, 1);
                    nk_label_wrap(ctx, saveStatus);
                }
            }
            nk_end(ctx);
        }
    }

    private static int clampCellIndex(int value) {
        return Math.max(0, Math.min(2, value));
    }

    private void sectionDivider() {
        nk_layout_row_dynamic(ctx, 10, 1);
        nk_spacing(ctx, 1);
        try (MemoryStack stack = stackPush()) {
            nk_layout_row_dynamic(ctx, 6, 1);
            nk_rule_horizontal(ctx, nk_rgb(110, 110, 110, NkColor.malloc(stack)), false);
        }
        nk_layout_row_dynamic(ctx, 10, 1);
        nk_spacing(ctx, 1);
    }

    private final class NumberField {
        private final ByteBuffer buffer = BufferUtils.createByteBuffer(32);
        private final int[] length = {0};
        private double committed = Double.NaN;
        private boolean activeLastFrame = false;

        double draw(
                String label,
                String rangeHint,
                String description,
                double currentValue,
                double min,
                double max) {
            if (!activeLastFrame && Double.compare(currentValue, committed) != 0) {
                setText(formatNumber(currentValue));
                committed = currentValue;
            }

            nk_layout_row_dynamic(ctx, 14, 1);
            nk_label(ctx, rangeHint, NK_TEXT_LEFT);

            nk_layout_row_dynamic(ctx, 22, 2);
            nk_label(ctx, label, NK_TEXT_LEFT);
            int flags =
                    nk_edit_string(
                            ctx,
                            NK_EDIT_FIELD,
                            buffer,
                            length,
                            buffer.capacity() - 1,
                            (edit, unicode) -> nnk_filter_float(edit, unicode));
            activeLastFrame = (flags & NK_EDIT_ACTIVE) != 0;

            if (nk_widget_is_hovered(ctx) && nk_tooltip_begin(ctx, 220)) {
                nk_layout_row_dynamic(ctx, 70, 1);
                nk_label_wrap(ctx, description);
                nk_tooltip_end(ctx);
            }

            byte[] bytes = new byte[length[0]];
            for (int i = 0; i < length[0]; i++) bytes[i] = buffer.get(i);
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            try {
                committed = Math.max(min, Math.min(max, Double.parseDouble(text)));
            } catch (NumberFormatException e) {

            }
            return committed;
        }

        private void setText(String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            buffer.clear();
            buffer.put(bytes);
            buffer.clear();
            length[0] = bytes.length;
        }
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        String text = String.format(java.util.Locale.ROOT, "%.6f", value);
        text = text.replaceAll("0+$", "");
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private void setupInput() {
        glfwSetScrollCallback(
                windowHandle,
                (window, xoffset, yoffset) -> {
                    try (MemoryStack stack = stackPush()) {
                        NkVec2 scroll = NkVec2.malloc(stack).x((float) xoffset).y((float) yoffset);
                        nk_input_scroll(ctx, scroll);
                    }
                });
        glfwSetCharCallback(windowHandle, (window, codepoint) -> nk_input_unicode(ctx, codepoint));
        glfwSetKeyCallback(
                windowHandle,
                (window, key, scancode, action, mods) -> {
                    boolean press = action == GLFW_PRESS;
                    switch (key) {
                        case GLFW_KEY_DELETE -> nk_input_key(ctx, NK_KEY_DEL, press);
                        case GLFW_KEY_ENTER -> nk_input_key(ctx, NK_KEY_ENTER, press);
                        case GLFW_KEY_TAB -> nk_input_key(ctx, NK_KEY_TAB, press);
                        case GLFW_KEY_BACKSPACE -> nk_input_key(ctx, NK_KEY_BACKSPACE, press);
                        case GLFW_KEY_UP -> nk_input_key(ctx, NK_KEY_UP, press);
                        case GLFW_KEY_DOWN -> nk_input_key(ctx, NK_KEY_DOWN, press);
                        case GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT ->
                                nk_input_key(ctx, NK_KEY_SHIFT, press);
                        case GLFW_KEY_LEFT -> nk_input_key(ctx, NK_KEY_LEFT, press);
                        case GLFW_KEY_RIGHT -> nk_input_key(ctx, NK_KEY_RIGHT, press);
                        default -> {}
                    }
                });
        glfwSetCursorPosCallback(
                windowHandle, (window, xpos, ypos) -> nk_input_motion(ctx, (int) xpos, (int) ypos));
        glfwSetMouseButtonCallback(
                windowHandle,
                (window, button, action, mods) -> {
                    try (MemoryStack stack = stackPush()) {
                        var cx = stack.mallocDouble(1);
                        var cy = stack.mallocDouble(1);
                        glfwGetCursorPos(window, cx, cy);

                        int nkButton =
                                switch (button) {
                                    case GLFW_MOUSE_BUTTON_RIGHT -> NK_BUTTON_RIGHT;
                                    case GLFW_MOUSE_BUTTON_MIDDLE -> NK_BUTTON_MIDDLE;
                                    default -> NK_BUTTON_LEFT;
                                };
                        nk_input_button(
                                ctx,
                                nkButton,
                                (int) cx.get(0),
                                (int) cy.get(0),
                                action == GLFW_PRESS);
                    }
                });
    }

    private void setupClipboard() {
        ctx.clip()
                .copy(
                        (handle, text, len) -> {
                            if (len == 0) return;
                            try (MemoryStack stack = stackPush()) {
                                ByteBuffer str = stack.malloc(len + 1);
                                memCopy(text, memAddress(str), len);
                                str.put(len, (byte) 0);
                                glfwSetClipboardString(windowHandle, str);
                            }
                        })
                .paste(
                        (handle, edit) -> {
                            long text = nglfwGetClipboardString(windowHandle);
                            if (text != NULL) {
                                nnk_textedit_paste(edit, text, nnk_strlen(text));
                            }
                        });
    }

    private void setupGl() {
        String vertexSrc =
                """
                #version 410 core
                uniform mat4 ProjMtx;
                in vec2 Position;
                in vec2 TexCoord;
                in vec4 Color;
                out vec2 Frag_UV;
                out vec4 Frag_Color;
                void main() {
                    Frag_UV = TexCoord;
                    Frag_Color = Color;
                    gl_Position = ProjMtx * vec4(Position.xy, 0, 1);
                }
                """;
        String fragmentSrc =
                """
                #version 410 core
                uniform sampler2D Texture;
                in vec2 Frag_UV;
                in vec4 Frag_Color;
                out vec4 Out_Color;
                void main() {
                    Out_Color = Frag_Color * texture(Texture, Frag_UV.st);
                }
                """;

        nk_buffer_init(cmds, allocator, BUFFER_INITIAL_SIZE);

        program = glCreateProgram();
        vertexShader = glCreateShader(GL_VERTEX_SHADER);
        fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(vertexShader, vertexSrc);
        glShaderSource(fragmentShader, fragmentSrc);
        glCompileShader(vertexShader);
        glCompileShader(fragmentShader);
        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) != GL_TRUE) {
            throw new IllegalStateException(
                    "Nuklear vertex shader compile failed: " + glGetShaderInfoLog(vertexShader));
        }
        if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) != GL_TRUE) {
            throw new IllegalStateException(
                    "Nuklear fragment shader compile failed: "
                            + glGetShaderInfoLog(fragmentShader));
        }
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            throw new IllegalStateException(
                    "Nuklear shader program link failed: " + glGetProgramInfoLog(program));
        }

        uniformTex = glGetUniformLocation(program, "Texture");
        uniformProj = glGetUniformLocation(program, "ProjMtx");
        int attribPos = glGetAttribLocation(program, "Position");
        int attribUv = glGetAttribLocation(program, "TexCoord");
        int attribCol = glGetAttribLocation(program, "Color");

        vbo = glGenBuffers();
        ebo = glGenBuffers();
        vao = glGenVertexArrays();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);

        glEnableVertexAttribArray(attribPos);
        glEnableVertexAttribArray(attribUv);
        glEnableVertexAttribArray(attribCol);

        glVertexAttribPointer(attribPos, 2, GL_FLOAT, false, 20, 0);
        glVertexAttribPointer(attribUv, 2, GL_FLOAT, false, 20, 8);
        glVertexAttribPointer(attribCol, 4, GL_UNSIGNED_BYTE, true, 20, 16);

        int nullTexId = glGenTextures();
        nullTexture.texture().id(nullTexId);
        nullTexture.uv().set(0.5f, 0.5f);
        glBindTexture(GL_TEXTURE_2D, nullTexId);
        try (MemoryStack stack = stackPush()) {
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA8,
                    1,
                    1,
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_INT_8_8_8_8_REV,
                    stack.ints(0xFFFFFFFF));
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void setupFont() {
        ttf = loadFont();

        fontTexId = glGenTextures();
        STBTTFontinfo fontInfo = STBTTFontinfo.create();

        STBTTPackedchar.Buffer cdata = STBTTPackedchar.create(224);

        float scale;
        float descent;

        try (MemoryStack stack = stackPush()) {
            stbtt_InitFont(fontInfo, ttf);
            scale = stbtt_ScaleForPixelHeight(fontInfo, FONT_HEIGHT);

            IntBuffer d = stack.mallocInt(1);
            stbtt_GetFontVMetrics(fontInfo, null, d, null);
            descent = d.get(0) * scale;

            ByteBuffer bitmap = memAlloc(BITMAP_SIZE * BITMAP_SIZE);

            STBTTPackContext pc = STBTTPackContext.malloc(stack);
            stbtt_PackBegin(pc, bitmap, BITMAP_SIZE, BITMAP_SIZE, 0, 1, NULL);
            stbtt_PackSetOversampling(pc, 4, 4);
            stbtt_PackFontRange(pc, ttf, 0, FONT_HEIGHT, 32, cdata);
            stbtt_PackEnd(pc);

            ByteBuffer texture = memAlloc(BITMAP_SIZE * BITMAP_SIZE * 4);
            for (int i = 0; i < bitmap.capacity(); i++) {
                texture.putInt((bitmap.get(i) << 24) | 0x00FFFFFF);
            }
            texture.flip();

            glBindTexture(GL_TEXTURE_2D, fontTexId);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA8,
                    BITMAP_SIZE,
                    BITMAP_SIZE,
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_INT_8_8_8_8_REV,
                    texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

            memFree(texture);
            memFree(bitmap);
        }

        defaultFont
                .width(
                        (handle, h, text, len) -> {
                            float textWidth = 0;
                            try (MemoryStack stack = stackPush()) {
                                IntBuffer unicode = stack.mallocInt(1);

                                int glyphLen = nnk_utf_decode(text, memAddress(unicode), len);
                                int textLen = glyphLen;
                                if (glyphLen == 0) return 0;

                                IntBuffer advance = stack.mallocInt(1);
                                while (textLen <= len && glyphLen != 0) {
                                    if (unicode.get(0) == NK_UTF_INVALID) break;

                                    stbtt_GetCodepointHMetrics(
                                            fontInfo, unicode.get(0), advance, null);
                                    textWidth += advance.get(0) * scale;

                                    glyphLen =
                                            nnk_utf_decode(
                                                    text + textLen,
                                                    memAddress(unicode),
                                                    len - textLen);
                                    textLen += glyphLen;
                                }
                            }
                            return textWidth;
                        })
                .height(FONT_HEIGHT)
                .query(
                        (handle, fontHeight, glyph, codepoint, nextCodepoint) -> {
                            try (MemoryStack stack = stackPush()) {
                                FloatBuffer x = stack.floats(0.0f);
                                FloatBuffer y = stack.floats(0.0f);

                                STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
                                IntBuffer advance = stack.mallocInt(1);

                                int safeCodepoint =
                                        (codepoint < 32 || codepoint > 255) ? '?' : codepoint;

                                stbtt_GetPackedQuad(
                                        cdata,
                                        BITMAP_SIZE,
                                        BITMAP_SIZE,
                                        safeCodepoint - 32,
                                        x,
                                        y,
                                        q,
                                        false);
                                stbtt_GetCodepointHMetrics(fontInfo, safeCodepoint, advance, null);

                                NkUserFontGlyph ufg = NkUserFontGlyph.create(glyph);
                                ufg.width(q.x1() - q.x0());
                                ufg.height(q.y1() - q.y0());
                                ufg.offset().set(q.x0(), q.y0() + (FONT_HEIGHT + descent));
                                ufg.xadvance(advance.get(0) * scale);
                                ufg.uv(0).set(q.s0(), q.t0());
                                ufg.uv(1).set(q.s1(), q.t1());
                            }
                        })
                .texture(it -> it.id(fontTexId));
    }

    private ByteBuffer loadFont() {
        try (InputStream is =
                TerrainEditorGui.class.getResourceAsStream("/font/FiraSans-Regular.ttf")) {
            if (is == null) {
                throw new IllegalStateException(
                        "Font resource not found: /font/FiraSans-Regular.ttf");
            }
            byte[] bytes = is.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load font", e);
        }
    }

    private void draw() {
        try (MemoryStack stack = stackPush()) {
            glEnable(GL_BLEND);
            glBlendEquation(GL_FUNC_ADD);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDisable(GL_CULL_FACE);
            glDisable(GL_DEPTH_TEST);
            glEnable(GL_SCISSOR_TEST);
            glActiveTexture(GL_TEXTURE0);

            glUseProgram(program);
            glUniform1i(uniformTex, 0);
            glUniformMatrix4fv(
                    uniformProj,
                    false,
                    stack.floats(
                            2.0f / width,
                            0.0f,
                            0.0f,
                            0.0f,
                            0.0f,
                            -2.0f / height,
                            0.0f,
                            0.0f,
                            0.0f,
                            0.0f,
                            -1.0f,
                            0.0f,
                            -1.0f,
                            1.0f,
                            0.0f,
                            1.0f));
            glViewport(0, 0, displayWidth, displayHeight);
        }

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);

        glBufferData(GL_ARRAY_BUFFER, MAX_VERTEX_BUFFER, GL_STREAM_DRAW);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, MAX_ELEMENT_BUFFER, GL_STREAM_DRAW);

        ByteBuffer vertices =
                java.util.Objects.requireNonNull(
                        glMapBuffer(GL_ARRAY_BUFFER, GL_WRITE_ONLY, MAX_VERTEX_BUFFER, null));
        ByteBuffer elements =
                java.util.Objects.requireNonNull(
                        glMapBuffer(
                                GL_ELEMENT_ARRAY_BUFFER, GL_WRITE_ONLY, MAX_ELEMENT_BUFFER, null));
        try (MemoryStack stack = stackPush()) {
            var config =
                    org.lwjgl.nuklear.NkConvertConfig.calloc(stack)
                            .vertex_layout(VERTEX_LAYOUT)
                            .vertex_size(20)
                            .vertex_alignment(4)
                            .tex_null(nullTexture)
                            .circle_segment_count(22)
                            .curve_segment_count(22)
                            .arc_segment_count(22)
                            .global_alpha(1.0f)
                            .shape_AA(NK_ANTI_ALIASING_ON)
                            .line_AA(NK_ANTI_ALIASING_ON);

            NkBuffer vbuf = NkBuffer.malloc(stack);
            NkBuffer ebuf = NkBuffer.malloc(stack);
            nk_buffer_init_fixed(vbuf, vertices);
            nk_buffer_init_fixed(ebuf, elements);
            nk_convert(ctx, cmds, vbuf, ebuf, config);
        }
        glUnmapBuffer(GL_ELEMENT_ARRAY_BUFFER);
        glUnmapBuffer(GL_ARRAY_BUFFER);

        float fbScaleX = (float) displayWidth / (float) width;
        float fbScaleY = (float) displayHeight / (float) height;

        long offset = NULL;
        for (NkDrawCommand cmd = nk__draw_begin(ctx, cmds);
                cmd != null;
                cmd = nk__draw_next(cmd, cmds, ctx)) {
            if (cmd.elem_count() == 0) continue;
            glBindTexture(GL_TEXTURE_2D, cmd.texture().id());
            glScissor(
                    (int) (cmd.clip_rect().x() * fbScaleX),
                    (int) ((height - (int) (cmd.clip_rect().y() + cmd.clip_rect().h())) * fbScaleY),
                    (int) (cmd.clip_rect().w() * fbScaleX),
                    (int) (cmd.clip_rect().h() * fbScaleY));
            glDrawElements(GL_TRIANGLES, cmd.elem_count(), GL_UNSIGNED_SHORT, offset);
            offset += (long) cmd.elem_count() * 2;
        }
        nk_clear(ctx);
        nk_buffer_clear(cmds);

        glUseProgram(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        glDisable(GL_SCISSOR_TEST);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public void shutdown() {
        ctx.clip().copy().free();
        ctx.clip().paste().free();
        nk_free(ctx);

        glDetachShader(program, vertexShader);
        glDetachShader(program, fragmentShader);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        glDeleteProgram(program);
        glDeleteTextures(fontTexId);
        glDeleteTextures(nullTexture.texture().id());
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
        nk_buffer_free(cmds);

        defaultFont.query().free();
        defaultFont.width().free();

        allocator.alloc().free();
        allocator.mfree().free();
    }
}
