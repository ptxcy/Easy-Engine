package dev.ptxy.engine_demos;

import dev.ptxy.engine.Core;

public final class Launcher {
    public static void main(String[] args) {
        new Core().run(new SceneRenderer());
    }
}
