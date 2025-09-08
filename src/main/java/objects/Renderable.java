package objects;

import org.joml.Matrix4f;

public interface Renderable {
    abstract void render(Matrix4f projection);
}
