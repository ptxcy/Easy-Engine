package dev.ptxy.engine.map;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.config.TerrainConfig;
import dev.ptxy.engine.config.VegetationConfig;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.shader.ShaderCompiler;
import dev.ptxy.engine.shader.ShaderUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

/*
 * Chunk-Konfiguration:
 *   CHUNK_SIZE       — Welteinheiten pro Chunk-Seite
 *   CHUNK_RESOLUTION — Quads pro Chunk-Seite; mehr = feineres Mesh, mehr Speicher
 *   RENDER_DISTANCE  — Radius in Chunks um den Spieler, der geladen bleibt
 *   HEIGHT_AMPLITUDE — Skaliert den Noise-Output [-1,1] auf Welteinheiten
 *   WORKER_THREADS   — Parallele Background-Threads für Vertex-Berechnung
 *
 * Rendering ist indiziert (glDrawElements): jeder Gitterpunkt existiert nur einmal
 * im Vertex-Buffer (VERTS_PER_SIDE^2 statt CHUNK_RESOLUTION^2*6 Vertices). Normalen
 * werden nicht mehr pro Vertex mitgeführt, sondern im Fragment-Shader per
 * Screen-Space-Derivative (dFdx/dFdy) berechnet — das erlaubt volle Vertex-Sharing
 * trotz Flat Shading. Die Dreiecks-Topologie ist für jeden Chunk identisch (nur die
 * Höhen unterscheiden sich), deshalb wird SHARED_INDICES einmal berechnet und ein
 * einziger Index-Buffer über alle Chunks hinweg wiederverwendet.
 */
public final class ChunkManager {
    private static final Logger log = LogManager.getLogger(ChunkManager.class);

    private static final TerrainConfig TERRAIN = Config.getTerrainConfig();

    private static final int CHUNK_SIZE = TERRAIN.chunkSize();
    private static final int CHUNK_RESOLUTION = TERRAIN.chunkResolution();
    private static final int RENDER_DISTANCE = TERRAIN.renderDistance();
    private static final int WORKER_THREADS = TERRAIN.workerThreads();
    // Deutlich kleiner als RENDER_DISTANCE -- einzelne Grashalme sind auf Terrain-Sichtweite
    // längst nicht mehr wahrnehmbar, kosten dort aber einen vollen Draw-Call pro Chunk (das war
    // mit RENDER_DISTANCE=64, bis zu 16641 Chunks, ein Haupttreiber eines massiven FPS-Einbruchs).
    // Wird pro Frame anhand der aktuellen Spielerposition geprüft (siehe renderVegetation()),
    // nicht beim Chunk-Bau -- sonst "friert" die Reichweite auf die Position beim ersten Laden
    // des Chunks ein.
    private static final int VEGETATION_RENDER_DISTANCE =
            Config.getVegetationConfig().renderDistance();

    private static final int VERTS_PER_SIDE = CHUNK_RESOLUTION + 1;
    // pos(3) + uv(2) + temp(1) + humidity(1) + cellA(1, flat, NUR für debugMode 3) +
    // weightTundra(1) + weightSavanna(1) + weightRainforest(1). cellA kommt aus
    // Map.getBiomeCell() (respektiert Pool-Clamp + Enabled-Filter), die drei Gewichte aus
    // Map.getBiomeWeights() -- dieselbe Zuordnung/dasselbe Übergangsband, die Map.getHeight()
    // für die Geometrie verwendet. Bewusst KEIN diskretes "cellB" mehr als zweites Attribut: eine
    // frühere Fassung übertrug cellA/cellB als flat + ein gemeinsames weightA normal interpoliert
    // -- das brach an jedem Dreieck, dessen Vertices sich uneinig waren, welche Zelle die nächste
    // ist (in der ganzen Übergangszone der Normalfall), weil OpenGL "flat" nur vom "provoking
    // vertex" übernimmt, das interpolierte weightA sich an den übrigen Vertices dann aber auf die
    // falsche Zellenpaarung bezog. Mit drei fest zugeordneten, überall stetigen Pro-Zellen-
    // Gewichten (keines davon flat) gibt es diese Mehrdeutigkeit nicht mehr.
    private static final int VERTEX_FLOATS = 11;
    // x, y, z, biomeOrdinal (Biome.ordinal(), siehe VegetationPlacer) -- eine Zeile pro Node.
    // Gemeinsames Layout für ALLE Vegetations-Typen (Gras, Baum, ...), da VegetationPlacer.place()
    // für jeden Typ dasselbe Format zurückgibt.
    private static final int VEGETATION_NODE_FLOATS = 4;
    // MUSS mit "const int BLADES_PER_NODE" in shader/vegetation/vertex.glsl übereinstimmen --
    // steuert sowohl den Attribut-Divisor (glVertexAttribDivisor) als auch den instanceCount
    // in upload(). Ersetzt die frühere Geometry-Shader-Vervielfachung durch echtes GPU-Instancing
    // eines einmal hochgeladenen Halm-Meshes -- auf macOS (OpenGL nur Kompatibilitäts-Layer über
    // Metal) ist Geometry-Shader-Amplifikation um Größenordnungen langsamer als Instancing.
    private static final int BLADES_PER_NODE = 3;
    private static final int BLADE_SEGMENTS = 3;
    private static final float[] BLADE_MESH = buildBladeMesh();
    private static final int BLADE_MESH_FLOATS =
            3; // x (Breite), y (Höhe, = fragHeightT), z (Neigung)
    private static final int BLADE_VERTEX_COUNT = BLADE_MESH.length / BLADE_MESH_FLOATS;
    private static final int[] SHARED_INDICES = buildSharedIndices();

