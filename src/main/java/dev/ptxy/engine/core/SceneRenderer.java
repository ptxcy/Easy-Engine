package dev.ptxy.engine.core;

public interface SceneRenderer {
    void renderScene();

    default void shutdown() {}
}
