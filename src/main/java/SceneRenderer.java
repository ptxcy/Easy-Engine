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
    public static void renderScene(Matrix4f projection) {
        new Triangle().moveTo2D(0,0).scale2D(100,100).render(projection, camera.getViewMatrix());
        new Square().moveTo2D(105,0).scale2D(100,100).render(projection, camera.getViewMatrix());
        new Circle().moveTo2D(210,0).scale2D(100,100).render(projection, camera.getViewMatrix());
        new Line().moveTo2D(0,105).scale2D(1000,1).render(projection, camera.getViewMatrix());
        new OutlinedTriangle().moveTo2D(0,110).scale2D(100,100).render(projection, camera.getViewMatrix());
        new OutlinedSquare().moveTo2D(105,110).scale2D(100,100).render(projection, camera.getViewMatrix());
        new OutlinedCircle().moveTo2D(210,110).scale2D(100,100).render(projection, camera.getViewMatrix());
    }
}
