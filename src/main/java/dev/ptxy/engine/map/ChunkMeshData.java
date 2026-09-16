package dev.ptxy.engine.map;

import java.util.Arrays;
import java.util.Objects;

record ChunkMeshData(
        ChunkPos pos,
        float[] vertices,
        float[] grassNodes,
        float[] treeNodes,
        float[] rockNodes,
        boolean vegetationComputed,
        int generation) {

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
                        float[] rockNodes1,
                        boolean vegetationComputed1,
                        int generation1))) return false;
        return generation == generation1
                && vegetationComputed == vegetationComputed1
                && Objects.equals(pos, pos1)
                && Arrays.equals(vertices, vertices1)
                && Arrays.equals(grassNodes, grassNodes1)
                && Arrays.equals(treeNodes, treeNodes1)
                && Arrays.equals(rockNodes, rockNodes1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                pos,
                Arrays.hashCode(vertices),
                Arrays.hashCode(grassNodes),
                Arrays.hashCode(treeNodes),
                Arrays.hashCode(rockNodes),
                vegetationComputed,
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
                + ", rockNodes="
                + Arrays.toString(rockNodes)
                + ", vegetationComputed="
                + vegetationComputed
                + ", generation="
                + generation
                + "]";
    }
}
