package dev.ptxy.engine.light;

import org.joml.Vector3f;

public final class DirectionalLight {
    private Vector3f direction;
    private Vector3f color;

    public DirectionalLight(Vector3f direction, Vector3f color) {
        this.direction = direction;
        this.color = color;
    }

    public Vector3f getDirection() {
        return direction;
    }

    public void setDirection(Vector3f direction) {
        this.direction = direction;
    }

    public Vector3f getColor() {
        return color;
    }

    public void setColor(Vector3f color) {
        this.color = color;
    }
}
