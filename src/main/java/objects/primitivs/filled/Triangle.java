package objects.primitivs.filled;

import objects.GameObject;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class Triangle extends GameObject {
    private void setBaseSettings() {
        this.model.scale(100, 100, 1);
    }

    public Triangle() {
        setBaseSettings();
    }

    private static final Vector2f[] basePoints = new Vector2f[]{
            new Vector2f(0.0f, 0.0f),
            new Vector2f(1.0f, 0.0f),
            new Vector2f(1.0f, 1.0f)
    };

    @Override
    protected  Vector2f[] setBaseCornerPoints() {
        return basePoints;
    }

    @Override
    protected boolean shouldStaticColorBeUsed() {
        return true;
    }

    @Override
    protected Vector4f setStaticColor() {
        return new Vector4f(0.0f, 0.0f, 1.0f, 1.0f);
    }
}

