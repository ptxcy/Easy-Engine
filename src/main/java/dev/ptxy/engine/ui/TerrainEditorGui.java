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
import dev.ptxy.engine.map.ChunkManager;
import dev.ptxy.engine.world.Player;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
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

/*
 * In-App-Editor fürs Terrain-Tuning (Roadmap-Schritt 3): Panel mit den 4 fBm-Parametern der
 * gerade ausgewählten Biom-Zelle (Amplitude/Frequenz/Persistence/Lacunarity), globalen Werten
 * und einem Regenerieren-Button -- kein App-Neustart mehr nötig, um Terrain-Parameter zu testen.
 *
 * Nuklear-GLFW/GL3-Backend portiert von der offiziellen LWJGL-Demo
 * (org.lwjgl.demo.nuklear.GLFWDemo). Font ist Fira Sans (SIL Open Font License, siehe
 * resources/font/FiraSans-OFL.txt).
 *
 * Aufrufer (PbrTestLauncher) muss:
 *   - beginInput()/endInput() exakt um Cores glfwPollEvents() legen (SceneRenderer-Hooks)
 *   - render() nach dem 3D-Scene-Rendering, vor glfwSwapBuffers aufrufen
 *   - shutdown() beim Beenden aufrufen
 * WICHTIG: render() verändert globalen GL-State (Blend/Cull/Depth/Scissor) und stellt am Ende
 * exakt den Zustand wieder her, den Core.java beim Start setzt -- sonst ist die 3D-Szene ab dem
 * nächsten Frame falsch gerendert (kein Depth-Test/Culling mehr).
 */
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

    private boolean open = false;
    private boolean infoOpen = true;
    private int fps = 0;
    private String saveStatus = "";

    // Was die Welt gerade zeigt (Pool): 0-8 = genau dieses Biom (row*3+col, siehe BIOME_NAMES),
    // ALL_BIOMES_INDEX = die komplette, unbeschränkte Verblendung aller 9 Biome (Normalzustand
    // der Karte). Getrennt von editBiome, damit man die volle Karte ansehen und trotzdem gezielt
    // ein einzelnes Biom bearbeiten kann.
    private int selectedBiome = ALL_BIOMES_INDEX;

    // Welches Biom die Form-Regler darunter gerade bearbeiten -- bleibt IMMER ein echtes Biom
    // (nie "Alle"), damit Bearbeiten nie ins Leere geht. Wird automatisch auf selectedBiome
    // synchronisiert, sobald ein echtes Biom im Pool gewählt wird; bleibt unverändert, wenn der
    // Pool auf "Alle" steht (siehe Bug vom vorigen Umbau: getrennte Pool/Editier-Auswahl konnte
    // dazu führen, dass Bearbeiten wirkungslos blieb, weil die Welt ein anderes Biom zeigte --
    // dieser Fall kann jetzt nicht mehr passieren, weil editBiome nur beim expliziten Wählen
    // eines echten Bioms geändert wird, nicht beim Wählen von "Alle").
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

    // Muss als Feld gehalten werden (nicht lokal in setupFont()): STBTTFontinfo merkt sich nur
    // einen rohen Zeiger in diesen Speicher fuer spaetere Glyph-Lookups (stbtt_GetCodepointHMetrics
    // in der Query-Callback, die erst beim tatsaechlichen Rendern spaeter aufgerufen wird) -- als
    // lokale Variable waere der direkte ByteBuffer nach setupFont() fuer den GC freigegeben.
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

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isInfoOpen() {
        return infoOpen;
    }

    public void setInfoOpen(boolean infoOpen) {
        this.infoOpen = infoOpen;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    // Muss vor Cores glfwPollEvents() aufgerufen werden (SceneRenderer.beforePollEvents).
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

    // Muss nach Cores glfwPollEvents() aufgerufen werden (SceneRenderer.afterPollEvents).
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
                    // Volle, unbeschränkte Verblendung -- editBiome bewusst NICHT anfassen, damit
                    // die Form-Regler weiter am zuletzt gewählten echten Biom hängen.
                    params.setMinRow(0);
                    params.setMaxRow(2);
                    params.setMinCol(0);
                    params.setMaxCol(2);
                } else {
                    int poolRow = selectedBiome / 3;
                    int poolCol = selectedBiome % 3;
                    params.setMinRow(poolRow);
                    params.setMaxRow(poolRow);
                    params.setMinCol(poolCol);
                    params.setMaxCol(poolCol);
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
                                table.amplitude()[row][col]);
                table.frequency()[row][col] =
                        frequencyField.draw(
                                "Frequenz:",
                                "Wert 0.1 bis 5",
                                "Wie eng Hügel und Täler beieinander liegen -- hoch: viele kleine"
                                        + " Hügel dicht gedrängt. Niedrig: wenige breite, weit"
                                        + " auseinanderliegende Berge. Ändert nur den Maßstab,"
                                        + " nicht die Form.",
                                table.frequency()[row][col]);
                table.persistence()[row][col] =
                        persistenceField.draw(
                                "Persistence:",
                                "Wert 0.05 bis 0.95",
                                "Wie glatt oder zerklüftet die Oberfläche wirkt -- niedrig: sanfte,"
                                        + " plateauartige Hügel wie Dünen. Hoch: raue, zerklüftete"
                                        + " Felsen mit vielen kleinen Unebenheiten.",
                                table.persistence()[row][col]);
                table.lacunarity()[row][col] =
                        lacunarityField.draw(
                                "Lacunarity:",
                                "Wert 1 bis 4",
                                "Wie stark sich grobe und feine Unebenheiten unterscheiden --"
                                        + " niedrig: wirkt flach, kaum Feindetail. Um 2: natürlich"
                                        + " wirkendes Terrain. Hoch: extrem zerrissen, viele"
                                        + " Detailebenen übereinander.",
                                table.lacunarity()[row][col]);
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
                                table.redistribution()[row][col]);
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
                                table.valleyRedistribution()[row][col]);

                sectionDivider();
                nk_layout_row_dynamic(ctx, 18, 1);
                nk_label(ctx, "ALLE BIOME - Globale Regler", NK_TEXT_LEFT);
                params.setOctaves(
                        (int)
                                Math.round(
                                        octavesField.draw(
                                                "Octaves:",
                                                "Wert 1 bis 8 (ganzzahlig)",
                                                "Wie viele Detailebenen übereinandergelegt werden"
                                                    + " -- wenige: weiche, runde Hügel ohne"
                                                    + " Kleinstdetails. Viele: scharfe Felskanten"
                                                    + " und Erosionsrillen.",
                                                params.octaves())));
                params.setHeightScale(
                        heightScaleField.draw(
                                "Height Scale:",
                                "Wert 0.001 bis 0.2",
                                "Wie weit Berge und Täler insgesamt auseinanderliegen -- klein:"
                                        + " riesige, weitläufige Gebirgszüge. Groß: enge, schnell"
                                        + " wechselnde Hügellandschaft.",
                                params.heightScale()));
                params.setHeightAmplitude(
                        (float)
                                heightAmplitudeField.draw(
                                        "Height Amp.:",
                                        "Wert 1 bis 300",
                                        "Wie hoch die höchsten Berge insgesamt werden, in"
                                                + " Welteinheiten/Metern.",
                                        params.heightAmplitude()));
                params.setTempScale(
                        tempScaleField.draw(
                                "Temp Scale:",
                                "Wert 0.00001 bis 0.01",
                                "Wie großflächig die Temperaturzonen sind -- klein halten, sonst"
                                        + " wirken Biome fleckig statt als große, zusammenhängende"
                                        + " Zonen.",
                                params.tempScale()));
                params.setHumidityScale(
                        humidityScaleField.draw(
                                "Humid. Scale:",
                                "Wert 0.00001 bis 0.01",
                                "Wie großflächig die Feuchtezonen sind -- klein halten, sonst"
                                        + " wirken Biome fleckig statt als große, zusammenhängende"
                                        + " Zonen.",
                                params.humidityScale()));
                params.setHeightTempLapse(
                        heightTempLapseField.draw(
                                "Temp Lapse:",
                                "Wert 0 bis 0.05",
                                "Wie stark es mit der Höhe kälter wird -- höher bedeutet:"
                                        + " Bergspitzen kippen schneller in die kalte Ecke der"
                                        + " Biom-Tabelle (Richtung Zeile 0).",
                                params.heightTempLapse()));

                nk_layout_row_dynamic(ctx, 30, 1);
                if (nk_button_label(ctx, "REGENERATE")) {
                    chunkManager.regenerate();
                }

                nk_layout_row_dynamic(ctx, 30, 1);
                if (nk_button_label(ctx, "Speichern (überschreibt SceneConfig.json)")) {
                    try {
                        Config.saveTerrainParams();
                        saveStatus = "Gespeichert -- SceneConfig.json aktualisiert.";
                    } catch (RuntimeException e) {
                        saveStatus = "Fehler beim Speichern: " + e.getMessage();
                    }
                }
                if (!saveStatus.isEmpty()) {
                    // Feste Zeilenhöhe reicht für 1 Zeile -- eine umgebrochene Fehlermeldung mit
                    // langem Pfad würde sonst nach der ersten Zeile abgeschnitten aussehen, ohne
                    // dass das nach einem Fehler beim Speichern selbst aussieht. 90px = ~4 Zeilen.
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

    // Sichtbare Trennung zwischen Abschnitten (Pool / Editier-Zelle / pro-Biom-Regler / globale
    // Regler) -- eine echte Linie statt nur eines weiteren Labels, damit "nur dieses Biom" und
    // "alle Biome" nicht optisch ineinanderlaufen.
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

    // Anklickbares Zahlen-Eingabefeld statt Drag-Slider: Buffer/Länge müssen zwischen Frames
    // erhalten bleiben, solange der Nutzer tippt (Nuklear ist immediate-mode, das Widget selbst
    // hat kein Gedächtnis). Der angezeigte Text wird nur dann aus dem echten Wert neu befüllt,
    // wenn das Feld gerade NICHT aktiv ist (sonst würde jeder Frame die Eingabe überschreiben)
    // und sich der Wert seit dem letzten Abgleich geändert hat (z.B. nach Zellwechsel).
    private final class NumberField {
        private final ByteBuffer buffer = BufferUtils.createByteBuffer(32);
        private final int[] length = {0};
        private double committed = Double.NaN;
        private boolean activeLastFrame = false;

        double draw(String label, String rangeHint, String description, double currentValue) {
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
                committed = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                // Zwischenzustand beim Tippen (z.B. "-", "0.", leer) -- letzten gültigen Wert
                // behalten
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
                        default -> {
                            // Andere Tasten (WASD, Q/E, Pfeiltasten fuer Speed etc.) werden von
                            // PbrTestLauncher per Polling gelesen -- hier nichts zu tun.
                        }
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
        // 224 Zeichen ab Codepoint 32 deckt ASCII + Latin-1 Supplement ab (bis 255) -- sonst
        // stürzt stbtt_GetPackedQuad bei deutschen Umlauten/ß (ä/ö/ü/Ä/Ö/Ü/ß, Codepoints > 126)
        // mit einem Out-of-Bounds-Fehler ab, weil "codepoint - 32" außerhalb des Buffers landet.
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

            // R8 -> RGBA8 (Alpha aus dem Bitmap, RGB weiss -- Text wird per Frag_Color eingefaerbt)
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

                                // Gebackener Zeichensatz deckt nur Codepoints 32-255 ab (ASCII +
                                // Latin-1). Alles außerhalb (z.B. Gedankenstrich "—", U+2014)
                                // würde stbtt_GetPackedQuad mit Out-of-Bounds abstürzen lassen --
                                // stattdessen auf '?' ausweichen statt die App zu crashen.
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

        // Nuklear hinterlaesst Blend/Cull/Depth/Scissor in einem UI-freundlichen Zustand --
        // exakt auf das zurücksetzen, was Core.java beim Start konfiguriert, sonst rendert die
        // 3D-Szene ab dem naechsten Frame ohne Depth-Test/Culling.
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
