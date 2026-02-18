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

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.*;

public class Asset {
    private String id;
    private AssetType type;
    private List<Triangle> triangles;
    private List<Material> materials;
    private List<Texture> baseColors;
    private List<Texture> metallicRoughness;
    private List<Texture> normalMaps;

    //Optional parameters
    private Texture noiseTexture;

    private int vaoId = -1;
    private int vboId = -1;
    private int vertexCount = 0;

    public Asset() {}

    public String getId() {
        return id;
    }

    public void prepareAssetForRendering() {
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
        switch (type) {
            case GRASS -> setGrassShaderVars(shader);
            case GROUND -> setGroundShaderVars(shader);
            default -> {
                //Nothing i guess
            }
        }

        glUseProgram(0);
    }

    private void setGroundShaderVars(Integer shader) {
        if(noiseTexture == null) throw new RuntimeException("Noise texture not set but is required for: " + type.name() + " shader");
        baseColors.getFirst().bind(0);
        baseColors.get(1).bind(1);
        baseColors.get(2).bind(2);
        noiseTexture.bind(3);

        ShaderUtils.setUniformFloat(shader, "noiseScale", 1f);
        ShaderUtils.setUniformFloat(shader, "mixThreshold", 0.1f);
        ShaderUtils.setUniformFloat(shader, "mixStrength", 0.5f);
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
    public void setGrassShaderVars(Integer shaderId) {
        long currentTime = System.currentTimeMillis();
        float elapsedTime = (currentTime - gameStartTime) / 1000f;
        ShaderUtils.setUniformFloat(shaderId, "time", elapsedTime);
        ShaderUtils.setUniformFloat(shaderId, "windStrength", 0.05f);
    }

    public void setId(String id) {
        this.id = id;
    }

    public AssetType getType() {
        return type;
    }

    public void setType(AssetType type) {
        this.type = type;
    }

    public List<Triangle> getTriangles() {
        return triangles;
    }

    public void setTriangles(List<Triangle> triangles) {
        this.triangles = triangles;
    }

    public List<Material> getMaterials() {
        return materials;
    }

    public void setMaterials(List<Material> materials) {
        this.materials = materials;
    }

    public List<Texture> getBaseColors() {
        return baseColors;
    }

    public void setBaseColors(List<Texture> baseColors) {
        this.baseColors = baseColors;
    }

    public List<Texture> getMetallicRoughness() {
        return metallicRoughness;
    }

    public void setMetallicRoughness(List<Texture> metallicRoughness) {
        this.metallicRoughness = metallicRoughness;
    }

    public List<Texture> getNormalMaps() {
        return normalMaps;
    }

    public void setNormalMaps(List<Texture> normalMaps) {
        this.normalMaps = normalMaps;
    }

    public Texture getNoiseTexture() {
        return noiseTexture;
    }

    public void setNoiseTexture(Texture noiseTexture) {
        this.noiseTexture = noiseTexture;
    }
}
