package dev.ptxy.engine.objects.textured;
import dev.ptxy.engine.objects.primitivs.GameObject;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class TexturedSquare extends GameObject {
    private static final Vector2f[] basePoints = new Vector2f[]{
            //First Triangle
            new Vector2f(0.0f, 1.0f),
            new Vector2f(0.0f, 0.0f),
            new Vector2f(1.0f, 0.0f),
            //Second Triangle
            new Vector2f(0.0f, 1.0f),
            new Vector2f(1.0f, 1.0f),
            new Vector2f(1.0f, 0.0f)
    };

    @Override
    protected Vector2f[] setBaseCornerPoints() {
        return basePoints;
    }

    @Override
    protected ColorMode setColorMode() {
        return ColorMode.SPRITE;
    }

    @Override
    protected Vector4f setStaticColor() {
        return new Vector4f(1.0f, 0.0f, 0.0f, 1.0f);
    }
}
