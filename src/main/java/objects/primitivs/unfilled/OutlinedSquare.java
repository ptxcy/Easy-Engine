package objects.primitivs.unfilled;

import objects.GameObject;
import org.joml.Vector2f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.GL_LINES;

public final class OutlinedSquare extends GameObject {
    private static final Vector2f[] basePoints = new Vector2f[]{
            new Vector2f(0.0f, 1.0f),
            new Vector2f(0.0f, 0.0f),

            new Vector2f(0.0f, 0.0f),
            new Vector2f(1.0f, 0.0f),

            new Vector2f(1.0f, 0.0f),
            new Vector2f(1.0f, 1.0f),

            new Vector2f(1.0f, 1.0f),
            new Vector2f(0.0f, 1.0f),
    };

    public OutlinedSquare() {
        overwriteDrawMode();
    }

    private void overwriteDrawMode() {
        drawMode = GL_LINES;
    }

    @Override
    protected Vector2f[] setBaseCornerPoints() {
        return basePoints;
    }

    @Override
    protected boolean shouldStaticColorBeUsed() {
        return true;
    }

    @Override
    protected Vector4f setStaticColor() {
        return new Vector4f(1.0f, 0.0f, 0.0f, 1.0f);
    }
}