    // Baum-Mesh: Stamm-Zylinder + Kronen-Kegel, ein Baum pro Node-Treffer (kein Büschel wie beim
    // Gras -- Divisor beim Instancing daher schlicht 1). TREE_SIDES ist die Eckenzahl von Stamm
    // und Krone (bewusst niedrig für den low-poly-Stil und geringe Vertex-Zahl pro Instanz).
    private static final int TREE_SIDES = 6;
    // Bruchteil der Gesamthöhe (0..1), den der Stamm einnimmt -- MUSS mit TRUNK_HEIGHT_T in
    // shader/tree/fragment.glsl übereinstimmen (markiert dort die Stamm/Krone-Farbgrenze). Höher
    // als vorher (0.4) auf Wunsch: längerer Stamm, Krone setzt später an -- bei gleichbleibender
    // Gesamthöhe (heightMin/Max in SceneConfig.json unverändert), nicht insgesamt größer.
    private static final float TREE_TRUNK_HEIGHT_T = 0.65f;
    // Stammradius relativ zum Kronenradius an der Kronenbasis (=1.0) -- deutlich schlanker als
    // die Krone, klassische Baum-Silhouette.
    private static final float TREE_TRUNK_RADIUS = 0.28f;
    private static final float[] TREE_MESH = buildTreeMesh();
    private static final int TREE_MESH_FLOATS =
            7; // x,y,z (Position) + nx,ny,nz (Normale) + heightT
    private static final int TREE_VERTEX_COUNT = TREE_MESH.length / TREE_MESH_FLOATS;

    private final Map noiseMap;
    private final ExecutorService workerPool;

    private final ConcurrentHashMap<ChunkPos, TerrainChunk> loadedChunks =
            new ConcurrentHashMap<>();
    private final Set<ChunkPos> pendingChunks = ConcurrentHashMap.newKeySet();
    private final UploadQueue uploadQueue = new UploadQueue();

    private int debugMode = 0;
    private boolean vegetationEnabled = true;
    private int sharedIndexBuffer = 0;
    private int sharedBladeBuffer = 0;
    private int sharedTreeBuffer = 0;

    // Wird bei jedem regenerate() erhöht und in jeden Build-Task hineinkopiert; Ergebnisse
    // aus einer älteren Epoche werden beim Draining verworfen, damit nach einer Parameter-
    // Änderung keine Chunks mit den alten Werten aufblitzen (siehe regenerate()).
    private int generation = 0;

    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f vpMatrix = new Matrix4f();

    private int playerChunkX = 0;
    private int playerChunkZ = 0;

    public ChunkManager(long seed) {
        noiseMap = new Map(seed);
        workerPool = Executors.newFixedThreadPool(WORKER_THREADS);
    }

    // Innerhalb dieses Radius (in Chunks) wird IMMER rein nach Distanz einsortiert, unabhängig
    // von der Blickrichtung -- garantiert, dass direkt um den Spieler herum nie eine Lücke
    // entsteht, selbst wenn er sich gerade schnell umdreht.
    private static final int CHUNK_PRIORITY_CORE_RADIUS = 3;
    // Wie stark die Blickrichtung außerhalb des Kernradius die Priorität gegenüber reiner Distanz
    // verschiebt -- 0 wäre wieder reine Distanz, 1 würde einen Chunk exakt hinter dem Spieler
    // doppelt so weit weg erscheinen lassen wie einen exakt vor ihm liegenden Chunk derselben
    // Entfernung.
    private static final float CHUNK_PRIORITY_DIRECTION_WEIGHT = 0.6f;

    public void update(float playerX, float playerZ, float lookDirX, float lookDirZ) {
        int cx = Math.floorDiv((int) playerX, CHUNK_SIZE);
        int cz = Math.floorDiv((int) playerZ, CHUNK_SIZE);
        playerChunkX = cx;
        playerChunkZ = cz;

        var iter = loadedChunks.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            ChunkPos pos = entry.getKey();
            if (Math.abs(pos.x() - cx) > RENDER_DISTANCE
                    || Math.abs(pos.z() - cz) > RENDER_DISTANCE) {
                entry.getValue().cleanup();
                iter.remove();
                pendingChunks.remove(pos);
            }
        }

