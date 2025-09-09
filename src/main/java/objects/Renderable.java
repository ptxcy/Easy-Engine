package objects;

import org.joml.Matrix4f;

public interface Renderable {
    void render(Matrix4f projection, Matrix4f view);
}
