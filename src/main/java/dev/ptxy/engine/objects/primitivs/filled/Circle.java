package dev.ptxy.engine.objects.primitivs.filled;

import dev.ptxy.engine.objects.core.PrimitiveGameObject;
import dev.ptxy.engine.objects.texture.Texture;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class Circle extends PrimitiveGameObject {
    private static final float DETAIL_COUNT = 120.0f;

    private static final Vector2f[] basePoints = new Vector2f[(int) DETAIL_COUNT * 3];
    static {
        int segments = (int) DETAIL_COUNT;
        Vector2f center = new Vector2f(0.5f, 0.5f);

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) ((2 * Math.PI * i) / segments);
            float angle2 = (float) ((2 * Math.PI * (i + 1)) / segments);

            Vector2f p1 = new Vector2f(
                    0.5f + 0.5f * (float) Math.cos(angle1),
                    0.5f + 0.5f * (float) Math.sin(angle1)
            );

            Vector2f p2 = new Vector2f(
                    0.5f + 0.5f * (float) Math.cos(angle2),
                    0.5f + 0.5f * (float) Math.sin(angle2)
            );

            basePoints[i * 3] = center;
            basePoints[i * 3 + 1] = p1;
            basePoints[i * 3 + 2] = p2;
        }
    }

    @Override
    protected Vector2f[] setBaseCornerPoints() {
        return basePoints;
    }

    @Override
    protected Vector4f setStaticColor() {
        return new Vector4f(0.0f, 1.0f, 0.0f, 1.0f);
    }

    public Circle(){
        this.init();
    }
}
