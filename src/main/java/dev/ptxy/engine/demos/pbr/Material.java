package dev.ptxy.engine.demos.pbr;

public abstract class Material {
    protected Vector3f albedo = new Vector3f(0.5f, 0.0f, 0.0f);
    protected float metallic = 0.0f;
    protected float roughness = 0.5f;
    protected float ao = 1.0f;

    public Vector3f getAlbedo() { return albedo; }
    public float getMetallic() { return metallic; }
    public float getRoughness() { return roughness; }
    public float getAo() { return ao; }
}

