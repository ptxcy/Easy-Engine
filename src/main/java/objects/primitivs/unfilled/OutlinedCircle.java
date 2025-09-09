package objects.primitivs.unfilled;

import objects.GameObject;
import org.joml.Vector2f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.GL_LINES;

public class OutlinedCircle extends GameObject {
    private static final int DETAIL_COUNT = 120;
    private static final Vector2f[] basePoints;

    static {
        basePoints = new Vector2f[DETAIL_COUNT * 2];  // je 2 Punkte pro Linie

        for (int i = 0; i < DETAIL_COUNT; i++) {
            float angle1 = (float) (2 * Math.PI * i / DETAIL_COUNT);
            float angle2 = (float) (2 * Math.PI * (i + 1) / DETAIL_COUNT);

            Vector2f p1 = new Vector2f(
                    0.5f + 0.5f * (float) Math.cos(angle1),
                    0.5f + 0.5f * (float) Math.sin(angle1)
            );
            Vector2f p2 = new Vector2f(
                    0.5f + 0.5f * (float) Math.cos(angle2),
                    0.5f + 0.5f * (float) Math.sin(angle2)
            );

            basePoints[i * 2] = p1;
            basePoints[i * 2 + 1] = p2;
        }
    }

    public OutlinedCircle() {
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
        return new Vector4f(0.0f, 1.0f, 0.0f, 1.0f);
    }
}
