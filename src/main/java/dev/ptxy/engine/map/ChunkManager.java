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
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

public final class ChunkManager {
    private static final Logger log = LogManager.getLogger(ChunkManager.class);

    private static final TerrainConfig TERRAIN = Config.getTerrainConfig();

    private static final int CHUNK_SIZE = TERRAIN.chunkSize();
    private static final int CHUNK_RESOLUTION = TERRAIN.chunkResolution();
    private static final int RENDER_DISTANCE = TERRAIN.renderDistance();
    private static final int WORKER_THREADS = TERRAIN.workerThreads();

    private static final int VEGETATION_RENDER_DISTANCE =
            Config.getVegetationConfig().renderDistance();

    private static final int VERTS_PER_SIDE = CHUNK_RESOLUTION + 1;

    private static final int VERTEX_FLOATS = 9;

    private static final int VEGETATION_NODE_FLOATS = 4;

    private static final int BLADES_PER_NODE = 8;
    private static final int BLADE_SEGMENTS = 3;
    private static final float[] BLADE_MESH = buildBladeMesh();
    private static final int BLADE_MESH_FLOATS = 3;
    private static final int BLADE_VERTEX_COUNT = BLADE_MESH.length / BLADE_MESH_FLOATS;
    private static final int[] SHARED_INDICES = buildSharedIndices();

    private static final int TREE_SIDES = 6;

    private static final float TREE_TRUNK_HEIGHT_T = 0.65f;

    private static final float TREE_TRUNK_RADIUS = 0.28f;
    private static final float[] TREE_MESH = buildTreeMesh();
    private static final int TREE_MESH_FLOATS = 7;
    private static final int TREE_VERTEX_COUNT = TREE_MESH.length / TREE_MESH_FLOATS;

    private static final int ROCK_SIDES = 10;
    private static final float ROCK_RING_HEIGHT_T = 0.75f;
    private static final float ROCK_BASE_RADIUS_FRACTION = 0.55f;
    private static final float[] ROCK_MESH = buildRockMesh();
    private static final int ROCK_MESH_FLOATS = 7;
    private static final int ROCK_VERTEX_COUNT = ROCK_MESH.length / ROCK_MESH_FLOATS;

    @Getter private final Map noiseMap;
    @Getter private final VegetationController vegetationController = new VegetationController();
    private final ExecutorService workerPool;

    private final ConcurrentHashMap<ChunkPos, TerrainChunk> loadedChunks =
            new ConcurrentHashMap<>();
    private final Set<ChunkPos> pendingChunks = ConcurrentHashMap.newKeySet();
    private final UploadQueue uploadQueue = new UploadQueue();

    @Setter private int debugMode = 0;

    @Getter @Setter private boolean vegetationEnabled = true;
    private int sharedIndexBuffer = 0;
    private int sharedBladeBuffer = 0;
    private int sharedTreeBuffer = 0;
    private int sharedRockBuffer = 0;

    private int generation = 0;

    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f vpMatrix = new Matrix4f();

    private final FrustumIntersection loadPriorityFrustum = new FrustumIntersection();
    private final Matrix4f loadPriorityVpMatrix = new Matrix4f();

    private int playerChunkX = 0;
    private int playerChunkZ = 0;

