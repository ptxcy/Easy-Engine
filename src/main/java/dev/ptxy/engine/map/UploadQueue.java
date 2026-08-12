package dev.ptxy.engine.map;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

final class UploadQueue {
    private final ConcurrentLinkedQueue<ChunkMeshData> queue = new ConcurrentLinkedQueue<>();

    void enqueue(ChunkMeshData data) {
        queue.offer(data);
    }

    void drainTo(Consumer<ChunkMeshData> handler) {
        ChunkMeshData data;
        while ((data = queue.poll()) != null) {
            handler.accept(data);
        }
    }

    void clear() {
        queue.clear();
    }
}
