package dev.ptxy.engine.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VegetationPlacerTest {

    @Test
    void placeProducesGrassNodesWithinChunkBoundsInRainforest() {
        Map noiseMap = new Map(42L);
        int chunkSize = 64;

        ChunkPos rainforestChunk = null;
        for (int cx = -30; cx <= 30 && rainforestChunk == null; cx++) {
            for (int cz = -30; cz <= 30 && rainforestChunk == null; cz++) {
                float wx = cx * chunkSize + chunkSize / 2f;
                float wz = cz * chunkSize + chunkSize / 2f;
                if (noiseMap.getBiome(wx, wz) == Biome.RAINFOREST) {
                    rainforestChunk = new ChunkPos(cx, cz);
                }
            }
        }
        assertNotNull(rainforestChunk, "kein Regenwald-Chunk im abgesuchten Bereich gefunden");

        float[] nodes =
                VegetationPlacer.place(rainforestChunk, noiseMap, chunkSize, 70.0f, "grass", true);
        assertTrue(nodes.length > 0, "keine Gras-Knoten im Regenwald-Chunk platziert");

        float minX = rainforestChunk.x() * chunkSize;
        float minZ = rainforestChunk.z() * chunkSize;
        for (int i = 0; i < nodes.length; i += 4) {
            assertTrue(
                    nodes[i] >= minX && nodes[i] < minX + chunkSize,
                    "x-Koordinate außerhalb des Chunks: " + nodes[i]);
            assertTrue(
                    nodes[i + 2] >= minZ && nodes[i + 2] < minZ + chunkSize,
                    "z-Koordinate außerhalb des Chunks: " + nodes[i + 2]);
        }
    }

    @Test
    void placeIsDeterministicForSamePosition() {
        Map noiseMap = new Map(42L);
        ChunkPos pos = new ChunkPos(3, -2);

        float[] first = VegetationPlacer.place(pos, noiseMap, 64, 70.0f, "grass", true);
        float[] second = VegetationPlacer.place(pos, noiseMap, 64, 70.0f, "grass", true);

        assertArrayEquals(first, second);
    }

    @Test
    void placeReturnsBufferSizedInWholeNodes() {
        Map noiseMap = new Map(7L);
        float[] nodes =
                VegetationPlacer.place(new ChunkPos(0, 0), noiseMap, 64, 70.0f, "grass", true);

        assertEquals(0, nodes.length % 4, "buffer length must be a multiple of FLOATS_PER_NODE");
    }

    @Test
    void placeThrowsForUnknownVegetationType() {
        Map noiseMap = new Map(1L);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VegetationPlacer.place(
                                new ChunkPos(0, 0), noiseMap, 64, 70.0f, "unknown-type", true));
    }
}
