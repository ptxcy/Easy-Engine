package dev.ptxy.engine.objects.assets;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.objects.Triangle;
import dev.ptxy.engine.objects.properties.Material;
import dev.ptxy.engine.shader.ShaderCompiler;
import dev.ptxy.engine.shader.ShaderUtils;
import dev.ptxy.engine.shader.Texture;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Random;

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
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertexCount * 14);

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

                buffer.put(tri.tangents()[i].x());
                buffer.put(tri.tangents()[i].y());
                buffer.put(tri.tangents()[i].z());

                buffer.put(tri.bitangents()[i].x());
                buffer.put(tri.bitangents()[i].y());
                buffer.put(tri.bitangents()[i].z());
            }
        }
        buffer.flip();

        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        int stride = 14 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 6 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glVertexAttribPointer(3, 3, GL_FLOAT, false, stride, 8 * Float.BYTES);
        glEnableVertexAttribArray(3);

        glVertexAttribPointer(4, 3, GL_FLOAT, false, stride, 8 * Float.BYTES);
        glEnableVertexAttribArray(4);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void render(Matrix4f transform, SimpleCamera3D camera, DirectionalLight light) {
        if (vertexCount == 0 || vaoId == -1) return;

        int shader = ShaderCompiler.getShader("base");
        glUseProgram(shader);
        setShaderVars(transform,camera,light,shader);
        glUseProgram(0);
    }

    public void render(Matrix4f transform, SimpleCamera3D camera, DirectionalLight light, String shaderName) {
        if (vertexCount == 0 || vaoId == -1) return;

        int shader = ShaderCompiler.getShader(shaderName);
        glUseProgram(shader);
        setShaderVars(transform,camera,light,shader);
        setGrassShaderVars(shaderName,shader);
        glUseProgram(0);
    }

    private void setShaderVars(Matrix4f transform, SimpleCamera3D camera, DirectionalLight light, Integer shader) {
        // Matrizen
        ShaderUtils.setUniformMat4(shader, "model", transform);
        ShaderUtils.setUniformMat4(shader, "view", camera.getViewMatrix());
        ShaderUtils.setUniformMat4(shader, "projection", camera.getProjection());

        // Kamera & Licht
        ShaderUtils.setUniformVec3(shader, "camPos", camera.getPosition());
        ShaderUtils.setUniformVec3(shader, "lightDir", light.getDirection());
        ShaderUtils.setUniformVec3(shader, "lightColor", light.getColor());

        // Material
        Material mat = materials.isEmpty() ? new Material() : materials.getFirst();
        ShaderUtils.setUniformVec3(shader, "albedo", mat.getAlbedo());
        ShaderUtils.setUniformFloat(shader, "metallic", mat.getMetallic());
        ShaderUtils.setUniformFloat(shader, "roughness", mat.getRoughness());
        ShaderUtils.setUniformFloat(shader, "ao", mat.getAo());

        // Texturen
        boolean useBase = baseColors.stream().anyMatch(Objects::nonNull);
        boolean useMR = metallicRoughness.stream().anyMatch(Objects::nonNull);
        boolean useNormal = normalMaps.stream().anyMatch(Objects::nonNull);

        ShaderUtils.setUniformInt(shader, "useBaseColor", useBase ? 1 : 0);
        ShaderUtils.setUniformInt(shader, "useMetallicRoughness", useMR ? 1 : 0);
        ShaderUtils.setUniformInt(shader, "useNormalMap", useNormal ? 1 : 0);

        if (useBase) baseColors.getFirst().bind(0);
        if (useMR) metallicRoughness.getFirst().bind(1);
        if (useNormal) normalMaps.getFirst().bind(2);

        ShaderUtils.setUniformInt(shader, "baseColorTexture", 0);
        ShaderUtils.setUniformInt(shader, "metallicRoughnessTexture", 1);
        ShaderUtils.setUniformInt(shader, "normalMapTexture", 2);

        // Draw Call
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);

        if (useBase) baseColors.getFirst().unbind();
        if (useMR) metallicRoughness.getFirst().unbind();
        if (useNormal) normalMaps.getFirst().unbind();
    }

    long gameStartTime = System.currentTimeMillis();
    public void setGrassShaderVars(String shaderName, Integer shaderId) {
        if(!"grass".equals(shaderName)) return;
        long currentTime = System.currentTimeMillis();
        float elapsedTime = (currentTime - gameStartTime) / 1000f;
        ShaderUtils.setUniformFloat(shaderId, "time", elapsedTime);
        ShaderUtils.setUniformFloat(shaderId, "windStrength", 0.1f);
    }

    public List<Material> getMaterials() {
        return materials;
    }

    public List<Texture> getBaseColors() {
        return baseColors;
    }

    public List<Texture> getMetallicRoughness() {
        return metallicRoughness;
    }

    public List<Texture> getNormalMaps() {
        return normalMaps;
    }
}
