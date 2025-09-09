package dev.ptxy.engine_demos;

import dev.ptxy.engine.camera.SimpleCamera2D;
import dev.ptxy.engine.objects.primitivs.filled.*;
import dev.ptxy.engine.objects.primitivs.unfilled.*;

public final class SceneRenderer {
    private SimpleCamera2D camera = new SimpleCamera2D();

    public void renderScene() {
        new Triangle().moveTo2D(0,0).scale2D(100,100).render(camera);
        new Square().moveTo2D(105,0).scale2D(100,100).render(camera);
        new Circle().moveTo2D(210,0).scale2D(100,100).render(camera);
        new Line().moveTo2D(0,105).scale2D(1000,1).render(camera);
        new OutlinedTriangle().moveTo2D(0,110).scale2D(100,100).render(camera);
        new OutlinedSquare().moveTo2D(105,110).scale2D(100,100).render(camera);
        new OutlinedCircle().moveTo2D(210,110).scale2D(100,100).render(camera);
    }
}
