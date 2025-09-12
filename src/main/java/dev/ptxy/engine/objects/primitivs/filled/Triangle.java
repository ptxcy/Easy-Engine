package dev.ptxy.engine.objects.primitivs.filled;

import dev.ptxy.engine.objects.core.PrimitiveGameObject;
import dev.ptxy.engine.objects.texture.Texture;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class Triangle extends PrimitiveGameObject {

    private static final Vector2f[] basePoints = new Vector2f[]{
            new Vector2f(0.0f, 0.0f),
            new Vector2f(1.0f, 0.0f),
            new Vector2f(1.0f, 1.0f)
    };

    @Override
    protected Vector2f[] setBaseCornerPoints() {
        return basePoints;
    }

    @Override
    protected Vector4f setStaticColor() {
        return new Vector4f(0.0f, 0.0f, 1.0f, 1.0f);
    }

    public Triangle(){
        this.init();
    }
}

