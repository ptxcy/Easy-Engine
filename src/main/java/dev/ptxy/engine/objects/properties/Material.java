package dev.ptxy.engine.objects.properties;

import org.joml.Vector3f;

public class Material {
    private Vector3f albedo = new Vector3f(0.5f, 0.0f, 0.0f);
    private float metallic = 0.0f;
    private float roughness = 0.5f;
    private float ao = 1.0f;
    private float alpha = 1.0f;

    // Getter
    public Vector3f getAlbedo() { return albedo; }
    public float getMetallic() { return metallic; }
    public float getRoughness() { return roughness; }
    public float getAo() { return ao; }
    public float getAlpha() { return alpha; }

    // Setter
    public void setAlbedo(Vector3f albedo) { this.albedo.set(albedo); }
    public void setMetallic(float metallic) { this.metallic = metallic; }
    public void setRoughness(float roughness) { this.roughness = roughness; }
    public void setAo(float ao) { this.ao = ao; }
    public void setAlpha(float alpha) { this.alpha = alpha; }
}
