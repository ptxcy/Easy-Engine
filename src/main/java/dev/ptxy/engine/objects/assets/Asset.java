package dev.ptxy.engine.objects.assets;

import dev.ptxy.engine.camera.Camera;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.light.PointLight;
import dev.ptxy.engine.objects.Triangle;
import dev.ptxy.engine.objects.properties.Material;
import dev.ptxy.engine.shader.ShaderCompiler;
import dev.ptxy.engine.shader.ShaderUtils;
import dev.ptxy.engine.shader.Texture;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.*;

public class Asset {

    private final String id;
    private final List<Triangle> triangles;
    private final List<Material> materials;
    private final List<Texture> baseColors;
    private final List<Texture> metallicRoughness;
    private final List<Texture> normalMaps;

    private int vaoId = -1;
    private int vboId = -1;
    private int vertexCount = 0;

    public Asset(String id,
                 List<Triangle> triangles,
                 List<Material> materials,
                 List<Texture> baseColors,
                 List<Texture> metallicRoughness,
                 List<Texture> normalMaps) {
        this.id = id;
        this.triangles = triangles;
        this.materials = materials;
        this.baseColors = baseColors;
        this.metallicRoughness = metallicRoughness;
        this.normalMaps = normalMaps;
        setupMesh();
    }

    public String getId() {
        return id;
    }

    /**
     * Erstellt VAO/VBO aus den Triangles. Muss einmal nach Asset-Load aufgerufen werden.
     */
    public void setupMesh() {
        if (triangles.isEmpty()) return;

        vertexCount = triangles.size() * 3;
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertexCount * 8);

        for (Triangle tri : triangles) {
            for (int i = 0; i < 3; i++) {
                buffer.put(tri.formData()[i].x());
                buffer.put(tri.formData()[i].y());
                buffer.put(tri.formData()[i].z());

                buffer.put(tri.normals()[i].x());
                buffer.put(tri.normals()[i].y());
                buffer.put(tri.normals()[i].z());

                buffer.put(tri.uvCoords()[i].x());
                buffer.put(tri.uvCoords()[i].y());
            }
        }
        buffer.flip();

        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        // VertexAttribs: pos(0), uv(1), normal(2)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glVertexAttribPointer(2, 3, GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    /**
     * Rendert das Asset mit einem Draw Call.
     */
    public void render(Matrix4f transform, Camera camera, DirectionalLight light) {
        if (vertexCount == 0 || vaoId == -1) return;
        if (ShaderCompiler.shaderProgramId == null) {
            throw new IllegalStateException("Shader not initialized!");
        }

        int shader = ShaderCompiler.shaderProgramId;
        glUseProgram(shader);

        // Matrizen
        ShaderUtils.setUniformMat4(shader, "model", transform);
        ShaderUtils.setUniformMat4(shader, "view", camera.getViewMatrix());
        ShaderUtils.setUniformMat4(shader, "projection", camera.getProjection());

        // Kamera & Licht
        ShaderUtils.setUniformVec3(shader, "camPos", camera.getPosition());
        ShaderUtils.setUniformVec3(shader, "lightDir", light.getDirection());
        ShaderUtils.setUniformVec3(shader, "lightColor", light.getColor());

        // Material
        Material mat = materials.isEmpty() ? new Material() : materials.get(0);
        ShaderUtils.setUniformVec3(shader, "albedo", mat.getAlbedo());
        ShaderUtils.setUniformFloat(shader, "metallic", mat.getMetallic());
        ShaderUtils.setUniformFloat(shader, "roughness", mat.getRoughness());
        ShaderUtils.setUniformFloat(shader, "ao", mat.getAo());

        // Texturen
        boolean useBase = baseColors.stream().anyMatch(t -> t != null);
        boolean useMR = metallicRoughness.stream().anyMatch(t -> t != null);
        boolean useNormal = normalMaps.stream().anyMatch(t -> t != null);

        ShaderUtils.setUniformInt(shader, "useBaseColor", useBase ? 1 : 0);
        ShaderUtils.setUniformInt(shader, "useMetallicRoughness", useMR ? 1 : 0);
        ShaderUtils.setUniformInt(shader, "useNormalMap", useNormal ? 1 : 0);

        if (useBase) baseColors.get(0).bind(0);
        if (useMR) metallicRoughness.get(0).bind(1);
        if (useNormal) normalMaps.get(0).bind(2);

        ShaderUtils.setUniformInt(shader, "baseColorTexture", 0);
        ShaderUtils.setUniformInt(shader, "metallicRoughnessTexture", 1);
        ShaderUtils.setUniformInt(shader, "normalMapTexture", 2);

        // Draw Call
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);

        if (useBase) baseColors.get(0).unbind();
        if (useMR) metallicRoughness.get(0).unbind();
        if (useNormal) normalMaps.get(0).unbind();

        glUseProgram(0);
    }
}
