package dev.ptxy.engine.objects;

import dev.ptxy.engine.camera.SimpleCamera3D;
import dev.ptxy.engine.gltf.GLTFLoader;
import dev.ptxy.engine.light.DirectionalLight;
import dev.ptxy.engine.objects.assets.Asset;
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;
import dev.ptxy.engine.objects.properties.Material;
import dev.ptxy.engine.shader.Texture;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class Ground {
    private static final List<Asset> referenceAssets = new ArrayList<>();

    private SceneNode groundNode;

    public static Ground generateChunkOfGround(long seed) {
        if(referenceAssets.isEmpty()) {
            referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref").getAsset());
            referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref_two").getAsset());
            referenceAssets.add(SceneNodeRegistry.getAvailable("grass_ground_ref_three").getAsset());
        }

        Texture diffuseTexOne = referenceAssets.getFirst().getBaseColors().getFirst();
        Texture diffuseTexTwo = referenceAssets.get(1).getBaseColors().getFirst();
        Texture diffuseTexThree = referenceAssets.get(2).getBaseColors().getFirst();
        Texture noiseTex = genereateNoiseTexture(seed);



        return null;
    }

    private static Texture genereateNoiseTexture(long seed) {
        return null;
    }

    public void render(Matrix4f transform, SimpleCamera3D camera, DirectionalLight light){
        groundNode.render(transform,camera,light);
    }

    public void render(Matrix4f transform, SimpleCamera3D camera, DirectionalLight light, String shaderName){
        groundNode.render(transform,camera,light,shaderName);
    }
}
