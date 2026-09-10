package dev.ptxy.engine.map;

import java.util.Arrays;
import java.util.Objects;

record ChunkMeshData(
        ChunkPos pos, float[] vertices, float[] grassNodes, float[] treeNodes, int generation) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o
                instanceof
                ChunkMeshData(
                        ChunkPos pos1,
                        float[] vertices1,
                        float[] grassNodes1,
                        float[] treeNodes1,
                        int generation1))) return false;
        return generation == generation1
                && Objects.equals(pos, pos1)
                && Arrays.equals(vertices, vertices1)
                && Arrays.equals(grassNodes, grassNodes1)
                && Arrays.equals(treeNodes, treeNodes1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                pos,
                Arrays.hashCode(vertices),
                Arrays.hashCode(grassNodes),
                Arrays.hashCode(treeNodes),
                generation);
    }

    @Override
    public String toString() {
        return "ChunkMeshData[pos="
                + pos
                + ", vertices="
                + Arrays.toString(vertices)
                + ", grassNodes="
                + Arrays.toString(grassNodes)
                + ", treeNodes="
                + Arrays.toString(treeNodes)
                + ", generation="
                + generation
                + "]";
    }
}
