package dev.ptxy.engine.objects;

import dev.ptxy.engine.camera.Camera;
import dev.ptxy.engine.shader.ShaderCompiler;
import dev.ptxy.engine.shader.Texture;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1i;

public class TriangleMesh {
    //Texture Of The Triangle Mesh
    private Texture texture;

    private final List<Triangle> triangles = new ArrayList<>();

    public TriangleMesh(List<Triangle> trys, Texture texture) {
        triangles.addAll(trys);
    }

    public TriangleMesh(Texture texture) {
        this.texture = texture;
        float width = texture.getWidth();
        float height = texture.getHeight();
        float w = width;
        float h = height;

        // Zwei Dreiecke für das Rechteck
        Vector3f[] t1Vertices = new Vector3f[]{
            new Vector3f(0, 0, 0),
            new Vector3f(w, 0, 0),
            new Vector3f(0, h, 0)
        };
        Vector2f[] t1UV = new Vector2f[]{
            new Vector2f(0, 0),
            new Vector2f(1, 0),
            new Vector2f(0, 1)
        };

        Vector3f[] t2Vertices = new Vector3f[]{
            new Vector3f(w, 0, 0),
            new Vector3f(w, h, 0),
            new Vector3f(0, h, 0)
        };
        Vector2f[] t2UV = new Vector2f[]{
            new Vector2f(1, 0),
            new Vector2f(1, 1),
            new Vector2f(0, 1)
        };

        triangles.add(new Triangle(t1Vertices, t1UV));
        triangles.add(new Triangle(t2Vertices, t2UV));
    }

    public TriangleMesh(List<Triangle> trys) {
        triangles.addAll(trys);
    }

    public TriangleMesh translate(float x, float y, float z) {
        for (Triangle t : triangles) t.translate(x, y, z);
        return this;
    }

    public TriangleMesh rotate(float angleRad, float axisX, float axisY, float axisZ) {
        for (Triangle t : triangles) t.rotate(angleRad, axisX, axisY, axisZ);
        return this;
    }

    public TriangleMesh scale(float x, float y, float z) {
        for (Triangle t : triangles) t.scale(x, y, z);
        return this;
    }

    public void addTriangle(Triangle triangle) {
        triangles.add(triangle);
    }

    public void render(Camera camera) {
        if (texture != null) {
            texture.bind(0);
            int samplerLoc = glGetUniformLocation(ShaderCompiler.shaderProgramId, "textureSampler");
            if (samplerLoc != -1) {
                glUniform1i(samplerLoc, 0);
            }
        }

        for (Triangle triangle : triangles) {
            triangle.render(camera);
        }

        if (texture != null) {
            texture.unbind();
        }
    }

}
