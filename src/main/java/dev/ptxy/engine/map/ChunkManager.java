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
import dev.ptxy.engine.shader.TextureLoader;
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
    private static final int MAX_CHUNK_UPLOADS_PER_FRAME = TERRAIN.maxChunkUploadsPerFrame();

    private static final int VEGETATION_RENDER_DISTANCE =
            Config.getVegetationConfig().renderDistance();
    // Grass is by far the densest vegetation type (88-97% ground coverage vs. under 1% for
    // trees/rocks), so it alone dominates fill-rate — giving it its own, much shorter view
    // distance cuts the worst-case overdraw without shrinking how far trees/rocks stay visible.
    private static final int GRASS_RENDER_DISTANCE =
            Config.getVegetationConfig().grassRenderDistance();

    private static final int VERTS_PER_SIDE = CHUNK_RESOLUTION + 1;

    private static final int VERTEX_FLOATS = 9;

    private static final int VEGETATION_NODE_FLOATS = 4;

    private static final int BLADES_PER_NODE = Config.getVegetationConfig().bladesPerNode();
    private static final int BLADE_SEGMENTS = 2;
    private static final float[] BLADE_MESH = buildBladeMesh();
    private static final int BLADE_MESH_FLOATS = 8;
    private static final int BLADE_VERTEX_COUNT = BLADE_MESH.length / BLADE_MESH_FLOATS;
    private static final int[] SHARED_INDICES = buildSharedIndices();

    private static final int TREE_SIDES = 6;
    private static final int TREE_FOLIAGE_SIDES = 6;

    private static final float TREE_TRUNK_HEIGHT_T =
            (float) Config.getVegetationConfig().treeTrunkHeightFraction();

    private static final float TREE_TRUNK_RADIUS = 0.09f;
    private static final int TREE_MESH_FLOATS = 7;
    private static final float[] TREE_MESH = buildTreeMeshGeneric();
    private static final int TREE_VERTEX_COUNT = TREE_MESH.length / TREE_MESH_FLOATS;
    private static final float[] TREE_SAVANNA_MESH = buildTreeMeshSavanna();
    private static final int TREE_SAVANNA_VERTEX_COUNT =
            TREE_SAVANNA_MESH.length / TREE_MESH_FLOATS;
    private static final float[] TREE_TUNDRA_MESH = buildTreeMeshTundra();
    private static final int TREE_TUNDRA_VERTEX_COUNT = TREE_TUNDRA_MESH.length / TREE_MESH_FLOATS;
    private static final float[] TREE_DECIDUOUS_MESH = buildTreeMeshDeciduous();
    private static final int TREE_DECIDUOUS_VERTEX_COUNT =
            TREE_DECIDUOUS_MESH.length / TREE_MESH_FLOATS;

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
    private int sharedGrassTexture = 0;
    private int sharedTreeBuffer = 0;
    private int sharedTreeSavannaBuffer = 0;
    private int sharedTreeTundraBuffer = 0;
    private int sharedTreeDeciduousBuffer = 0;
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
                boolean needsGrassUpgrade =
                        existing != null
                                && !existing.grassComputed()
                                && Math.abs(pos.x() - cx) <= GRASS_RENDER_DISTANCE
                                && Math.abs(pos.z() - cz) <= GRASS_RENDER_DISTANCE;
                if (existing == null || needsVegetationUpgrade || needsGrassUpgrade) {
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
                },
                MAX_CHUNK_UPLOADS_PER_FRAME);
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

        ensureSharedGrassTexture();
        boolean windEnabled = vegetationController.isWindEnabled();
        VegetationConfig.VegetationType grass = Config.getVegetationConfig().type("grass");
        renderVegetationLayer(
                windEnabled ? "vegetation_wind" : "vegetation",
                camera,
                light,
                TerrainChunk::drawGrass,
                shader -> {
                    glActiveTexture(GL_TEXTURE0);
                    glBindTexture(GL_TEXTURE_2D, sharedGrassTexture);
                    ShaderUtils.setUniformInt(shader, "grassTexture", 0);
                    ShaderUtils.setUniformVec3(shader, "camPos", camera.getPosition());
                    VegetationConfig vegConfig = Config.getVegetationConfig();
                    ShaderUtils.setUniformInt(shader, "bladesPerNode", BLADES_PER_NODE);
                    ShaderUtils.setUniformFloat(
                            shader, "scatterRadius", (float) vegConfig.bladeScatterRadiusMeters());
                    ShaderUtils.setUniformFloat(
                            shader,
                            "lodNearDistance",
                            (float) vegConfig.bladeLodNearDistanceMeters());
                    ShaderUtils.setUniformFloat(
                            shader,
                            "lodFarDistance",
                            (float) vegConfig.bladeLodFarDistanceMeters());
                    ShaderUtils.setUniformFloat(
                            shader, "lodMinBlades", (float) vegConfig.bladeLodMinBlades());
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
        java.util.function.IntConsumer treeScaleUniforms =
                shader -> {
                    ShaderUtils.setUniformFloat(
                            shader, "heightMin", (float) tree.scale("heightMin"));
                    ShaderUtils.setUniformFloat(
                            shader, "heightMax", (float) tree.scale("heightMax"));
                    ShaderUtils.setUniformFloat(
                            shader, "radiusMin", (float) tree.scale("radiusMin"));
                    ShaderUtils.setUniformFloat(
                            shader, "radiusMax", (float) tree.scale("radiusMax"));
                    ShaderUtils.setUniformFloat(shader, "trunkHeightT", TREE_TRUNK_HEIGHT_T);
                };
        renderVegetationLayer("tree", camera, light, TerrainChunk::drawTree, treeScaleUniforms);
        renderVegetationLayer(
                "tree", camera, light, TerrainChunk::drawTreeSavanna, treeScaleUniforms);
        renderVegetationLayer(
                "tree", camera, light, TerrainChunk::drawTreeTundra, treeScaleUniforms);
        renderVegetationLayer(
                "tree", camera, light, TerrainChunk::drawTreeDeciduous, treeScaleUniforms);

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
        if (sharedGrassTexture != 0) {
            glDeleteTextures(sharedGrassTexture);
            sharedGrassTexture = 0;
        }
        if (sharedTreeBuffer != 0) {
            glDeleteBuffers(sharedTreeBuffer);
            sharedTreeBuffer = 0;
        }
        if (sharedTreeSavannaBuffer != 0) {
            glDeleteBuffers(sharedTreeSavannaBuffer);
            sharedTreeSavannaBuffer = 0;
        }
        if (sharedTreeTundraBuffer != 0) {
            glDeleteBuffers(sharedTreeTundraBuffer);
            sharedTreeTundraBuffer = 0;
        }
        if (sharedTreeDeciduousBuffer != 0) {
            glDeleteBuffers(sharedTreeDeciduousBuffer);
            sharedTreeDeciduousBuffer = 0;
        }
        if (sharedRockBuffer != 0) {
            glDeleteBuffers(sharedRockBuffer);
            sharedRockBuffer = 0;
        }
    }

    private void buildAndEnqueue(
            ChunkPos pos, int taskGeneration, int playerChunkX, int playerChunkZ) {
        boolean withinVegetationRange =
                Math.abs(pos.x() - playerChunkX) <= VEGETATION_RENDER_DISTANCE
                        && Math.abs(pos.z() - playerChunkZ) <= VEGETATION_RENDER_DISTANCE;
        boolean withinGrassRange =
                Math.abs(pos.x() - playerChunkX) <= GRASS_RENDER_DISTANCE
                        && Math.abs(pos.z() - playerChunkZ) <= GRASS_RENDER_DISTANCE;
        boolean organicClumpingEnabled = vegetationController.isOrganicClumpingEnabled();
        float[] grassNodes =
                withinGrassRange
                        ? VegetationPlacer.place(
                                pos,
                                noiseMap,
                                CHUNK_SIZE,
                                heightAmplitude(),
                                "grass",
                                organicClumpingEnabled)
                        : new float[0];
        float[] treeNodesAll =
                withinVegetationRange
                        ? VegetationPlacer.place(
                                pos,
                                noiseMap,
                                CHUNK_SIZE,
                                heightAmplitude(),
                                "tree",
                                organicClumpingEnabled)
                        : new float[0];
        float[][] treeNodesByMesh = splitTreeNodesByBiome(treeNodesAll);
        float[] treeNodes = treeNodesByMesh[0];
        float[] treeNodesSavanna = treeNodesByMesh[1];
        float[] treeNodesTundra = treeNodesByMesh[2];
        float[] treeNodesDeciduous = treeNodesByMesh[3];
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
                        treeNodesSavanna,
                        treeNodesTundra,
                        treeNodesDeciduous,
                        rockNodes,
                        withinVegetationRange,
                        withinGrassRange,
                        taskGeneration));
    }

    // Splits placed tree nodes into per-mesh buckets by biome ordinal, one bucket per biome that
    // has its own dedicated tree mesh, plus an "other" bucket (generic mesh) for every
    // undesigned biome cell that still falls back to it.
    private static float[][] splitTreeNodesByBiome(float[] nodes) {
        int savannaOrdinal = Biome.SAVANNA_PRAIRIE.ordinal();
        int tundraOrdinal = Biome.TUNDRA_STEPPE.ordinal();
        int deciduousOrdinal = Biome.DECIDUOUS_FOREST.ordinal();
        int count = nodes.length / VEGETATION_NODE_FLOATS;

        int otherCount = 0;
        int savannaCount = 0;
        int tundraCount = 0;
        int deciduousCount = 0;
        for (int i = 0; i < count; i++) {
            int biomeOrdinal = (int) (nodes[i * VEGETATION_NODE_FLOATS + 3] + 0.5f);
            if (biomeOrdinal == savannaOrdinal) savannaCount++;
            else if (biomeOrdinal == tundraOrdinal) tundraCount++;
            else if (biomeOrdinal == deciduousOrdinal) deciduousCount++;
            else otherCount++;
        }

        float[] other = new float[otherCount * VEGETATION_NODE_FLOATS];
        float[] savanna = new float[savannaCount * VEGETATION_NODE_FLOATS];
        float[] tundra = new float[tundraCount * VEGETATION_NODE_FLOATS];
        float[] deciduous = new float[deciduousCount * VEGETATION_NODE_FLOATS];
        int otherIndex = 0;
        int savannaIndex = 0;
        int tundraIndex = 0;
        int deciduousIndex = 0;
        for (int i = 0; i < count; i++) {
            int base = i * VEGETATION_NODE_FLOATS;
            int biomeOrdinal = (int) (nodes[base + 3] + 0.5f);
            float[] bucket;
            int bucketIndex;
            if (biomeOrdinal == savannaOrdinal) {
                bucket = savanna;
                bucketIndex = savannaIndex++;
            } else if (biomeOrdinal == tundraOrdinal) {
                bucket = tundra;
                bucketIndex = tundraIndex++;
            } else if (biomeOrdinal == deciduousOrdinal) {
                bucket = deciduous;
                bucketIndex = deciduousIndex++;
            } else {
                bucket = other;
                bucketIndex = otherIndex++;
            }
            System.arraycopy(
                    nodes,
                    base,
                    bucket,
                    bucketIndex * VEGETATION_NODE_FLOATS,
                    VEGETATION_NODE_FLOATS);
        }
        return new float[][] {other, savanna, tundra, deciduous};
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
                                (float) weights.deciduousForest());
            }
        }
        return buf;
    }

    /**
     * A single thin, tapered, textured blade bent into BLADE_SEGMENTS+1 rows instead of one flat
     * quad — the local Z per row (leanCurve) is a fixed shape baked into the mesh, and the vertex
     * shader multiplies it by a random per-instance leanScale (can be negative) so each instance
     * bends its own way. Quantity comes from instancing many separate blades per node, not from
     * packing multiple blades into one card.
     */
    private static float[] buildBladeMesh() {
        java.util.List<Float> verts = new java.util.ArrayList<>();
        float[] leftX = new float[BLADE_SEGMENTS + 1];
        float[] rightX = new float[BLADE_SEGMENTS + 1];
        float[] y = new float[BLADE_SEGMENTS + 1];
        float[] z = new float[BLADE_SEGMENTS + 1];
        for (int seg = 0; seg <= BLADE_SEGMENTS; seg++) {
            float t = (float) seg / BLADE_SEGMENTS;
            float taper = 1.0f - t * 0.7f;
            leftX[seg] = -taper;
            rightX[seg] = taper;
            y[seg] = t;
            z[seg] = t * t;
        }

        float normalX = 0f;
        float normalZ = 1f;
        float uLeft = 0f;
        float uRight = 1f;
        for (int seg = 0; seg < BLADE_SEGMENTS; seg++) {
            float v0 = (float) seg / BLADE_SEGMENTS;
            float v1 = (float) (seg + 1) / BLADE_SEGMENTS;

            addVegetationVertex(verts, leftX[seg], y[seg], z[seg], normalX, normalZ, uLeft, v0);
            addVegetationVertex(verts, rightX[seg], y[seg], z[seg], normalX, normalZ, uRight, v0);
            addVegetationVertex(
                    verts, rightX[seg + 1], y[seg + 1], z[seg + 1], normalX, normalZ, uRight, v1);

            addVegetationVertex(verts, leftX[seg], y[seg], z[seg], normalX, normalZ, uLeft, v0);
            addVegetationVertex(
                    verts, rightX[seg + 1], y[seg + 1], z[seg + 1], normalX, normalZ, uRight, v1);
            addVegetationVertex(
                    verts, leftX[seg + 1], y[seg + 1], z[seg + 1], normalX, normalZ, uLeft, v1);
        }
        return toArray(verts);
    }

    private static void addVegetationVertex(
            java.util.List<Float> verts,
            float x,
            float y,
            float z,
            float normalX,
            float normalZ,
            float u,
            float v) {
        verts.add(x);
        verts.add(y);
        verts.add(z);
        verts.add(normalX);
        verts.add(0f);
        verts.add(normalZ);
        verts.add(u);
        verts.add(v);
    }

    private static void addTrunk(
            java.util.List<Float> verts, float trunkHeight, float trunkRadius) {
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
            float tx0 = x0 * trunkRadius;
            float tz0 = z0 * trunkRadius;
            float tx1 = x1 * trunkRadius;
            float tz1 = z1 * trunkRadius;
            addVertex(verts, tx0, 0f, tz0, trunkNx, 0f, trunkNz, 0f);
            addVertex(verts, tx0, trunkHeight, tz0, trunkNx, 0f, trunkNz, trunkHeight);
            addVertex(verts, tx1, 0f, tz1, trunkNx, 0f, trunkNz, 0f);
            addVertex(verts, tx0, trunkHeight, tz0, trunkNx, 0f, trunkNz, trunkHeight);
            addVertex(verts, tx1, trunkHeight, tz1, trunkNx, 0f, trunkNz, trunkHeight);
            addVertex(verts, tx1, 0f, tz1, trunkNx, 0f, trunkNz, 0f);
        }
    }

    private static float[] toArray(java.util.List<Float> verts) {
        float[] arr = new float[verts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = verts.get(i);
        return arr;
    }

    // Minecraft-spruce-style conifer: flat-based foliage layers stacked with visible trunk gaps
    // between them, instead of a smooth continuous cone — the stepped silhouette with bare trunk
    // peeking through is what reads as "spruce" rather than a generic Christmas-tree cone.
    private static float[] buildTreeMeshGeneric() {
        java.util.List<Float> verts = new java.util.ArrayList<>();
        addTrunk(verts, TREE_TRUNK_HEIGHT_T, TREE_TRUNK_RADIUS);

        addConeTier(verts, 0.12f, 0.30f, 0.46f, 8, 0.85f);
        addConeTier(verts, 0.34f, 0.50f, 0.36f, 8, 0.87f);
        addConeTier(verts, 0.54f, 0.68f, 0.26f, 8, 0.9f);
        addConeTier(verts, 0.72f, 0.84f, 0.17f, 8, 0.92f);
        addConeTier(verts, 0.88f, 1.05f, 0.09f, 8, 0.95f);

        return toArray(verts);
    }

    // Loaded from an .obj instead of built from hand-rolled bipyramids: an icosphere-based
    // canopy (regenerate via tools/gen_savanna_tree.py) has real subdivided roundness that the
    // low-sided procedural clusters couldn't reach, matching the soft lumpy acacia crowns in
    // the reference photos (savanne_01.jpg).
    private static float[] buildTreeMeshSavanna() {
        return ObjTreeMeshLoader.load("/models/savanna_tree.obj");
    }

    // Tall, narrow conifer silhouette (regenerate via tools/gen_tundra_tree.py) for the
    // tundra/steppe biome, replacing the generic mesh's stepped cone with a proper single
    // tapering fir/spruce shape typical of a snow biome.
    private static float[] buildTreeMeshTundra() {
        return ObjTreeMeshLoader.load("/models/tundra_tree.obj");
    }

    // Tall bare trunk topped by a broad, rounded, multi-lobed broadleaf crown (regenerate via
    // tools/gen_deciduous_tree.py), for the deciduous biome.
    private static float[] buildTreeMeshDeciduous() {
        return ObjTreeMeshLoader.load("/models/deciduous_tree.obj");
    }

    private static void addFoliageCluster(
            java.util.List<Float> verts,
            float centerX,
            float centerY,
            float centerZ,
            float radiusX,
            float radiusZ,
            float halfHeight,
            int sides,
            float heightT) {
        for (int side = 0; side < sides; side++) {
            float a0 = (float) (side * 2.0 * Math.PI / sides);
            float a1 = (float) ((side + 1) * 2.0 * Math.PI / sides);
            float x0 = (float) Math.cos(a0) * radiusX;
            float z0 = (float) Math.sin(a0) * radiusZ;
            float x1 = (float) Math.cos(a1) * radiusX;
            float z1 = (float) Math.sin(a1) * radiusZ;

            float topNx = (x0 + x1) * 0.5f;
            float topNz = (z0 + z1) * 0.5f;
            float topNy = 1.0f / halfHeight;
            float topNlen = (float) Math.sqrt(topNx * topNx + topNy * topNy + topNz * topNz);
            topNx /= topNlen;
            topNy /= topNlen;
            topNz /= topNlen;

            addVertex(verts, centerX + x0, centerY, centerZ + z0, topNx, topNy, topNz, heightT);
            addVertex(verts, centerX, centerY + halfHeight, centerZ, topNx, topNy, topNz, heightT);
            addVertex(verts, centerX + x1, centerY, centerZ + z1, topNx, topNy, topNz, heightT);

            float bottomNy = -topNy;
            addVertex(
                    verts, centerX, centerY - halfHeight, centerZ, topNx, bottomNy, topNz, heightT);
            addVertex(verts, centerX + x0, centerY, centerZ + z0, topNx, bottomNy, topNz, heightT);
            addVertex(verts, centerX + x1, centerY, centerZ + z1, topNx, bottomNy, topNz, heightT);
        }
    }

    // A single flat-bottomed cone — a solid foliage "layer" with a wide base and a pointed top,
    // unlike the bipyramid used above which tapers to a point on both ends. Stacking these with
    // gaps between one layer's apex and the next layer's base is what reads as a spruce's
    // stepped boughs with bare trunk showing through, instead of a smooth continuous cone.
    private static void addConeTier(
            java.util.List<Float> verts,
            float baseY,
            float apexY,
            float radius,
            int sides,
            float heightT) {
        float height = apexY - baseY;
        for (int side = 0; side < sides; side++) {
            float a0 = (float) (side * 2.0 * Math.PI / sides);
            float a1 = (float) ((side + 1) * 2.0 * Math.PI / sides);
            float x0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;

            float sideNx = (x0 + x1) * 0.5f;
            float sideNz = (z0 + z1) * 0.5f;
            float sideNy = radius / height;
            float sideNlen = (float) Math.sqrt(sideNx * sideNx + sideNy * sideNy + sideNz * sideNz);
            sideNx /= sideNlen;
            sideNy /= sideNlen;
            sideNz /= sideNlen;

            addVertex(verts, x0, baseY, z0, sideNx, sideNy, sideNz, heightT);
            addVertex(verts, 0f, apexY, 0f, sideNx, sideNy, sideNz, heightT);
            addVertex(verts, x1, baseY, z1, sideNx, sideNy, sideNz, heightT);

            addVertex(verts, x0, baseY, z0, 0f, -1f, 0f, heightT);
            addVertex(verts, x1, baseY, z1, 0f, -1f, 0f, heightT);
            addVertex(verts, 0f, baseY, 0f, 0f, -1f, 0f, heightT);
        }
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
            float weightDeciduous) {
        buf[i++] = x;
        buf[i++] = y;
        buf[i++] = z;
        buf[i++] = t;
        buf[i++] = h;
        buf[i++] = cellA;
        buf[i++] = weightTundra;
        buf[i++] = weightSavanna;
        buf[i++] = weightDeciduous;
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

    private void ensureSharedGrassTexture() {
        if (sharedGrassTexture != 0) return;
        sharedGrassTexture = TextureLoader.load("/textures/grass_blade.png");
    }

    private void ensureSharedTreeBuffer() {
        if (sharedTreeBuffer != 0) return;
        sharedTreeBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, sharedTreeBuffer);
        glBufferData(GL_ARRAY_BUFFER, TREE_MESH, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void ensureSharedTreeSavannaBuffer() {
        if (sharedTreeSavannaBuffer != 0) return;
        sharedTreeSavannaBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, sharedTreeSavannaBuffer);
        glBufferData(GL_ARRAY_BUFFER, TREE_SAVANNA_MESH, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void ensureSharedTreeTundraBuffer() {
        if (sharedTreeTundraBuffer != 0) return;
        sharedTreeTundraBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, sharedTreeTundraBuffer);
        glBufferData(GL_ARRAY_BUFFER, TREE_TUNDRA_MESH, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void ensureSharedTreeDeciduousBuffer() {
        if (sharedTreeDeciduousBuffer != 0) return;
        sharedTreeDeciduousBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, sharedTreeDeciduousBuffer);
        glBufferData(GL_ARRAY_BUFFER, TREE_DECIDUOUS_MESH, GL_STATIC_DRAW);
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
        TreeUpload treeSavanna = uploadTreeSavanna(data);
        TreeUpload treeTundra = uploadTreeTundra(data);
        TreeUpload treeDeciduous = uploadTreeDeciduous(data);
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
                treeSavanna.vao(),
                treeSavanna.vbo(),
                treeSavanna.instanceCount(),
                treeTundra.vao(),
                treeTundra.vbo(),
                treeTundra.instanceCount(),
                treeDeciduous.vao(),
                treeDeciduous.vbo(),
                treeDeciduous.instanceCount(),
                rock.vao(),
                rock.vbo(),
                rock.instanceCount(),
                data.vegetationComputed(),
                data.grassComputed());
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
        int bladeMeshStride = BLADE_MESH_FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, bladeMeshStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(3, 3, GL_FLOAT, false, bladeMeshStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(4, 2, GL_FLOAT, false, bladeMeshStride, 6L * Float.BYTES);
        glEnableVertexAttribArray(4);

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

    private TreeUpload uploadTreeSavanna(ChunkMeshData data) {
        if (data.treeNodesSavanna().length == 0) {
            return new TreeUpload(0, 0, 0);
        }

        ensureSharedTreeSavannaBuffer();

        int treeVao = glGenVertexArrays();
        int treeVbo = glGenBuffers();
        glBindVertexArray(treeVao);

        glBindBuffer(GL_ARRAY_BUFFER, sharedTreeSavannaBuffer);
        int treeMeshStride = TREE_MESH_FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, treeMeshStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, treeMeshStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, treeMeshStride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, treeVbo);
        glBufferData(GL_ARRAY_BUFFER, data.treeNodesSavanna(), GL_STATIC_DRAW);
        int treeNodeStride = VEGETATION_NODE_FLOATS * Float.BYTES;
        glVertexAttribPointer(3, 3, GL_FLOAT, false, treeNodeStride, 0L);
        glEnableVertexAttribArray(3);
        glVertexAttribDivisor(3, 1);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, treeNodeStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribDivisor(4, 1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        int nodeCount = data.treeNodesSavanna().length / VEGETATION_NODE_FLOATS;
        return new TreeUpload(treeVao, treeVbo, nodeCount);
    }

    private TreeUpload uploadTreeTundra(ChunkMeshData data) {
        if (data.treeNodesTundra().length == 0) {
            return new TreeUpload(0, 0, 0);
        }

        ensureSharedTreeTundraBuffer();

        int treeVao = glGenVertexArrays();
        int treeVbo = glGenBuffers();
        glBindVertexArray(treeVao);

        glBindBuffer(GL_ARRAY_BUFFER, sharedTreeTundraBuffer);
        int treeMeshStride = TREE_MESH_FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, treeMeshStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, treeMeshStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, treeMeshStride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, treeVbo);
        glBufferData(GL_ARRAY_BUFFER, data.treeNodesTundra(), GL_STATIC_DRAW);
        int treeNodeStride = VEGETATION_NODE_FLOATS * Float.BYTES;
        glVertexAttribPointer(3, 3, GL_FLOAT, false, treeNodeStride, 0L);
        glEnableVertexAttribArray(3);
        glVertexAttribDivisor(3, 1);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, treeNodeStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribDivisor(4, 1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        int nodeCount = data.treeNodesTundra().length / VEGETATION_NODE_FLOATS;
        return new TreeUpload(treeVao, treeVbo, nodeCount);
    }

    private TreeUpload uploadTreeDeciduous(ChunkMeshData data) {
        if (data.treeNodesDeciduous().length == 0) {
            return new TreeUpload(0, 0, 0);
        }

        ensureSharedTreeDeciduousBuffer();

        int treeVao = glGenVertexArrays();
        int treeVbo = glGenBuffers();
        glBindVertexArray(treeVao);

        glBindBuffer(GL_ARRAY_BUFFER, sharedTreeDeciduousBuffer);
        int treeMeshStride = TREE_MESH_FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, treeMeshStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, treeMeshStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, treeMeshStride, 6L * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, treeVbo);
        glBufferData(GL_ARRAY_BUFFER, data.treeNodesDeciduous(), GL_STATIC_DRAW);
        int treeNodeStride = VEGETATION_NODE_FLOATS * Float.BYTES;
        glVertexAttribPointer(3, 3, GL_FLOAT, false, treeNodeStride, 0L);
        glEnableVertexAttribArray(3);
        glVertexAttribDivisor(3, 1);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, treeNodeStride, 3L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribDivisor(4, 1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        int nodeCount = data.treeNodesDeciduous().length / VEGETATION_NODE_FLOATS;
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
            int treeSavannaVaoId,
            int treeSavannaVboId,
            int treeSavannaInstanceCount,
            int treeTundraVaoId,
            int treeTundraVboId,
            int treeTundraInstanceCount,
            int treeDeciduousVaoId,
            int treeDeciduousVboId,
            int treeDeciduousInstanceCount,
            int rockVaoId,
            int rockVboId,
            int rockInstanceCount,
            boolean vegetationComputed,
            boolean grassComputed) {
        void draw() {
            glBindVertexArray(vaoId);
            glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
            glBindVertexArray(0);
        }

        void drawGrass() {
            if (grassInstanceCount == 0) return;
            glDisable(GL_CULL_FACE);
            glBindVertexArray(grassVaoId);
            glDrawArraysInstanced(GL_TRIANGLES, 0, BLADE_VERTEX_COUNT, grassInstanceCount);
            glBindVertexArray(0);
            glEnable(GL_CULL_FACE);
        }

        void drawTree() {
            if (treeInstanceCount == 0) return;
            glDisable(GL_CULL_FACE);
            glBindVertexArray(treeVaoId);
            glDrawArraysInstanced(GL_TRIANGLES, 0, TREE_VERTEX_COUNT, treeInstanceCount);
            glBindVertexArray(0);
            glEnable(GL_CULL_FACE);
        }

        void drawTreeSavanna() {
            if (treeSavannaInstanceCount == 0) return;
            glDisable(GL_CULL_FACE);
            glBindVertexArray(treeSavannaVaoId);
            glDrawArraysInstanced(
                    GL_TRIANGLES, 0, TREE_SAVANNA_VERTEX_COUNT, treeSavannaInstanceCount);
            glBindVertexArray(0);
            glEnable(GL_CULL_FACE);
        }

        void drawTreeTundra() {
            if (treeTundraInstanceCount == 0) return;
            glDisable(GL_CULL_FACE);
            glBindVertexArray(treeTundraVaoId);
            glDrawArraysInstanced(
                    GL_TRIANGLES, 0, TREE_TUNDRA_VERTEX_COUNT, treeTundraInstanceCount);
            glBindVertexArray(0);
            glEnable(GL_CULL_FACE);
        }

        void drawTreeDeciduous() {
            if (treeDeciduousInstanceCount == 0) return;
            glDisable(GL_CULL_FACE);
            glBindVertexArray(treeDeciduousVaoId);
            glDrawArraysInstanced(
                    GL_TRIANGLES, 0, TREE_DECIDUOUS_VERTEX_COUNT, treeDeciduousInstanceCount);
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
            if (treeSavannaVaoId != 0) glDeleteVertexArrays(treeSavannaVaoId);
            if (treeSavannaVboId != 0) glDeleteBuffers(treeSavannaVboId);
            if (treeTundraVaoId != 0) glDeleteVertexArrays(treeTundraVaoId);
            if (treeTundraVboId != 0) glDeleteBuffers(treeTundraVboId);
            if (treeDeciduousVaoId != 0) glDeleteVertexArrays(treeDeciduousVaoId);
            if (treeDeciduousVboId != 0) glDeleteBuffers(treeDeciduousVboId);
            if (rockVaoId != 0) glDeleteVertexArrays(rockVaoId);
            if (rockVboId != 0) glDeleteBuffers(rockVboId);
        }
    }
}
