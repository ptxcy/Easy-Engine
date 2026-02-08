package dev.ptxy.engine.objects;

import dev.ptxy.engine.objects.properties.Material;
import dev.ptxy.engine.shader.Texture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static dev.ptxy.engine.shader.ShaderCompiler.shaderProgramId;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public abstract class AbstractMesh {
    protected List<ChildMesh> children = new ArrayList<>();
    protected final List<Triangle> triangles = new ArrayList<>();
    protected Texture baseColorTexture;
    protected Texture metallicRoughnessTexture;
    protected Texture normalMapTexture;
    protected Material material;
    protected int vaoId = -1;

    protected void initVaoAndVBO() {
        int triangleCount = triangles.size();
        float[] vertices = new float[triangleCount * 9];
        float[] normals = new float[triangleCount * 9];
        float[] uvs = new float[triangleCount * 6];

        for (int i = 0; i < triangleCount; i++) {
            Triangle t = triangles.get(i);
            for (int v = 0; v < 3; v++) {
                vertices[i * 9 + v * 3]     = t.getFormData()[v].x;
                vertices[i * 9 + v * 3 + 1] = t.getFormData()[v].y;
                vertices[i * 9 + v * 3 + 2] = t.getFormData()[v].z;

                normals[i * 9 + v * 3]     = t.getNormals()[v].x;
                normals[i * 9 + v * 3 + 1] = t.getNormals()[v].y;
                normals[i * 9 + v * 3 + 2] = t.getNormals()[v].z;

                uvs[i * 6 + v * 2]     = t.getUvCoords()[v].x;
                uvs[i * 6 + v * 2 + 1] = t.getUvCoords()[v].y;
            }
        }

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        int vboVertices = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboVertices);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);

        int vboNormals = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboNormals);
        glBufferData(GL_ARRAY_BUFFER, normals, GL_STATIC_DRAW);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);

        int vboUVs = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboUVs);
        glBufferData(GL_ARRAY_BUFFER, uvs, GL_STATIC_DRAW);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    protected void setUniformMatrix(String name, Matrix4f mat) {
        int loc = glGetUniformLocation(shaderProgramId, name);
        if (loc != -1) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            mat.get(buffer);
            glUniformMatrix4fv(loc, false, buffer);
        }
    }

    protected void bindTextures() {
        if (baseColorTexture != null) {
            baseColorTexture.bind(0);
            int loc = glGetUniformLocation(shaderProgramId, "baseColorTexture");
            if (loc != -1) glUniform1i(loc, 0);
            int flag = glGetUniformLocation(shaderProgramId, "useBaseColor");
            if (flag != -1) glUniform1i(flag, 1);
        } else {
            int flag = glGetUniformLocation(shaderProgramId, "useBaseColor");
            if (flag != -1) glUniform1i(flag, 0);
        }

        if (metallicRoughnessTexture != null) {
            metallicRoughnessTexture.bind(1);
            int loc = glGetUniformLocation(shaderProgramId, "metallicRoughnessTexture");
            if (loc != -1) glUniform1i(loc, 1);
            int flag = glGetUniformLocation(shaderProgramId, "useMetallicRoughness");
            if (flag != -1) glUniform1i(flag, 1);
        } else {
            int flag = glGetUniformLocation(shaderProgramId, "useMetallicRoughness");
            if (flag != -1) glUniform1i(flag, 0);
        }

        if (normalMapTexture != null) {
            normalMapTexture.bind(2);
            int loc = glGetUniformLocation(shaderProgramId, "normalMapTexture");
            if (loc != -1) glUniform1i(loc, 2);
            int flag = glGetUniformLocation(shaderProgramId, "useNormalMap");
            if (flag != -1) glUniform1i(flag, 1);
        } else {
            int flag = glGetUniformLocation(shaderProgramId, "useNormalMap");
            if (flag != -1) glUniform1i(flag, 0);
        }
    }

    protected void unbindTextures() {
        if (baseColorTexture != null) baseColorTexture.unbind();
        if (metallicRoughnessTexture != null) metallicRoughnessTexture.unbind();
        if (normalMapTexture != null) normalMapTexture.unbind();
    }

    protected void setUniform3f(String name, Vector3f vec) {
        int loc = glGetUniformLocation(shaderProgramId, name);
        if (loc != -1) glUniform3f(loc, vec.x, vec.y, vec.z);
    }

    protected void setUniform1f(String name, float value) {
        int loc = glGetUniformLocation(shaderProgramId, name);
        if (loc != -1) glUniform1f(loc, value);
    }

    public void addChild(ChildMesh child) {children.add(child);}

    // ---------------------------
    // Transformationen lokal
    // ---------------------------
    public void setBaseColorTexture(Texture t) { this.baseColorTexture = t; }
    public void setMetallicRoughnessTexture(Texture t) { this.metallicRoughnessTexture = t; }
    public void setNormalMapTexture(Texture t) { this.normalMapTexture = t; }
    public void setMaterial(Material m) { this.material = m; }
    public void addTriangle(Triangle t) { this.triangles.add(t); }
}
