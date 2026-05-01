package dev.ptxy.engine.map;

import static org.lwjgl.opengl.GL30.*;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.shader.ShaderCompiler;
import dev.ptxy.engine.shader.ShaderUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/*
 * Chunk-Konfiguration:
 *   CHUNK_SIZE       — Welteinheiten pro Chunk-Seite
 *   CHUNK_RESOLUTION — Quads pro Chunk-Seite; mehr = feineres Mesh, mehr Speicher
 *   RENDER_DISTANCE  — Radius in Chunks um den Spieler, der geladen bleibt
 *   HEIGHT_AMPLITUDE — Skaliert den Noise-Output [-1,1] auf Welteinheiten
 *   WORKER_THREADS   — Parallele Background-Threads für Vertex-Berechnung
 */
public final class ChunkManager {
    private static final int CHUNK_SIZE = 64;
    private static final int CHUNK_RESOLUTION = 64;
    private static final int RENDER_DISTANCE = 4;
    private static final float HEIGHT_AMPLITUDE = 20f;
    private static final int WORKER_THREADS = 2;

    private final Map noiseMap;
    private final ExecutorService workerPool;

    private final ConcurrentHashMap<ChunkPos, TerrainChunk> loadedChunks =
            new ConcurrentHashMap<>();
    private final Set<ChunkPos> pendingChunks = ConcurrentHashMap.newKeySet();
    private final UploadQueue uploadQueue = new UploadQueue();

    public ChunkManager(long seed) {
        noiseMap = new Map(seed);
        workerPool = Executors.newFixedThreadPool(WORKER_THREADS);
    }

    public void update(float playerX, float playerZ) {
        int cx = Math.floorDiv((int) playerX, CHUNK_SIZE);
        int cz = Math.floorDiv((int) playerZ, CHUNK_SIZE);

        for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
            for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
                ChunkPos pos = new ChunkPos(cx + dx, cz + dz);
                if (!loadedChunks.containsKey(pos) && pendingChunks.add(pos)) {
                    workerPool.submit(() -> buildAndEnqueue(pos));
                }
            }
        }

        uploadQueue.drainTo(
                data -> {
                    loadedChunks.put(data.pos(), upload(data));
                    pendingChunks.remove(data.pos());
                });
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

        Matrix4f identity = new Matrix4f().identity();
        for (TerrainChunk chunk : loadedChunks.values()) {
            ShaderUtils.setUniformMat4(shader, "model", identity);
            chunk.draw();
        }

        glUseProgram(0);
    }

    public void shutdown() {
        workerPool.shutdownNow();
        loadedChunks.values().forEach(TerrainChunk::cleanup);
        loadedChunks.clear();
    }

    private void buildAndEnqueue(ChunkPos pos) {
        uploadQueue.enqueue(new ChunkMeshData(pos, buildVertices(pos)));
    }

    private float[] buildVertices(ChunkPos pos) {
        float step = (float) CHUNK_SIZE / CHUNK_RESOLUTION;
        float worldX = pos.x() * CHUNK_SIZE;
        float worldZ = pos.z() * CHUNK_SIZE;

        float[] buf = new float[CHUNK_RESOLUTION * CHUNK_RESOLUTION * 6 * 8];
        int i = 0;

        for (int z = 0; z < CHUNK_RESOLUTION; z++) {
            for (int x = 0; x < CHUNK_RESOLUTION; x++) {
                float x0 = worldX + x * step, x1 = x0 + step;
                float z0 = worldZ + z * step, z1 = z0 + step;
                float h00 = height(x0, z0), h10 = height(x1, z0);
                float h01 = height(x0, z1), h11 = height(x1, z1);
                float u0 = (float) x / CHUNK_RESOLUTION, u1 = (float) (x + 1) / CHUNK_RESOLUTION;
                float v0 = (float) z / CHUNK_RESOLUTION, v1 = (float) (z + 1) / CHUNK_RESOLUTION;

                Vector3f n1 = normal(x0, z0, x1, z1, x1, z0, h00, h11, h10);
                i = vert(buf, i, x0, h00, z0, n1, u0, v0);
                i = vert(buf, i, x1, h11, z1, n1, u1, v1);
                i = vert(buf, i, x1, h10, z0, n1, u1, v0);

                Vector3f n2 = normal(x0, z0, x0, z1, x1, z1, h00, h01, h11);
                i = vert(buf, i, x0, h00, z0, n2, u0, v0);
                i = vert(buf, i, x0, h01, z1, n2, u0, v1);
                i = vert(buf, i, x1, h11, z1, n2, u1, v1);
            }
        }
        return buf;
    }

    private float height(float wx, float wz) {
        return (float) noiseMap.getHeight(wx, wz) * HEIGHT_AMPLITUDE;
    }

    private Vector3f normal(
            float x0,
            float z0,
            float x1,
            float z1,
            float x2,
            float z2,
            float h0,
            float h1,
            float h2) {
        return new Vector3f(x1 - x0, h1 - h0, z1 - z0)
                .cross(new Vector3f(x2 - x0, h2 - h0, z2 - z0))
                .normalize();
    }

    private int vert(float[] buf, int i, float x, float y, float z, Vector3f n, float u, float v) {
        buf[i++] = x;
        buf[i++] = y;
        buf[i++] = z;
        buf[i++] = n.x;
        buf[i++] = n.y;
        buf[i++] = n.z;
        buf[i++] = u;
        buf[i++] = v;
        return i;
    }

    private TerrainChunk upload(ChunkMeshData data) {
        int vao = glGenVertexArrays();
        int vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data.vertices(), GL_STATIC_DRAW);
        int stride = 8 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        return new TerrainChunk(vao, vbo, data.vertices().length / 8);
    }

    private record TerrainChunk(int vaoId, int vboId, int vertexCount) {
        void draw() {
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            glBindVertexArray(0);
        }

        void cleanup() {
            glDeleteVertexArrays(vaoId);
            glDeleteBuffers(vboId);
        }
    }
}
