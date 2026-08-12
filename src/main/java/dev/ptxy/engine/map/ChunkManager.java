package dev.ptxy.engine.map;

import static org.lwjgl.opengl.GL30.*;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.config.TerrainConfig;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.shader.ShaderCompiler;
import dev.ptxy.engine.shader.ShaderUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final TerrainConfig TERRAIN = Config.getTerrainConfig();

    private static final int CHUNK_SIZE = TERRAIN.chunkSize();
    private static final int CHUNK_RESOLUTION = TERRAIN.chunkResolution();
    private static final int RENDER_DISTANCE = TERRAIN.renderDistance();
    private static final int WORKER_THREADS = TERRAIN.workerThreads();

    private static final int VERTS_PER_SIDE = CHUNK_RESOLUTION + 1;
    // pos(3) + uv(2) + temp(1) + humidity(1) + biomeCell(1) -- biomeCell kommt direkt aus
    // Map.getBiomeCell() (CPU-seitig, respektiert Pool-Clamp + Enabled-Filter), nicht mehr im
    // Shader aus Roh-Temperatur/-Feuchte neu berechnet -- sonst weicht die Debug-Einfärbung
    // (debugMode 3) von der tatsächlich verwendeten Zelle ab, sobald deaktivierte Zellen im Spiel
    // sind (siehe BiomeLookUpTable.enabled).
    private static final int VERTEX_FLOATS = 8;
    private static final int[] SHARED_INDICES = buildSharedIndices();

    private final Map noiseMap;
    private final ExecutorService workerPool;

    private final ConcurrentHashMap<ChunkPos, TerrainChunk> loadedChunks =
            new ConcurrentHashMap<>();
    private final Set<ChunkPos> pendingChunks = ConcurrentHashMap.newKeySet();
    private final UploadQueue uploadQueue = new UploadQueue();

    private int debugMode = 0;
    private int sharedIndexBuffer = 0;

    // Wird bei jedem regenerate() erhöht und in jeden Build-Task hineinkopiert; Ergebnisse
    // aus einer älteren Epoche werden beim Draining verworfen, damit nach einer Parameter-
    // Änderung keine Chunks mit den alten Werten aufblitzen (siehe regenerate()).
    private int generation = 0;

    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f vpMatrix = new Matrix4f();

    public ChunkManager(long seed) {
        noiseMap = new Map(seed);
        workerPool = Executors.newFixedThreadPool(WORKER_THREADS);
    }

    public void update(float playerX, float playerZ) {
        int cx = Math.floorDiv((int) playerX, CHUNK_SIZE);
        int cz = Math.floorDiv((int) playerZ, CHUNK_SIZE);

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

        // Nach dem Distance^2 zum Spieler sortiert einreihen (statt in Rasterreihenfolge ab der
        // -RENDER_DISTANCE-Ecke): der FixedThreadPool arbeitet seine Warteschlange FIFO ab, damit
        // füllt sich die Umgebung des Spielers zuerst -- besonders nach regenerate() sonst
        // sichtbare Löcher rund um den Spieler, während irgendwo am Rand gebaut wird.
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
                java.util.Comparator.comparingInt(
                        pos -> (pos.x() - cx) * (pos.x() - cx) + (pos.z() - cz) * (pos.z() - cz)));
        for (ChunkPos pos : missing) {
            if (pendingChunks.add(pos)) {
                int taskGeneration = generation;
                workerPool.submit(() -> buildAndEnqueue(pos, taskGeneration));
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
    }

    private void buildAndEnqueue(ChunkPos pos, int taskGeneration) {
        uploadQueue.enqueue(new ChunkMeshData(pos, buildVertices(pos), taskGeneration));
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
                float biomeCell = cell[0] * 3 + cell[1];
                i = vert(buf, i, wx, h, wz, u, v, t, hu, biomeCell);
            }
        }
        return buf;
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
            float biomeCell) {
        buf[i++] = x;
        buf[i++] = y;
        buf[i++] = z;
        buf[i++] = u;
        buf[i++] = v;
        buf[i++] = t;
        buf[i++] = h;
        buf[i++] = biomeCell;
        return i;
    }

    private void ensureSharedIndexBuffer() {
        if (sharedIndexBuffer != 0) return;
        sharedIndexBuffer = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sharedIndexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, SHARED_INDICES, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
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

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sharedIndexBuffer);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        return new TerrainChunk(vao, vbo, SHARED_INDICES.length);
    }

    private record TerrainChunk(int vaoId, int vboId, int indexCount) {
        void draw() {
            glBindVertexArray(vaoId);
            glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
            glBindVertexArray(0);
        }

        void cleanup() {
            glDeleteVertexArrays(vaoId);
            glDeleteBuffers(vboId);
        }
    }
}
