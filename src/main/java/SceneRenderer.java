import objects.primitivs.Circle;
import objects.primitivs.Square;
import objects.primitivs.Triangle;
import org.joml.Matrix4f;

public final class SceneRenderer {
    public static void renderScene(Matrix4f projection) {
        new Triangle().render(projection);
        new Square().render(projection);
        new Circle().render(projection);
    }
}
