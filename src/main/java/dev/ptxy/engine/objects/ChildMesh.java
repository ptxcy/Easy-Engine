package dev.ptxy.engine.objects;

import dev.ptxy.engine.camera.Camera;
import dev.ptxy.engine.demos.pbr.PointLight;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static dev.ptxy.engine.shader.ShaderCompiler.shaderProgramId;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL30.*;

public class ChildMesh extends AbstractMesh {
    public Matrix4f getLocalTransform() {
        return localTransform;
    }

    protected final Matrix4f localTransform = new Matrix4f();

    public ChildMesh(List<Triangle> triangles) {
        this.triangles.addAll(triangles);
    }

    public void renderAllRecursive(Camera camera, PointLight light, Matrix4f parentTransform) {
        render(camera, light, parentTransform);

        for (ChildMesh c : children) {
            c.renderAllRecursive(camera, light, new Matrix4f(parentTransform).mul(localTransform));
        }
    }

    /**
     * Render mit gegebener Parent-Transformation. Die globale Transformation ergibt sich aus: parentTransform * localTransform
     */
    private void render(Camera camera, PointLight light, Matrix4f parentTransform) {
        if (vaoId == -1)
            initVaoAndVBO();

        Matrix4f globalTransform = new Matrix4f(parentTransform).mul(localTransform);

        glUseProgram(shaderProgramId);

        bindTextures();

        setUniform3f("camPos", camera.getPosition());
        if (light != null) {
            setUniform3f("lightPos", light.getPosition());
            setUniform3f("lightColor", light.getColor());
        }

        if (material != null) {
            setUniform3f("albedo", material.getAlbedo());
            setUniform1f("metallic", material.getMetallic());
            setUniform1f("roughness", material.getRoughness());
            setUniform1f("ao", material.getAo());
        }

        setUniformMatrix("projection", camera.getProjection());
        setUniformMatrix("view", camera.getViewMatrix());
        setUniformMatrix("model", globalTransform);

        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, triangles.size() * 3);
        glBindVertexArray(0);

        unbindTextures();
        glUseProgram(0);
    }

    public void translate(float x, float y, float z) {localTransform.translate(x, y, z);}

    public void rotate(float angleRad, float axisX, float axisY, float axisZ) {localTransform.rotate(angleRad, axisX, axisY, axisZ);}

    public void scale(float x, float y, float z) {localTransform.scale(x, y, z);}
}
