package dev.ptxy.engine.objects;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class MovementUtility {
    //TODO Add Methods for working with the Transform Matrix so that Calculation with normal world coordinates can be applied
    private MovementUtility() {
        throw new IllegalStateException("Utility Class");
    }

    /**
     * Sets the world/local position (translation) to x,y,z while preserving rotation and scale.
     */
    public static Matrix4f setPosition(Matrix4f transform, float x, float y, float z) {
        if (transform == null) throw new IllegalArgumentException("transform must not be null");
        return transform.setTranslation(x, y, z);
    }

    public static Matrix4f setPosition(Matrix4f transform, Vector3f position) {
        if (position == null) throw new IllegalArgumentException("position must not be null");
        return setPosition(transform, position.x, position.y, position.z);
    }

    /**
     * Moves (adds) a delta translation in the matrix' current space.
     * Note: This applies a translation multiplication, which is typically what you want for "move by".
     */
    public static Matrix4f translate(Matrix4f transform, float dx, float dy, float dz) {
        if (transform == null) throw new IllegalArgumentException("transform must not be null");
        return transform.translate(dx, dy, dz);
    }

    public static Matrix4f translate(Matrix4f transform, Vector3f delta) {
        if (delta == null) throw new IllegalArgumentException("delta must not be null");
        return translate(transform, delta.x, delta.y, delta.z);
    }

    /**
     * Reads the translation component from the matrix into dest (or a new vector if dest is null).
     */
    public static Vector3f getPosition(Matrix4f transform, Vector3f dest) {
        if (transform == null) throw new IllegalArgumentException("transform must not be null");
        Vector3f out = (dest != null) ? dest : new Vector3f();
        return transform.getTranslation(out);
    }

    public static Vector3f getPosition(Matrix4f transform) {
        return getPosition(transform, null);
    }
}
