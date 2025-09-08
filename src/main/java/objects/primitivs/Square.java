package objects.primitivs;

import objects.GameObject;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class Square extends GameObject {
    @Override
    protected Vector2f[] setBaseCornerPoints() {
        return new Vector2f[]{
                //First Triangle
                new Vector2f(0.0f, 1.0f),
                new Vector2f(0.0f, 0.0f),
                new Vector2f(1.0f, 0.0f),
                //Second Triangle
                new Vector2f(0.0f, 1.0f),
                new Vector2f(1.0f, 1.0f),
                new Vector2f(1.0f, 0.0f)
        };
    }

    @Override
    protected boolean shouldStaticColorBeUsed() {
        return true;
    }

    @Override
    protected Vector4f setStaticColor() {
        return new Vector4f(1.0f, 0.0f, 0.0f, 1.0f);
    }

    public void setBasics() {
        model.translate(125.0f, 0.0f, 0.0f);
        model.scale(100, 100, 100);
    }

    public Square() {
        setBasics();
    }
}
