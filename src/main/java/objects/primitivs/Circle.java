package objects.primitivs;

import objects.GameObject;
import org.joml.Vector2f;
import org.joml.Vector4f;

public final class Circle extends GameObject {
    private static final float DETAIL_COUNT = 120.0f;

    @Override
    protected Vector2f[] setBaseCornerPoints() {
        int segments = (int) DETAIL_COUNT;
        Vector2f[] points = new Vector2f[segments * 3];
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

            points[i * 3] = center;
            points[i * 3 + 1] = p1;
            points[i * 3 + 2] = p2;
        }

        return points;
    }


    @Override
    protected boolean shouldStaticColorBeUsed() {
        return true;
    }

    @Override
    protected Vector4f setStaticColor() {
        return new Vector4f(0.0f, 1.0f, 0.0f, 1.0f);
    }

    public void setBasics() {
        model.identity()
                .translate(250.0f, 0.0f, 0.0f)
                .scale(100.0f, 100.0f, 1.0f);
    }

    public Circle() {
        setBasics();
    }
}
