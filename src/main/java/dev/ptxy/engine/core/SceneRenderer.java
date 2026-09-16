package dev.ptxy.engine.core;

public interface SceneRenderer {
    void renderScene(float deltaTime);

    default void beforePollEvents() {}

    default void afterPollEvents() {}

    default void onFps(int fps) {}

    default void shutdown() {}
}
