package dev.ptxy.engine.objects.primitivs;

import dev.ptxy.engine.objects.core.Renderable;
import org.joml.Matrix4f;
import org.joml.Vector2f;

import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

public abstract class GameObject implements Renderable {
    protected boolean isInitialized = false;
    protected int drawMode = GL_TRIANGLES;
    protected int vaoId;
    protected int vboId;
    protected Vector2f[] cornerPoints;
    protected Matrix4f model = new Matrix4f().identity();

    protected abstract Vector2f[] setBaseCornerPoints();

    protected int instanceVao() {
        return glGenVertexArrays();
    }

    protected int instanceVbo() {
        FloatBuffer vertexBuffer = memAllocFloat(cornerPoints.length * 2);
        for (Vector2f v : cornerPoints) {
            vertexBuffer.put(v.x)
                .put(v.y);
        }
        vertexBuffer.flip();

        int vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        memFree(vertexBuffer);

        return vboId;
    }

    public GameObject moveTo2D(float x, float y) {
        this.model.identity()
            .translate(x, y, 1);
        return this;
    }

    public GameObject scale2D(float x, float y) {
        this.model.scale(x, y, 1);
        return this;
    }
}
