package dev.ptxy.engine.objects;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.objects.assets.Asset;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class SceneNode {

    private final Asset asset;
    private final Matrix4f localTransform = new Matrix4f();
    private final List<SceneNode> children = new ArrayList<>();

    public SceneNode(Asset asset) {
        this.asset = asset;
    }

    public void addChild(SceneNode child) {
        children.add(child);
    }

    public void setLocalTransform(Matrix4f transform) {
        localTransform.set(transform);
    }

    public Matrix4f getLocalTransform() {
        return localTransform;
    }

    public List<SceneNode> getChildren() {
        return children;
    }

    public Asset getAsset() {
        return asset;
    }

    /**
     * Deep copy für Instanzierung
     */
    public SceneNode cloneNode() {
        SceneNode copy = new SceneNode(asset);
        copy.setLocalTransform(new Matrix4f(localTransform));
        for (SceneNode child : children) {
            copy.addChild(child.cloneNode());
        }
        return copy;
    }

    public void render(Matrix4f identity, SimpleCamera3D camera, DirectionalLight light) {
        asset.render(identity,camera,light);
        children.forEach(node->render(identity,camera,light));
    }

}
