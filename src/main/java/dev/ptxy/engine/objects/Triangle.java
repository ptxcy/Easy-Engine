package dev.ptxy.engine.objects;

import dev.ptxy.engine.camera.Camera;
import dev.ptxy.engine.core.RenderAppearence;
import dev.ptxy.engine.shader.ShaderCompiler;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL30.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL30.GL_FLOAT;
import static org.lwjgl.opengl.GL30.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL30.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glBufferData;
import static org.lwjgl.opengl.GL30.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.glGenBuffers;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.glGetUniformLocation;
import static org.lwjgl.opengl.GL30.glUniform1i;
import static org.lwjgl.opengl.GL30.glUniform4f;
import static org.lwjgl.opengl.GL30.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL30.glUseProgram;
import static org.lwjgl.opengl.GL30.glVertexAttribPointer;

public class Triangle {
    //Array That Describes The Form of The Triangle
    private final Vector2f[] uvCoords = new Vector2f[] {new Vector2f(0f, 0f), new Vector2f(0f, 0f), new Vector2f(0f, 0f)};
    //Array That Describes The Normals Of each Vertex in formData
    private final Vector3f[] normals = new Vector3f[3];
    //Array That Describes The Form of The Triangle
    private final Vector3f[] formData = new Vector3f[3];

    //Value That Describes The Color Of The Triangle (Only used if appearence is set to STATIC)
    private final Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);

    //Value That Describes The appearance Of The Triangle Either static colored or textured
    private RenderAppearence appearance;
    //Matrix That Describes The Transformation Of The Triangle
    private Matrix4f model = new Matrix4f();

    //vao identifier
    private int vaoId;

    private Vector3f albedo = new Vector3f(0, 0, 0);

    //Constructor for Textured Triangle
    public Triangle(Vector3f[] formData, Vector2f[] uvCoords) {
        if (formData == null || formData.length != 3)
            throw new IllegalArgumentException("formData must have exactly 3 elements");

        if (uvCoords == null || uvCoords.length != 3)
            throw new IllegalArgumentException("uvCoords must have exactly 3 elements");

        this.formData[0] = formData[0];
        this.formData[1] = formData[1];
        this.formData[2] = formData[2];

        this.uvCoords[0] = uvCoords[0];
        this.uvCoords[1] = uvCoords[1];
        this.uvCoords[2] = uvCoords[2];

        this.appearance = RenderAppearence.SPRITE;

        initGraphicDriverRelevantObjects();
    }

    //Constructor for Static Colored Triangle
    public Triangle(Vector3f[] formData, Vector3f color) {
        if (formData == null || formData.length != 3)
            throw new IllegalArgumentException("formData must have exactly 3 elements");

        if (color == null)
            throw new IllegalArgumentException("color must not be null");

        this.formData[0] = formData[0];
        this.formData[1] = formData[1];
        this.formData[2] = formData[2];

        this.appearance = RenderAppearence.STATIC;

        this.color.x = color.x;
        this.color.y = color.y;
        this.color.z = color.z;

        initGraphicDriverRelevantObjects();
    }

    public void setModelMatrix(Matrix4f modelMatrix) {
        this.model = modelMatrix;
    }

    //Creates VBO and VAO and fills them with relevant Values to give the graphic driver the information it needs to render the triangle
    private void initGraphicDriverRelevantObjects() {
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        // === Position VBO ===
        int posVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, posVboId);

        float[] vertices = new float[9];
        for (int i = 0; i < 3; i++) {
            vertices[i * 3] = formData[i].x;
            vertices[i * 3 + 1] = formData[i].y;
            vertices[i * 3 + 2] = formData[i].z;
        }

        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0); // layout(location = 0) im Shader
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);

        // === UV VBO ===
        int uvVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, uvVboId);

        float[] uvData = new float[6];
        for (int i = 0; i < 3; i++) {
            uvData[i * 2] = uvCoords[i].x;
            uvData[i * 2 + 1] = uvCoords[i].y;
        }

        glBufferData(GL_ARRAY_BUFFER, uvData, GL_STATIC_DRAW);
        glEnableVertexAttribArray(1); // layout(location = 1) im Shader
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);

        // === Normal VBO ===
        int normalVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, normalVboId);

        float[] normalsData = new float[9];
        for (int i = 0; i < 3; i++) {
            normalsData[i * 3] = normals[i].x;
            normalsData[i * 3 + 1] = normals[i].y;
            normalsData[i * 3 + 2] = normals[i].z;
        }

        glBufferData(GL_ARRAY_BUFFER, normalsData, GL_STATIC_DRAW);
        glEnableVertexAttribArray(2); // layout(location = 2) im Shader
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);

        // Unbind VBO & VAO
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }


    public void render(Camera camera) {
        glUseProgram(ShaderCompiler.shaderProgramId);
        glBindVertexArray(vaoId);

        if (appearance.equals(RenderAppearence.STATIC)) {
            int colorLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "staticInputColor");
            if (colorLocation != -1) {
                glUniform4f(colorLocation, color.x, color.y, color.z, 1.0f);
            } else {
                System.err.println("WARN: staticInputColor uniform not found.");
            }

            int useTextureLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "useTexture");
            if (useTextureLocation != -1) {
                glUniform1i(useTextureLocation, 0);
            }
        }

        if(appearance.equals(RenderAppearence.SPRITE)){
            int useTextureLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "useTexture");
            if (useTextureLocation != -1) {
                glUniform1i(useTextureLocation, 1);
            }
        }

        int projectionLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "projection");
        if (projectionLocation != -1) {
            FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
            camera.getProjection()
                .get(projBuffer);
            glUniformMatrix4fv(projectionLocation, false, projBuffer);
        } else {
            System.err.println("WARN: projection uniform not found.");
        }

        int modelLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "model");
        if (modelLocation != -1) {
            FloatBuffer modelBuffer = BufferUtils.createFloatBuffer(16);
            model.get(modelBuffer);
            glUniformMatrix4fv(modelLocation, false, modelBuffer);
        } else {
            System.err.println("WARN: model uniform not found.");
        }

        int viewMatrixLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "view");
        if (viewMatrixLocation != -1) {
            FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
            camera.getViewMatrix()
                .get(viewBuffer);
            glUniformMatrix4fv(viewMatrixLocation, false, viewBuffer);
        } else {
            System.err.println("WARN: view uniform not found.");
        }

        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    public void translate(float x, float y, float z) {
        model.translate(x, y, z);
    }

    public void rotate(float angleRad, float axisX, float axisY, float axisZ) {
        model.rotate(angleRad, new Vector3f(axisX, axisY, axisZ));
    }

    public void scale(float x, float y, float z) {
        model.scale(x, y, z);
    }
}
