package dev.ptxy.engine.objects;

import dev.ptxy.engine.camera.Camera;
import dev.ptxy.engine.demos.pbr.PointLight;
import org.joml.Matrix4f;
import java.util.List;

import static dev.ptxy.engine.shader.ShaderCompiler.shaderProgramId;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glUseProgram;

public class MasterMesh extends AbstractMesh{
    //Postion Matrix of the Mesh.
    private final Matrix4f model = new Matrix4f();

    public MasterMesh(List<Triangle> triangles) {
        this.triangles.addAll(triangles);
    }

    public void renderAllRecursive(Camera camera, PointLight light) {
        render(camera, light);

        for (ChildMesh c : children) {
            c.renderAllRecursive(camera, light, model);
        }
    }

    private void render(Camera camera, PointLight light) {
        if (vaoId == -1) initVaoAndVBO();

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
        setUniformMatrix("model", model);

        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, triangles.size() * 3);
        glBindVertexArray(0);

        unbindTextures();
        glUseProgram(0);
    }
}
