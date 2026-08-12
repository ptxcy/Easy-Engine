package dev.ptxy.engine.core;

public interface SceneRenderer {
    void renderScene(float deltaTime);

    // Bracketing-Hooks um Cores glfwPollEvents() -- Nuklear (oder ein anderes GLFW-Callback-
    // basiertes Input-System) muss nk_input_begin/nk_input_end exakt um den tatsächlichen
    // Poll-Aufruf legen, damit die während des Pollens feuernden Callbacks korrekt erfasst werden.
    default void beforePollEvents() {}

    default void afterPollEvents() {}

    // Einmal pro Sekunde mit der tatsächlich gerenderten Framezahl aufgerufen, statt sie zu loggen
    // -- Aufrufer kann sie z.B. in einem In-Game-Overlay anzeigen.
    default void onFps(int fps) {}

    default void shutdown() {}
}
