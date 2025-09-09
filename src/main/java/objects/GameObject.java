package objects;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import shader.ShaderCompiler;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

public abstract class GameObject implements Renderable {
    protected int drawMode = GL_TRIANGLES;
    protected int vaoId;
    protected int vboId;

    protected Vector2f[] cornerPoints;
    protected Matrix4f model = new Matrix4f().identity();

    protected boolean useStaticColor = false;
    protected Vector4f staticColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    protected abstract Vector2f[] setBaseCornerPoints();

    protected abstract boolean shouldStaticColorBeUsed();

    protected abstract Vector4f setStaticColor();

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

    private void init() {
        cornerPoints = setBaseCornerPoints();
        useStaticColor = shouldStaticColorBeUsed();
        staticColor = setStaticColor();
        vaoId = instanceVao();
        glBindVertexArray(vaoId);
        vboId = instanceVbo();
        glBindVertexArray(0);
    }

    protected GameObject() {
        init();
    }

    private void checkGLError(String stage) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.printf("OpenGL ERROR [%s]: 0x%X\n", stage, error);
        }
    }

    @Override
    public void render(Matrix4f projection, Matrix4f viewMatrix) {
        // Shader aktivieren
        glUseProgram(ShaderCompiler.shaderProgramId);
        checkGLError("glUseProgram");

        // VAO binden
        glBindVertexArray(vaoId);
        checkGLError("glBindVertexArray");

        // Farbe setzen
        if (useStaticColor) {
            int colorLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "staticInputColor");
            if (colorLocation != -1) {
                glUniform4f(colorLocation, staticColor.x, staticColor.y, staticColor.z, staticColor.w);
                checkGLError("glUniform4f (staticInputColor)");
            } else {
                System.err.println("WARN: staticInputColor uniform not found.");
            }
        }

        // Projection setzen
        int projectionLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "projection");
        if (projectionLocation != -1) {
            FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
            projection.get(projBuffer);
            glUniformMatrix4fv(projectionLocation, false, projBuffer);
            checkGLError("glUniformMatrix4fv (projection)");
        } else {
            System.err.println("WARN: projection uniform not found.");
        }

        // Model setzen
        int modelLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "model");
        if (modelLocation != -1) {
            FloatBuffer modelBuffer = BufferUtils.createFloatBuffer(16);
            model.get(modelBuffer);
            glUniformMatrix4fv(modelLocation, false, modelBuffer);
            checkGLError("glUniformMatrix4fv (model)");
        } else {
            System.err.println("WARN: model uniform not found.");
        }

        // View setzen
        if (viewMatrix != null) {
            int viewMatrixLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "view");
            if (viewMatrixLocation != -1) {
                FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
                model.get(viewBuffer);
                glUniformMatrix4fv(viewMatrixLocation, false, viewBuffer);
                checkGLError("glUniformMatrix4fv (view)");
            } else {
                System.err.println("WARN: view uniform not found.");
            }
        }

        // Zeichnen
        glDrawArrays(drawMode, 0, cornerPoints.length);
        checkGLError("glDrawArrays");

        // VAO unbinden
        glBindVertexArray(0);
        glUseProgram(0);
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
