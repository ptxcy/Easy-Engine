package dev.ptxy.engine.objects.primitivs.unfilled;

import dev.ptxy.engine.objects.core.PrimitiveGameObject;
import dev.ptxy.engine.objects.texture.Texture;
import org.joml.Vector2f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.GL_LINES;

public final class Line extends PrimitiveGameObject {
    private static final Vector2f[] basePoints = new Vector2f[]{
            new Vector2f(0.0f, 0.0f),
            new Vector2f(1.0f, 1.0f)
    };

    public Line(Vector4f color) {
        overwriteDrawMode();
        this.staticColor = color;
        this.init();
    }

    private void overwriteDrawMode() {
        drawMode = GL_LINES;
    }

    @Override
    protected Vector2f[] setBaseCornerPoints() {
        return basePoints;
    }

    @Override
    protected Vector4f setStaticColor() {
        return staticColor;
    }
}
