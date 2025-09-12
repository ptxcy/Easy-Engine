package dev.ptxy.engine.objects.texture;
import dev.ptxy.engine.objects.core.TexturedGameObject;
import org.joml.Vector2f;

public final class TexturedSquare extends TexturedGameObject {
    private static final Vector2f[] baseUVs = new Vector2f[] {
            new Vector2f(0.0f, 1.0f),
            new Vector2f(0.0f, 0.0f),
            new Vector2f(1.0f, 0.0f),

            new Vector2f(0.0f, 1.0f),
            new Vector2f(1.0f, 1.0f),
            new Vector2f(1.0f, 0.0f),
    };

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

    private Texture texture;

    public TexturedSquare(Texture tex){
        texture = tex;
        this.init();
    }

    @Override
    protected Vector2f[] setBaseCornerPoints() {
        return basePoints;
    }

    @Override
    protected Texture setTexture() {
        return texture;
    }

    @Override
    protected Vector2f[] setTextureCords() {
        return baseUVs;
    }
}
