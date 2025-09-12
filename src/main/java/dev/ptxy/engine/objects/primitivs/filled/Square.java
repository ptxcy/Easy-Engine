package dev.ptxy.engine.objects.primitivs.filled;

import dev.ptxy.engine.objects.core.PrimitiveGameObject;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class Square extends PrimitiveGameObject {
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
    protected Vector4f setStaticColor() {
        return staticColor;
    }

    public Square(Vector4f color){
        this.staticColor = color;
        this.init();
    }
}
