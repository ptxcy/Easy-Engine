package dev.ptxy.engine.map;

import java.util.Arrays;
import java.util.Objects;

record ChunkMeshData(
        ChunkPos pos,
        float[] vertices,
        float[] grassNodes,
        float[] treeNodes,
        float[] treeNodesSavanna,
        float[] treeNodesTundra,
        float[] treeNodesDeciduous,
        float[] rockNodes,
        boolean vegetationComputed,
        boolean grassComputed,
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
                        float[] treeNodesSavanna1,
                        float[] treeNodesTundra1,
                        float[] treeNodesDeciduous1,
                        float[] rockNodes1,
                        boolean vegetationComputed1,
                        boolean grassComputed1,
                        int generation1))) return false;
        return generation == generation1
                && vegetationComputed == vegetationComputed1
                && grassComputed == grassComputed1
                && Objects.equals(pos, pos1)
                && Arrays.equals(vertices, vertices1)
                && Arrays.equals(grassNodes, grassNodes1)
                && Arrays.equals(treeNodes, treeNodes1)
                && Arrays.equals(treeNodesSavanna, treeNodesSavanna1)
                && Arrays.equals(treeNodesTundra, treeNodesTundra1)
                && Arrays.equals(treeNodesDeciduous, treeNodesDeciduous1)
                && Arrays.equals(rockNodes, rockNodes1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                pos,
                Arrays.hashCode(vertices),
                Arrays.hashCode(grassNodes),
                Arrays.hashCode(treeNodes),
                Arrays.hashCode(treeNodesSavanna),
                Arrays.hashCode(treeNodesTundra),
                Arrays.hashCode(treeNodesDeciduous),
                Arrays.hashCode(rockNodes),
                vegetationComputed,
                grassComputed,
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
                + ", treeNodesSavanna="
                + Arrays.toString(treeNodesSavanna)
                + ", treeNodesTundra="
                + Arrays.toString(treeNodesTundra)
                + ", treeNodesDeciduous="
                + Arrays.toString(treeNodesDeciduous)
                + ", rockNodes="
                + Arrays.toString(rockNodes)
                + ", vegetationComputed="
                + vegetationComputed
                + ", grassComputed="
                + grassComputed
                + ", generation="
                + generation
                + "]";
    }
}