    public ChunkManager(long seed) {
        noiseMap = new Map(seed);
        workerPool =
                Executors.newFixedThreadPool(
                        WORKER_THREADS,
                        r -> {
                            Thread thread = new Thread(r);
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    private static final int CHUNK_PRIORITY_CORE_RADIUS = 3;

    private static final int MAX_IN_FLIGHT_BUILDS = WORKER_THREADS;

    public void update(float playerX, float playerZ, SimpleCamera3D camera) {
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

        if (!camera.isRotating()) {
            camera.getProjection().mul(camera.getViewMatrix(), loadPriorityVpMatrix);
            loadPriorityFrustum.set(loadPriorityVpMatrix);
        }

        java.util.List<ChunkPos> toBuild = new java.util.ArrayList<>();
        for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
            for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
                ChunkPos pos = new ChunkPos(cx + dx, cz + dz);
                if (pendingChunks.contains(pos)) continue;

                TerrainChunk existing = loadedChunks.get(pos);
                boolean needsVegetationUpgrade =
                        existing != null
                                && !existing.vegetationComputed()
                                && Math.abs(pos.x() - cx) <= VEGETATION_RENDER_DISTANCE
                                && Math.abs(pos.z() - cz) <= VEGETATION_RENDER_DISTANCE;
                if (existing == null || needsVegetationUpgrade) {
                    toBuild.add(pos);
                }
            }
        }
        float heightAmplitude = heightAmplitude();
        toBuild.sort(
                java.util.Comparator.comparingInt(
                                (ChunkPos pos) -> loadPriorityTier(pos, cx, cz, heightAmplitude))
                        .thenComparingDouble(pos -> chunkDistance(pos, cx, cz)));
        int freeBuildSlots = MAX_IN_FLIGHT_BUILDS - pendingChunks.size();
        for (ChunkPos pos : toBuild) {
            if (freeBuildSlots <= 0) break;
            if (pendingChunks.add(pos)) {
                freeBuildSlots--;
                int taskGeneration = generation;
                int taskPlayerChunkX = cx;
                int taskPlayerChunkZ = cz;
                workerPool.submit(
                        () -> {
                            try {
                                buildAndEnqueue(
                                        pos, taskGeneration, taskPlayerChunkX, taskPlayerChunkZ);
                            } catch (RuntimeException | Error e) {
                                log.error("Failed to build chunk {}", pos, e);
                                pendingChunks.remove(pos);
                            }
                        });
            }
        }

        uploadQueue.drainTo(
                data -> {
                    if (data.generation() != generation) return;
                    if (pendingChunks.contains(data.pos())) {
                        TerrainChunk old = loadedChunks.put(data.pos(), upload(data));
                        if (old != null) old.cleanup();
                    }
                    pendingChunks.remove(data.pos());
                });
    }

    private int loadPriorityTier(ChunkPos pos, int cx, int cz, float heightAmplitude) {
        if (chunkDistance(pos, cx, cz) <= CHUNK_PRIORITY_CORE_RADIUS) return 0;
        return isInLoadPriorityFrustum(pos, heightAmplitude) ? 1 : 2;
    }

    private static double chunkDistance(ChunkPos pos, int cx, int cz) {
        double dx = pos.x() - cx;
        double dz = pos.z() - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean isInLoadPriorityFrustum(ChunkPos pos, float heightAmplitude) {
        float wx = pos.x() * CHUNK_SIZE;
        float wz = pos.z() * CHUNK_SIZE;
        return loadPriorityFrustum.testAab(
                wx, -heightAmplitude, wz, wx + CHUNK_SIZE, heightAmplitude, wz + CHUNK_SIZE);
    }

    public void regenerate() {
        log.info(
                "DEBUG regenerate() called, generation {} -> {}, loadedChunks={}",
                generation,
                generation + 1,
                loadedChunks.size());
        generation++;
        pendingChunks.clear();
        uploadQueue.clear();
        loadedChunks.values().forEach(TerrainChunk::cleanup);
        loadedChunks.clear();
    }

    public void logDebugState() {
        long withVeg =
                loadedChunks.values().stream().filter(TerrainChunk::vegetationComputed).count();
        log.info(
                "DEBUG state: loadedChunks={} pendingChunks={} withVegetation={}"
                        + " withoutVegetation={} generation={} playerChunk=({},{})"
                        + " vegetationEnabled={}",
                loadedChunks.size(),
                pendingChunks.size(),
                withVeg,
                loadedChunks.size() - withVeg,
                generation,
                playerChunkX,
                playerChunkZ,
                vegetationEnabled);
    }

    public void renderVegetation(SimpleCamera3D camera, DirectionalLight light, float time) {
        if (!vegetationEnabled) return;

        boolean windEnabled = vegetationController.isWindEnabled();
        VegetationConfig.VegetationType grass = Config.getVegetationConfig().type("grass");
        renderVegetationLayer(
                windEnabled ? "vegetation_wind" : "vegetation",
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
                    if (windEnabled) {
                        ShaderUtils.setUniformFloat(shader, "time", time);
                    }
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

        if (vegetationController.isRocksEnabled()) {
            VegetationConfig.VegetationType rock = Config.getVegetationConfig().type("rock");
            renderVegetationLayer(
                    "rock",
                    camera,
                    light,
                    TerrainChunk::drawRock,
                    shader -> {
                        ShaderUtils.setUniformFloat(
                                shader, "heightMin", (float) rock.scale("heightMin"));
                        ShaderUtils.setUniformFloat(
                                shader, "heightMax", (float) rock.scale("heightMax"));
                        ShaderUtils.setUniformFloat(
                                shader, "radiusMin", (float) rock.scale("radiusMin"));
                        ShaderUtils.setUniformFloat(
                                shader, "radiusMax", (float) rock.scale("radiusMax"));
                    });
        }
    }

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

    private void buildAndEnqueue(
            ChunkPos pos, int taskGeneration, int playerChunkX, int playerChunkZ) {
        boolean withinVegetationRange =
                Math.abs(pos.x() - playerChunkX) <= VEGETATION_RENDER_DISTANCE
                        && Math.abs(pos.z() - playerChunkZ) <= VEGETATION_RENDER_DISTANCE;
        boolean organicClumpingEnabled = vegetationController.isOrganicClumpingEnabled();
        float[] grassNodes =
                withinVegetationRange
                        ? VegetationPlacer.place(
                                pos,
                                noiseMap,
                                CHUNK_SIZE,
                                heightAmplitude(),
                                "grass",
                                organicClumpingEnabled)
                        : new float[0];
        float[] treeNodes =
                withinVegetationRange
                        ? VegetationPlacer.place(
                                pos,
                                noiseMap,
                                CHUNK_SIZE,
                                heightAmplitude(),
                                "tree",
                                organicClumpingEnabled)
                        : new float[0];
        float[] rockNodes =
                withinVegetationRange
                        ? VegetationPlacer.place(
                                pos,
                                noiseMap,
                                CHUNK_SIZE,
                                heightAmplitude(),
                                "rock",
                                organicClumpingEnabled)
                        : new float[0];
        uploadQueue.enqueue(
                new ChunkMeshData(
                        pos,
                        buildVertices(pos),
                        grassNodes,
                        treeNodes,
                        rockNodes,
                        withinVegetationRange,
                        taskGeneration));
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
                Map.TerrainSample sample = noiseMap.sampleTerrain(wx, wz);
                float h = (float) sample.height() * heightAmplitude();
                float t =
                        (float)
                                (sample.rawTemperature()
                                        - h * Config.getTerrainParams().heightTempLapse());
                float hu = (float) sample.humidity();
                int[] cell = sample.nearestCell();
                int cellA = cell[0] * 3 + cell[1];
                Map.BiomeWeights weights = sample.weights();
                i =
                        vert(
                                buf,
                                i,
                                wx,
                                h,
                                wz,
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

    private static float[] buildBladeMesh() {
        float[] verts = new float[(BLADE_SEGMENTS + 1) * 2 * BLADE_MESH_FLOATS];
        int i = 0;
        for (int seg = 0; seg <= BLADE_SEGMENTS; seg++) {
            float t = (float) seg / BLADE_SEGMENTS;
            float taper = 1.0f - t * 0.85f;
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
            addVertex(verts, tx0, trunkY, tz0, trunkNx, 0f, trunkNz, trunkY);
            addVertex(verts, tx1, 0f, tz1, trunkNx, 0f, trunkNz, 0f);
            addVertex(verts, tx0, trunkY, tz0, trunkNx, 0f, trunkNz, trunkY);
            addVertex(verts, tx1, trunkY, tz1, trunkNx, 0f, trunkNz, trunkY);
            addVertex(verts, tx1, 0f, tz1, trunkNx, 0f, trunkNz, 0f);

            addVertex(verts, tx0, trunkY, tz0, 0f, 1f, 0f, trunkY);
            addVertex(verts, tx1, trunkY, tz1, 0f, 1f, 0f, trunkY);
            addVertex(verts, x0, trunkY, z0, 0f, 1f, 0f, trunkY);
            addVertex(verts, x0, trunkY, z0, 0f, 1f, 0f, trunkY);
            addVertex(verts, tx1, trunkY, tz1, 0f, 1f, 0f, trunkY);
            addVertex(verts, x1, trunkY, z1, 0f, 1f, 0f, trunkY);

            float coneNy = 1.0f / coneHeight;
            float coneNx = (x0 + x1) * 0.5f;
            float coneNz = (z0 + z1) * 0.5f;
            float coneNlen = (float) Math.sqrt(coneNx * coneNx + coneNz * coneNz + coneNy * coneNy);
            coneNx /= coneNlen;
            coneNz /= coneNlen;
            coneNy /= coneNlen;
            addVertex(verts, x0, trunkY, z0, coneNx, coneNy, coneNz, trunkY);
            addVertex(verts, 0f, 1f, 0f, coneNx, coneNy, coneNz, 1f);
            addVertex(verts, x1, trunkY, z1, coneNx, coneNy, coneNz, trunkY);
        }

        float[] arr = new float[verts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = verts.get(i);
        return arr;
    }

    private static float[] buildRockMesh() {
        java.util.List<Float> verts = new java.util.ArrayList<>();
        float ringHeight = ROCK_RING_HEIGHT_T;
        float lowerWallHeight = ringHeight;
        float topConeHeight = 1.0f - ringHeight;

        float[] midRadius = new float[ROCK_SIDES];
        float[] baseRadius = new float[ROCK_SIDES];
        for (int side = 0; side < ROCK_SIDES; side++) {
            midRadius[side] = 1.0f + 0.18f * (float) Math.sin(side * 2.399963f);
            baseRadius[side] =
                    ROCK_BASE_RADIUS_FRACTION
                            * (1.0f + 0.15f * (float) Math.sin(side * 3.883222f + 1.7f));
        }

        for (int side = 0; side < ROCK_SIDES; side++) {
            int next = (side + 1) % ROCK_SIDES;
            float a0 = (float) (side * 2.0 * Math.PI / ROCK_SIDES);
            float a1 = (float) (next * 2.0 * Math.PI / ROCK_SIDES);
            float cosA0 = (float) Math.cos(a0);
            float sinA0 = (float) Math.sin(a0);
            float cosA1 = (float) Math.cos(a1);
            float sinA1 = (float) Math.sin(a1);

            float base0x = cosA0 * baseRadius[side];
            float base0z = sinA0 * baseRadius[side];
            float base1x = cosA1 * baseRadius[next];
            float base1z = sinA1 * baseRadius[next];
            float mid0x = cosA0 * midRadius[side];
            float mid0z = sinA0 * midRadius[side];
            float mid1x = cosA1 * midRadius[next];
            float mid1z = sinA1 * midRadius[next];

            float wallNx = (base0x + base1x + mid0x + mid1x) * 0.25f;
            float wallNz = (base0z + base1z + mid0z + mid1z) * 0.25f;
            float wallNy = -1.0f / lowerWallHeight;
            float wallNlen = (float) Math.sqrt(wallNx * wallNx + wallNy * wallNy + wallNz * wallNz);
            wallNx /= wallNlen;
            wallNy /= wallNlen;
            wallNz /= wallNlen;

            addVertex(verts, base0x, 0f, base0z, wallNx, wallNy, wallNz, 0f);
            addVertex(verts, mid0x, ringHeight, mid0z, wallNx, wallNy, wallNz, ringHeight);
            addVertex(verts, base1x, 0f, base1z, wallNx, wallNy, wallNz, 0f);
            addVertex(verts, mid0x, ringHeight, mid0z, wallNx, wallNy, wallNz, ringHeight);
            addVertex(verts, mid1x, ringHeight, mid1z, wallNx, wallNy, wallNz, ringHeight);
            addVertex(verts, base1x, 0f, base1z, wallNx, wallNy, wallNz, 0f);

            float topNx = (mid0x + mid1x) * 0.5f;
            float topNz = (mid0z + mid1z) * 0.5f;
            float topNy = 1.0f / topConeHeight;
            float topNlen = (float) Math.sqrt(topNx * topNx + topNy * topNy + topNz * topNz);
            topNx /= topNlen;
            topNz /= topNlen;
            topNy /= topNlen;

            addVertex(verts, mid0x, ringHeight, mid0z, topNx, topNy, topNz, ringHeight);
            addVertex(verts, 0f, 1f, 0f, topNx, topNy, topNz, 1f);
            addVertex(verts, mid1x, ringHeight, mid1z, topNx, topNy, topNz, ringHeight);
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

    private float heightAmplitude() {
        return Config.getTerrainParams().heightAmplitude();
    }

    private int vert(
            float[] buf,
            int i,
            float x,
            float y,
            float z,
            float t,
            float h,
            int cellA,
            float weightTundra,
            float weightSavanna,
            float weightRainforest) {
        buf[i++] = x;
        buf[i++] = y;
        buf[i++] = z;
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

    private void ensureSharedRockBuffer() {
        if (sharedRockBuffer != 0) return;
        sharedRockBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, sharedRockBuffer);
        glBufferData(GL_ARRAY_BUFFER, ROCK_MESH, GL_STATIC_DRAW);
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
        glVertexAttribPointer(1, 1, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 4L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);
        glEnableVertexAttribArray(5);
        glVertexAttribPointer(6, 1, GL_FLOAT, false, stride, 8L * Float.BYTES);
        glEnableVertexAttribArray(6);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sharedIndexBuffer);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        GrassUpload grass = uploadGrass(data);
        TreeUpload tree = uploadTree(data);
        RockUpload rock = uploadRock(data);
        return new TerrainChunk(
                vao,
                vbo,
                SHARED_INDICES.length,
                grass.vao(),
                grass.vbo(),
                grass.instanceCount(),
                tree.vao(),
                tree.vbo(),
                tree.instanceCount(),
                rock.vao(),
                rock.vbo(),
                rock.instanceCount(),
                data.vegetationComputed());
    }

    private GrassUpload uploadGrass(ChunkMeshData data) {
        if (data.grassNodes().length == 0) {
            return new GrassUpload(0, 0, 0);
        }

        ensureSharedBladeBuffer();

        int grassVao = glGenVertexArrays();
        int grassVbo = glGenBuffers();
        glBindVertexArray(grassVao);

        glBindBuffer(GL_ARRAY_BUFFER, sharedBladeBuffer);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, BLADE_MESH_FLOATS * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

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

    private TreeUpload uploadTree(ChunkMeshData data) {
        if (data.treeNodes().length == 0) {
            return new TreeUpload(0, 0, 0);
        }

        ensureSharedTreeBuffer();

        int treeVao = glGenVertexArrays();
        int treeVbo = glGenBuffers();
        glBindVertexArray(treeVao);

        glBindBuffer(GL_ARRAY_BUFFER, sharedTreeBuffer);
        int treeMeshStride = TREE_MESH_FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, treeMeshStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, treeMeshStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, treeMeshStride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);

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

    private RockUpload uploadRock(ChunkMeshData data) {
        if (data.rockNodes().length == 0) {
            return new RockUpload(0, 0, 0);
        }

        ensureSharedRockBuffer();

        int rockVao = glGenVertexArrays();
        int rockVbo = glGenBuffers();
        glBindVertexArray(rockVao);

        glBindBuffer(GL_ARRAY_BUFFER, sharedRockBuffer);
        int rockMeshStride = ROCK_MESH_FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, rockMeshStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, rockMeshStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, rockMeshStride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, rockVbo);
        glBufferData(GL_ARRAY_BUFFER, data.rockNodes(), GL_STATIC_DRAW);
        int rockNodeStride = VEGETATION_NODE_FLOATS * Float.BYTES;
        glVertexAttribPointer(3, 3, GL_FLOAT, false, rockNodeStride, 0L);
        glEnableVertexAttribArray(3);
        glVertexAttribDivisor(3, 1);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, rockNodeStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribDivisor(4, 1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        int nodeCount = data.rockNodes().length / VEGETATION_NODE_FLOATS;
        return new RockUpload(rockVao, rockVbo, nodeCount);
    }

    private record GrassUpload(int vao, int vbo, int instanceCount) {}

    private record TreeUpload(int vao, int vbo, int instanceCount) {}

    private record RockUpload(int vao, int vbo, int instanceCount) {}

    private record TerrainChunk(
            int vaoId,
            int vboId,
            int indexCount,
            int grassVaoId,
            int grassVboId,
            int grassInstanceCount,
            int treeVaoId,
            int treeVboId,
            int treeInstanceCount,
            int rockVaoId,
            int rockVboId,
            int rockInstanceCount,
            boolean vegetationComputed) {
        void draw() {
            glBindVertexArray(vaoId);
            glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
            glBindVertexArray(0);
        }

        void drawGrass() {
            if (grassInstanceCount == 0) return;
            glBindVertexArray(grassVaoId);
            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, BLADE_VERTEX_COUNT, grassInstanceCount);
            glBindVertexArray(0);
        }

        void drawTree() {
            if (treeInstanceCount == 0) return;
            glDisable(GL_CULL_FACE);
            glBindVertexArray(treeVaoId);
            glDrawArraysInstanced(GL_TRIANGLES, 0, TREE_VERTEX_COUNT, treeInstanceCount);
            glBindVertexArray(0);
            glEnable(GL_CULL_FACE);
        }

        void drawRock() {
            if (rockInstanceCount == 0) return;
            glBindVertexArray(rockVaoId);
            glDrawArraysInstanced(GL_TRIANGLES, 0, ROCK_VERTEX_COUNT, rockInstanceCount);
            glBindVertexArray(0);
        }

        void cleanup() {
            glDeleteVertexArrays(vaoId);
            glDeleteBuffers(vboId);
            if (grassVaoId != 0) glDeleteVertexArrays(grassVaoId);
            if (grassVboId != 0) glDeleteBuffers(grassVboId);
            if (treeVaoId != 0) glDeleteVertexArrays(treeVaoId);
            if (treeVboId != 0) glDeleteBuffers(treeVboId);
            if (rockVaoId != 0) glDeleteVertexArrays(rockVaoId);
            if (rockVboId != 0) glDeleteBuffers(rockVboId);
        }
    }
}