        // XZ-Blickrichtung normalisieren (camera.getForward() ist bei Neigung nicht mehr
        // XZ-normiert) -- bei Blick exakt nach oben/unten (Länge ~0) bleibt die Richtung neutral,
        // Priorität fällt dann auf reine Distanz zurück.
        float lookLen = (float) Math.sqrt(lookDirX * lookDirX + lookDirZ * lookDirZ);
        float lookX = lookLen > 1e-6f ? lookDirX / lookLen : 0f;
        float lookZ = lookLen > 1e-6f ? lookDirZ / lookLen : 0f;

        // Innerhalb CHUNK_PRIORITY_CORE_RADIUS nach Distanz², außerhalb nach Distanz gewichtet mit
        // der Blickrichtung -- Chunks vor dem Spieler laden zuerst, Chunks hinter ihm zuletzt,
        // statt wie zuvor ausschließlich nach reiner Distanz zum Spieler (das füllte bei einem
        // frischen Start/Regenerate die Warteschlange in einem gleichmäßigen Ring um den Spieler,
        // unabhängig davon, wohin er gerade schaut oder sich bewegt -- bei RENDER_DISTANCE=64
        // sind das bis zu ~16.600 Positionen, ein Ring lädt dann spürbar langsamer voll als die
        // Richtung, in die tatsächlich geschaut wird).
        java.util.List<ChunkPos> missing = new java.util.ArrayList<>();
        for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
            for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
                ChunkPos pos = new ChunkPos(cx + dx, cz + dz);
                if (!loadedChunks.containsKey(pos) && !pendingChunks.contains(pos)) {
                    missing.add(pos);
                }
            }
        }
        missing.sort(
                java.util.Comparator.comparingDouble(
                        pos -> {
                            double dx = pos.x() - cx;
                            double dz = pos.z() - cz;
                            double dist = Math.sqrt(dx * dx + dz * dz);
                            if (dist <= CHUNK_PRIORITY_CORE_RADIUS) {
                                return dist;
                            }
                            double dot = dist > 0 ? (dx * lookX + dz * lookZ) / dist : 0;
                            // +CORE_RADIUS stellt sicher, dass JEDE Position außerhalb des
                            // Kernradius höher bewertet wird als JEDE Position darin, unabhängig
                            // von der Richtungsgewichtung oben.
                            return CHUNK_PRIORITY_CORE_RADIUS
                                    + dist * (1 - CHUNK_PRIORITY_DIRECTION_WEIGHT * dot);
                        }));
        for (ChunkPos pos : missing) {
            if (pendingChunks.add(pos)) {
                int taskGeneration = generation;
                workerPool.submit(
                        () -> {
                            try {
                                buildAndEnqueue(pos, taskGeneration);
                            } catch (RuntimeException e) {
                                // submit(Runnable) liefert ein Future, das hier nie abgefragt
                                // wird -- ohne diesen Catch verschwindet eine Exception spurlos
                                // und pos bleibt für immer unsichtbar in pendingChunks stecken.
                                // Kein erneutes Einreihen: der Fehler ist i.d.R. deterministisch
                                // (z.B. Config-Mismatch) und würde bei jedem update() erneut
                                // auftreten.
                                log.error("Failed to build chunk {}", pos, e);
                            }
                        });
            }
        }

        uploadQueue.drainTo(
                data -> {
                    if (data.generation() != generation) return; // aus alter Epoche, verwerfen
                    if (pendingChunks.contains(data.pos())) {
                        TerrainChunk old = loadedChunks.put(data.pos(), upload(data));
                        if (old != null) old.cleanup();
                    }
                    pendingChunks.remove(data.pos());
                });
    }

    // Verwirft alle geladenen/anstehenden Chunks und lässt sie beim nächsten update() mit den
    // aktuellen TerrainParams/BiomeLookUpTable-Werten neu bauen. Muss auf dem GL-Thread laufen
    // (löscht VAO/VBO). sharedIndexBuffer bleibt bestehen, da sich die Chunk-Topologie
    // (CHUNK_RESOLUTION) nicht ändert -- nur die Vertex-Höhen tun das.
    public void regenerate() {
        generation++;
        pendingChunks.clear();
        uploadQueue.clear();
        loadedChunks.values().forEach(TerrainChunk::cleanup);
        loadedChunks.clear();
    }

    public Map getNoiseMap() {
        return noiseMap;
    }

    public void renderVegetation(SimpleCamera3D camera, DirectionalLight light) {
        if (!vegetationEnabled) return;

        VegetationConfig.VegetationType grass = Config.getVegetationConfig().type("grass");
        renderVegetationLayer(
                "vegetation",
                camera,
                light,
                TerrainChunk::drawGrass,
                shader -> {
                    ShaderUtils.setUniformFloat(
                            shader, "widthMin", (float) grass.scale("widthMin"));
                    ShaderUtils.setUniformFloat(
                            shader, "widthMax", (float) grass.scale("widthMax"));
                    ShaderUtils.setUniformFloat(
                            shader, "heightMin", (float) grass.scale("heightMin"));
                    ShaderUtils.setUniformFloat(
                            shader, "heightMax", (float) grass.scale("heightMax"));
                });

        VegetationConfig.VegetationType tree = Config.getVegetationConfig().type("tree");
        renderVegetationLayer(
                "tree",
                camera,
                light,
                TerrainChunk::drawTree,
                shader -> {
                    ShaderUtils.setUniformFloat(
                            shader, "heightMin", (float) tree.scale("heightMin"));
                    ShaderUtils.setUniformFloat(
                            shader, "heightMax", (float) tree.scale("heightMax"));
                    ShaderUtils.setUniformFloat(
                            shader, "radiusMin", (float) tree.scale("radiusMin"));
                    ShaderUtils.setUniformFloat(
                            shader, "radiusMax", (float) tree.scale("radiusMax"));
                });
    }

    // Gemeinsame Render-Schleife für alle Vegetations-Typen -- unterscheiden sich nur im Shader,
    // dessen Größen-Uniforms und in der Draw-Methode des Chunks, Kandidatenauswahl/Frustum-Culling
    // ist identisch.
    private void renderVegetationLayer(
            String shaderName,
            SimpleCamera3D camera,
            DirectionalLight light,
            java.util.function.Consumer<TerrainChunk> draw,
            java.util.function.IntConsumer setScaleUniforms) {
        int shader = ShaderCompiler.getShader(shaderName);
        glUseProgram(shader);
        ShaderUtils.setUniformMat4(shader, "view", camera.getViewMatrix());
        ShaderUtils.setUniformMat4(shader, "projection", camera.getProjection());
        ShaderUtils.setUniformVec3(shader, "lightDir", light.getDirection());
        ShaderUtils.setUniformVec3(shader, "lightColor", light.getColor());
        ShaderUtils.setUniformFloat(shader, "ao", 1.0f);
        setScaleUniforms.accept(shader);

        // vpMatrix/frustum wurden bereits in renderAll() für dieselbe Kamera in diesem Frame
        // berechnet -- laut PbrTestLauncher.renderObjects() wird renderVegetation() immer direkt
        // danach mit derselben Kamera aufgerufen, eine erneute Berechnung wäre redundant.
        //
        // Statt die komplette loadedChunks-Map zu durchlaufen (bis zu (2*RENDER_DISTANCE+1)^2
        // Einträge) und die meisten per Abstandscheck zu verwerfen, werden nur die
        // (2*VEGETATION_RENDER_DISTANCE+1)^2 Kandidatenpositionen direkt nachgeschlagen --
        // dieselbe Größenordnung, die den ursprünglichen FPS-Einbruch beheben sollte (siehe
        // VEGETATION_RENDER_DISTANCE oben).
        float heightAmplitude = heightAmplitude();
        for (int dx = -VEGETATION_RENDER_DISTANCE; dx <= VEGETATION_RENDER_DISTANCE; dx++) {
            for (int dz = -VEGETATION_RENDER_DISTANCE; dz <= VEGETATION_RENDER_DISTANCE; dz++) {
                ChunkPos pos = new ChunkPos(playerChunkX + dx, playerChunkZ + dz);
                TerrainChunk chunk = loadedChunks.get(pos);
                if (chunk == null) continue;
                float wx = pos.x() * CHUNK_SIZE;
                float wz = pos.z() * CHUNK_SIZE;
                if (!frustum.testAab(
                        wx,
                        -heightAmplitude,
                        wz,
                        wx + CHUNK_SIZE,
                        heightAmplitude,
                        wz + CHUNK_SIZE)) {
                    continue;
                }
                draw.accept(chunk);
            }
        }

        glUseProgram(0);
    }

    public void renderAll(SimpleCamera3D camera, DirectionalLight light) {
        int shader = ShaderCompiler.getShader("base");
        glUseProgram(shader);
        ShaderUtils.setUniformMat4(shader, "view", camera.getViewMatrix());
        ShaderUtils.setUniformMat4(shader, "projection", camera.getProjection());
        ShaderUtils.setUniformVec3(shader, "camPos", camera.getPosition());
        ShaderUtils.setUniformVec3(shader, "lightDir", light.getDirection());
        ShaderUtils.setUniformVec3(shader, "lightColor", light.getColor());
        ShaderUtils.setUniformFloat(shader, "ao", 1.0f);
        ShaderUtils.setUniformInt(shader, "debugMode", debugMode);
        ShaderUtils.setUniformFloat(shader, "heightAmplitude", heightAmplitude());
        // Höhenfärbung normiert auf einen EINHEITLICHEN Weltmaßstab statt pro Biom relativ --
        // eine frühere Fassung nutzte je Biom dessen eigenes Maximum, wodurch niedrige Hügel
        // (Savanne/Regenwald, ~30-35m) an ihrem eigenen Gipfel bereits "Schnee" zeigten und die
        // Färbung an Zellgrenzen hart sprang, sobald sich das Referenzmaximum mit der Zelle
        // änderte (siehe shader/base/fragment.glsl).
        ShaderUtils.setUniformFloat(
                shader, "worldMaxHeight", Config.getTerrainConfig().worldMaxHeightMeters());
        ShaderUtils.setUniformMat4(shader, "model", new Matrix4f());

        camera.getProjection().mul(camera.getViewMatrix(), vpMatrix);
        frustum.set(vpMatrix);

        float heightAmplitude = heightAmplitude();
        for (var entry : loadedChunks.entrySet()) {
            ChunkPos pos = entry.getKey();
            float wx = pos.x() * CHUNK_SIZE;
            float wz = pos.z() * CHUNK_SIZE;
            if (!frustum.testAab(
                    wx, -heightAmplitude, wz, wx + CHUNK_SIZE, heightAmplitude, wz + CHUNK_SIZE)) {
                continue;
            }
            entry.getValue().draw();
        }

        glUseProgram(0);
    }

    public void setVegetationEnabled(boolean enabled) {
        vegetationEnabled = enabled;
    }

    public boolean isVegetationEnabled() {
        return vegetationEnabled;
    }

    public void setDebugMode(int mode) {
        debugMode = mode;
    }

    public void shutdown() {
        workerPool.shutdownNow();
        loadedChunks.values().forEach(TerrainChunk::cleanup);
        loadedChunks.clear();
        if (sharedIndexBuffer != 0) {
            glDeleteBuffers(sharedIndexBuffer);
            sharedIndexBuffer = 0;
        }
        if (sharedBladeBuffer != 0) {
            glDeleteBuffers(sharedBladeBuffer);
            sharedBladeBuffer = 0;
        }
        if (sharedTreeBuffer != 0) {
            glDeleteBuffers(sharedTreeBuffer);
            sharedTreeBuffer = 0;
        }
    }

    private void buildAndEnqueue(ChunkPos pos, int taskGeneration) {
        float[] grassNodes =
                VegetationPlacer.place(pos, noiseMap, CHUNK_SIZE, heightAmplitude(), "grass");
        float[] treeNodes =
                VegetationPlacer.place(pos, noiseMap, CHUNK_SIZE, heightAmplitude(), "tree");
        uploadQueue.enqueue(
                new ChunkMeshData(pos, buildVertices(pos), grassNodes, treeNodes, taskGeneration));
    }

    private float[] buildVertices(ChunkPos pos) {
        float step = (float) CHUNK_SIZE / CHUNK_RESOLUTION;
        float worldX = pos.x() * CHUNK_SIZE;
        float worldZ = pos.z() * CHUNK_SIZE;

        float[] buf = new float[VERTS_PER_SIDE * VERTS_PER_SIDE * VERTEX_FLOATS];
        int i = 0;

        for (int z = 0; z < VERTS_PER_SIDE; z++) {
            for (int x = 0; x < VERTS_PER_SIDE; x++) {
                float wx = worldX + x * step;
                float wz = worldZ + z * step;
                float h = height(wx, wz);
                float t = temp(wx, wz, h);
                float hu = humidity(wx, wz);
                float u = (float) x / CHUNK_RESOLUTION;
                float v = (float) z / CHUNK_RESOLUTION;
                int[] cell = noiseMap.getBiomeCell(wx, wz);
                int cellA = cell[0] * 3 + cell[1];
                Map.BiomeWeights weights = noiseMap.getBiomeWeights(wx, wz);
                i =
                        vert(
                                buf,
                                i,
                                wx,
                                h,
                                wz,
                                u,
                                v,
                                t,
                                hu,
                                cellA,
                                (float) weights.tundra(),
                                (float) weights.savanna(),
                                (float) weights.rainforest());
            }
        }
        return buf;
    }

    // Ein statisches Halm-Mesh in lokalem Raum, einmalig gebaut und geteilt (siehe
    // ensureSharedBladeBuffer()) -- x = Breitenrichtung (-1..1, Verjüngung zur Spitze bereits
    // eingerechnet), y = Höhen-Parameter 0..1, z = Neigungs-Kurve t^2 (0..1). Die eigentliche
    // Zufallsvariation (Rotation, Skalierung von Breite/Höhe/Neigung, Streuung im Büschel)
    // passiert pro Instanz im Vertex-Shader, siehe shader/vegetation/vertex.glsl.
    private static float[] buildBladeMesh() {
        float[] verts = new float[(BLADE_SEGMENTS + 1) * 2 * BLADE_MESH_FLOATS];
        int i = 0;
        for (int seg = 0; seg <= BLADE_SEGMENTS; seg++) {
            float t = (float) seg / BLADE_SEGMENTS;
            float taper = 1.0f - t * 0.85f; // Spitze schmaler als die Basis
            float leanCurve = t * t;
            verts[i++] = -taper;
            verts[i++] = t;
            verts[i++] = leanCurve;
            verts[i++] = taper;
            verts[i++] = t;
            verts[i++] = leanCurve;
        }
        return verts;
    }

    // Stamm-Zylinder (0..TREE_TRUNK_HEIGHT_T) + Kronen-Kegel (TREE_TRUNK_HEIGHT_T..1) +
    // Schulter-Ring dazwischen (schließt die Lücke zwischen Stammradius und breiterem
    // Kronenradius). Nicht indiziert und mit dupliziertem Pro-Face-Normal für Flat Shading,
    // analog zum Halm-Mesh. Normalen sind rein geometrisch aus Zylinder/Kegel-Form hergeleitet
    // und daher unabhängig von der Dreiecks-Wickelrichtung korrekt -- Face Culling wird beim
    // Baum-Draw deshalb ohnehin deaktiviert (siehe TerrainChunk.drawTree()), falls die
    // Wickelrichtung einzelner Flächen nicht überall nach außen zeigt (visuell noch nicht
    // verifiziert, kein Display-Zugriff in dieser Session).
    private static float[] buildTreeMesh() {
        java.util.List<Float> verts = new java.util.ArrayList<>();
        float trunkY = TREE_TRUNK_HEIGHT_T;
        float coneHeight = 1.0f - trunkY;

        for (int side = 0; side < TREE_SIDES; side++) {
            float a0 = (float) (side * 2.0 * Math.PI / TREE_SIDES);
            float a1 = (float) ((side + 1) * 2.0 * Math.PI / TREE_SIDES);
            float x0 = (float) Math.cos(a0);
            float z0 = (float) Math.sin(a0);
            float x1 = (float) Math.cos(a1);
            float z1 = (float) Math.sin(a1);

            // Stamm-Seitenfläche: vertikales Quad aus zwei Dreiecken, Normale = gemittelte
            // Radialrichtung der Kante (Stamm ist ein gerader Zylinder, kein Konus).
            float trunkNx = (x0 + x1) * 0.5f;
            float trunkNz = (z0 + z1) * 0.5f;
            float trunkNlen = (float) Math.sqrt(trunkNx * trunkNx + trunkNz * trunkNz);
            trunkNx /= trunkNlen;
            trunkNz /= trunkNlen;
            float tx0 = x0 * TREE_TRUNK_RADIUS;
            float tz0 = z0 * TREE_TRUNK_RADIUS;
            float tx1 = x1 * TREE_TRUNK_RADIUS;
            float tz1 = z1 * TREE_TRUNK_RADIUS;
            addVertex(verts, tx0, 0f, tz0, trunkNx, 0f, trunkNz, 0f);
            addVertex(verts, tx1, 0f, tz1, trunkNx, 0f, trunkNz, 0f);
            addVertex(verts, tx0, trunkY, tz0, trunkNx, 0f, trunkNz, trunkY);
            addVertex(verts, tx0, trunkY, tz0, trunkNx, 0f, trunkNz, trunkY);
            addVertex(verts, tx1, 0f, tz1, trunkNx, 0f, trunkNz, 0f);
            addVertex(verts, tx1, trunkY, tz1, trunkNx, 0f, trunkNz, trunkY);

            // Schulter-Ring: verbindet Stammradius mit dem breiteren Kronenradius (=1.0) auf
            // Höhe trunkY, nach oben ausgerichtet.
            addVertex(verts, tx0, trunkY, tz0, 0f, 1f, 0f, trunkY);
            addVertex(verts, tx1, trunkY, tz1, 0f, 1f, 0f, trunkY);
            addVertex(verts, x0, trunkY, z0, 0f, 1f, 0f, trunkY);
            addVertex(verts, x0, trunkY, z0, 0f, 1f, 0f, trunkY);
            addVertex(verts, tx1, trunkY, tz1, 0f, 1f, 0f, trunkY);
            addVertex(verts, x1, trunkY, z1, 0f, 1f, 0f, trunkY);

            // Kronen-Kegelmantel: Basisradius 1.0 auf Höhe trunkY, Spitze bei (0,1,0). Normale
            // = klassische Kegel-Seitennormale (radial nach außen + Steigungsanteil nach oben,
            // aus Basisradius=1 und Höhe coneHeight hergeleitet).
            float coneNy = 1.0f / coneHeight;
            float coneNx = (x0 + x1) * 0.5f;
            float coneNz = (z0 + z1) * 0.5f;
            float coneNlen = (float) Math.sqrt(coneNx * coneNx + coneNz * coneNz + coneNy * coneNy);
            coneNx /= coneNlen;
            coneNz /= coneNlen;
            coneNy /= coneNlen;
            addVertex(verts, x0, trunkY, z0, coneNx, coneNy, coneNz, trunkY);
            addVertex(verts, x1, trunkY, z1, coneNx, coneNy, coneNz, trunkY);
            addVertex(verts, 0f, 1f, 0f, coneNx, coneNy, coneNz, 1f);
        }

        float[] arr = new float[verts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = verts.get(i);
        return arr;
    }

    private static void addVertex(
            java.util.List<Float> out,
            float x,
            float y,
            float z,
            float nx,
            float ny,
            float nz,
            float t) {
        out.add(x);
        out.add(y);
        out.add(z);
        out.add(nx);
        out.add(ny);
        out.add(nz);
        out.add(t);
    }

    private static int[] buildSharedIndices() {
        int[] indices = new int[CHUNK_RESOLUTION * CHUNK_RESOLUTION * 6];
        int i = 0;
        for (int z = 0; z < CHUNK_RESOLUTION; z++) {
            for (int x = 0; x < CHUNK_RESOLUTION; x++) {
                int i00 = z * VERTS_PER_SIDE + x;
                int i10 = z * VERTS_PER_SIDE + x + 1;
                int i01 = (z + 1) * VERTS_PER_SIDE + x;
                int i11 = (z + 1) * VERTS_PER_SIDE + x + 1;

                indices[i++] = i00;
                indices[i++] = i11;
                indices[i++] = i10;

                indices[i++] = i00;
                indices[i++] = i01;
                indices[i++] = i11;
            }
        }
        return indices;
    }

    private float height(float wx, float wz) {
        return (float) noiseMap.getHeight(wx, wz) * heightAmplitude();
    }

    private float heightAmplitude() {
        return Config.getTerrainParams().heightAmplitude();
    }

    private float temp(float wx, float wz, float height) {
        return (float) noiseMap.getTemperature(wx, wz, height);
    }

    private float humidity(float wx, float wz) {
        return (float) noiseMap.getHumidity(wx, wz);
    }

    private int vert(
            float[] buf,
            int i,
            float x,
            float y,
            float z,
            float u,
            float v,
            float t,
            float h,
            int cellA,
            float weightTundra,
            float weightSavanna,
            float weightRainforest) {
        buf[i++] = x;
        buf[i++] = y;
        buf[i++] = z;
        buf[i++] = u;
        buf[i++] = v;
        buf[i++] = t;
        buf[i++] = h;
        buf[i++] = cellA;
        buf[i++] = weightTundra;
        buf[i++] = weightSavanna;
        buf[i++] = weightRainforest;
        return i;
    }

    private void ensureSharedIndexBuffer() {
        if (sharedIndexBuffer != 0) return;
        sharedIndexBuffer = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sharedIndexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, SHARED_INDICES, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void ensureSharedBladeBuffer() {
        if (sharedBladeBuffer != 0) return;
        sharedBladeBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, sharedBladeBuffer);
        glBufferData(GL_ARRAY_BUFFER, BLADE_MESH, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void ensureSharedTreeBuffer() {
        if (sharedTreeBuffer != 0) return;
        sharedTreeBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, sharedTreeBuffer);
        glBufferData(GL_ARRAY_BUFFER, TREE_MESH, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private TerrainChunk upload(ChunkMeshData data) {
        ensureSharedIndexBuffer();

        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data.vertices(), GL_STATIC_DRAW);
        int stride = VERTEX_FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 8L * Float.BYTES);
        glEnableVertexAttribArray(5);
        glVertexAttribPointer(6, 1, GL_FLOAT, false, stride, 9L * Float.BYTES);
        glEnableVertexAttribArray(6);
        glVertexAttribPointer(7, 1, GL_FLOAT, false, stride, 10L * Float.BYTES);
        glEnableVertexAttribArray(7);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sharedIndexBuffer);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        GrassUpload grass = uploadGrass(data);
        TreeUpload tree = uploadTree(data);
        return new TerrainChunk(
                vao,
                vbo,
                SHARED_INDICES.length,
                grass.vao(),
                grass.vbo(),
                grass.instanceCount(),
                tree.vao(),
                tree.vbo(),
                tree.instanceCount());
    }

    // Legt VAO/VBO nur an, wenn dieser Chunk tatsächlich Grasnodes hat -- sonst würde jeder der
    // bis zu (2*RENDER_DISTANCE+1)^2 geladenen Chunks ein zweites VAO/VBO-Paar bekommen, auch
    // außerhalb der Vegetations-Reichweite oder in Biom-Zellen mit coveragePercent=0.
    private GrassUpload uploadGrass(ChunkMeshData data) {
        if (data.grassNodes().length == 0) {
            return new GrassUpload(0, 0, 0);
        }

        ensureSharedBladeBuffer();

        int grassVao = glGenVertexArrays();
        int grassVbo = glGenBuffers();
        glBindVertexArray(grassVao);

        // Attribut 0: das geteilte statische Halm-Mesh -- Divisor 0 (Standard), liest also normal
        // einen Vertex nach dem anderen statt einmal pro Instanz.
        glBindBuffer(GL_ARRAY_BUFFER, sharedBladeBuffer);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, BLADE_MESH_FLOATS * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        // Attribut 1+2: pro-Chunk Node-Daten (Weltposition + Biom). Divisor=BLADES_PER_NODE --
        // dieselbe Node-Zeile bedient BLADES_PER_NODE aufeinanderfolgende Instanzen (siehe
        // gl_InstanceID % BLADES_PER_NODE in shader/vegetation/vertex.glsl für die
        // Halm-innerhalb-des-Büschels-Variation).
        glBindBuffer(GL_ARRAY_BUFFER, grassVbo);
        glBufferData(GL_ARRAY_BUFFER, data.grassNodes(), GL_STATIC_DRAW);
        int grassStride = VEGETATION_NODE_FLOATS * Float.BYTES;
        glVertexAttribPointer(1, 3, GL_FLOAT, false, grassStride, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribDivisor(1, BLADES_PER_NODE);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, grassStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribDivisor(2, BLADES_PER_NODE);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        int nodeCount = data.grassNodes().length / VEGETATION_NODE_FLOATS;
        return new GrassUpload(grassVao, grassVbo, nodeCount * BLADES_PER_NODE);
    }

    // Legt VAO/VBO nur an, wenn dieser Chunk tatsächlich Baum-Nodes hat (analog zu uploadGrass()).
    private TreeUpload uploadTree(ChunkMeshData data) {
        if (data.treeNodes().length == 0) {
            return new TreeUpload(0, 0, 0);
        }

        ensureSharedTreeBuffer();

        int treeVao = glGenVertexArrays();
        int treeVbo = glGenBuffers();
        glBindVertexArray(treeVao);

        // Attribut 0-2: das geteilte statische Baum-Mesh (Position, Normale, Höhen-Parameter) --
        // Divisor 0, liest also normal einen Vertex nach dem anderen statt einmal pro Instanz.
        glBindBuffer(GL_ARRAY_BUFFER, sharedTreeBuffer);
        int treeMeshStride = TREE_MESH_FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, treeMeshStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, treeMeshStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, treeMeshStride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);

        // Attribut 3+4: pro-Chunk Node-Daten (Weltposition + Biom). Divisor=1 -- anders als beim
        // Gras (Büschel aus BLADES_PER_NODE Halmen) steht hier genau ein Baum pro Node-Treffer.
        glBindBuffer(GL_ARRAY_BUFFER, treeVbo);
        glBufferData(GL_ARRAY_BUFFER, data.treeNodes(), GL_STATIC_DRAW);
        int treeNodeStride = VEGETATION_NODE_FLOATS * Float.BYTES;
        glVertexAttribPointer(3, 3, GL_FLOAT, false, treeNodeStride, 0L);
        glEnableVertexAttribArray(3);
        glVertexAttribDivisor(3, 1);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, treeNodeStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribDivisor(4, 1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        int nodeCount = data.treeNodes().length / VEGETATION_NODE_FLOATS;
        return new TreeUpload(treeVao, treeVbo, nodeCount);
    }

    private record GrassUpload(int vao, int vbo, int instanceCount) {}

    private record TreeUpload(int vao, int vbo, int instanceCount) {}

    private record TerrainChunk(
            int vaoId,
            int vboId,
            int indexCount,
            int grassVaoId,
            int grassVboId,
            int grassInstanceCount,
            int treeVaoId,
            int treeVboId,
            int treeInstanceCount) {
        void draw() {
            glBindVertexArray(vaoId);
            glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
            glBindVertexArray(0);
        }

        // Nodes ohne Treffer (siehe VegetationPlacer) liefern ein leeres Array -- glDrawArrays
        // mit count=0 wäre harmlos, aber so wird gar nicht erst ein leeres VAO gebunden.
        void drawGrass() {
            if (grassInstanceCount == 0) return;
            glBindVertexArray(grassVaoId);
            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, BLADE_VERTEX_COUNT, grassInstanceCount);
            glBindVertexArray(0);
        }

        // Face Culling wird hier bewusst kurz deaktiviert: anders als beim Grashalm (eine
        // einzelne flache Ebene, siehe drawGrass()) ist der Baum ein echter 3D-Körper aus
        // mehreren Flächen, dessen Wickelrichtung pro Fläche visuell noch nicht verifiziert ist
        // (kein Display-Zugriff in dieser Session) -- ohne das Deaktivieren würden falsch
        // gewickelte Flächen unsichtbar bleiben statt nur kosmetisch falsch beleuchtet zu sein.
        void drawTree() {
            if (treeInstanceCount == 0) return;
            glDisable(GL_CULL_FACE);
            glBindVertexArray(treeVaoId);
            glDrawArraysInstanced(GL_TRIANGLES, 0, TREE_VERTEX_COUNT, treeInstanceCount);
            glBindVertexArray(0);
            glEnable(GL_CULL_FACE);
        }

        void cleanup() {
            glDeleteVertexArrays(vaoId);
            glDeleteBuffers(vboId);
            if (grassVaoId != 0) glDeleteVertexArrays(grassVaoId);
            if (grassVboId != 0) glDeleteBuffers(grassVboId);
            if (treeVaoId != 0) glDeleteVertexArrays(treeVaoId);
            if (treeVboId != 0) glDeleteBuffers(treeVboId);
        }
    }
}
