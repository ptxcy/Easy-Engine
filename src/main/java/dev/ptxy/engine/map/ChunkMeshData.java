package dev.ptxy.engine.map;

import java.util.Arrays;
import java.util.Objects;

record ChunkMeshData(ChunkPos pos, float[] vertices, int generation) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkMeshData(ChunkPos pos1, float[] vertices1, int generation1)))
            return false;
        return generation == generation1
                && Objects.equals(pos, pos1)
                && Arrays.equals(vertices, vertices1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos, Arrays.hashCode(vertices), generation);
    }

    @Override
    public String toString() {
        return "ChunkMeshData[pos="
                + pos
                + ", vertices="
                + Arrays.toString(vertices)
                + ", generation="
                + generation
                + "]";
    }
}
