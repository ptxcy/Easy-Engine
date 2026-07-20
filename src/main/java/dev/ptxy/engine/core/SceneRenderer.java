package dev.ptxy.engine.core;

public interface SceneRenderer {
    void renderScene(float deltaTime);

    default void shutdown() {}
}
