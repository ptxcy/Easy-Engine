import camera.SimpleCamera;
import objects.primitivs.filled.Circle;
import objects.primitivs.filled.Square;
import objects.primitivs.filled.Triangle;
import objects.primitivs.unfilled.Line;
import objects.primitivs.unfilled.OutlinedCircle;
import objects.primitivs.unfilled.OutlinedSquare;
import objects.primitivs.unfilled.OutlinedTriangle;
import org.joml.Matrix4f;

public final class SceneRenderer {
    private static SimpleCamera camera = new SimpleCamera();
    public static void renderScene() {
        new Triangle().moveTo2D(0,0).scale2D(100,100).render(camera);
        new Square().moveTo2D(105,0).scale2D(100,100).render(camera);
        new Circle().moveTo2D(210,0).scale2D(100,100).render(camera);
        new Line().moveTo2D(0,105).scale2D(1000,1).render(camera);
        new OutlinedTriangle().moveTo2D(0,110).scale2D(100,100).render(camera);
        new OutlinedSquare().moveTo2D(105,110).scale2D(100,100).render(camera);
        new OutlinedCircle().moveTo2D(210,110).scale2D(100,100).render(camera);
    }
}
