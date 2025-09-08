import objects.primitivs.filled.Circle;
import objects.primitivs.filled.Square;
import objects.primitivs.filled.Triangle;
import objects.primitivs.unfilled.Line;
import objects.primitivs.unfilled.OutlinedCircle;
import objects.primitivs.unfilled.OutlinedSquare;
import objects.primitivs.unfilled.OutlinedTriangle;
import org.joml.Matrix4f;

public final class SceneRenderer {
    public static void renderScene(Matrix4f projection) {
        new Triangle().render(projection);
        new Square().render(projection);
        new Circle().render(projection);
        new Line().render(projection);
        new OutlinedTriangle().render(projection);
        new OutlinedSquare().render(projection);
        new OutlinedCircle().render(projection);
    }
}
