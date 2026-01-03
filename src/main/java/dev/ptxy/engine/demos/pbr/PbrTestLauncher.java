package dev.ptxy.engine.demos.pbr;

import dev.ptxy.engine.core.Core;
import dev.ptxy.engine.core.SceneRenderer;

public class PbrTestLauncher implements SceneRenderer {
    @Override
    public void renderScene() {
        
    }

    public static void main(String[] args) {
        new Core().run(new PbrTestLauncher());
    }
}
