package dev.ptxy.engine.map;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

final class UploadQueue {
    private final ConcurrentLinkedQueue<ChunkMeshData> queue = new ConcurrentLinkedQueue<>();

    void enqueue(ChunkMeshData data) {
        queue.offer(data);
    }

    void drainTo(Consumer<ChunkMeshData> handler, int maxItems) {
        ChunkMeshData data;
        int drained = 0;
        while (drained < maxItems && (data = queue.poll()) != null) {
            handler.accept(data);
            drained++;
        }
    }

    void clear() {
        queue.clear();
    }
}
