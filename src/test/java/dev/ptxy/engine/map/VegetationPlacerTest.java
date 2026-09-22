package dev.ptxy.engine.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VegetationPlacerTest {

    @Test
    void placeProducesGrassNodesWithinChunkBoundsInDeciduousForest() {
        Map noiseMap = new Map(42L);
        int chunkSize = 64;

        ChunkPos deciduousChunk = null;
        for (int cx = -30; cx <= 30 && deciduousChunk == null; cx++) {
            for (int cz = -30; cz <= 30 && deciduousChunk == null; cz++) {
                float wx = cx * chunkSize + chunkSize / 2f;
                float wz = cz * chunkSize + chunkSize / 2f;
                if (noiseMap.getBiome(wx, wz) == Biome.DECIDUOUS_FOREST) {
                    deciduousChunk = new ChunkPos(cx, cz);
                }
            }
        }
        assertNotNull(deciduousChunk, "kein Laubwald-Chunk im abgesuchten Bereich gefunden");

        float[] nodes =
                VegetationPlacer.place(deciduousChunk, noiseMap, chunkSize, 70.0f, "grass", true);
        assertTrue(nodes.length > 0, "keine Gras-Knoten im Laubwald-Chunk platziert");

        float minX = deciduousChunk.x() * chunkSize;
        float minZ = deciduousChunk.z() * chunkSize;
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
