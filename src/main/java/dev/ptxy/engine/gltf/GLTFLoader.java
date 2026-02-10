package dev.ptxy.engine.gltf;

import dev.ptxy.engine.objects.SceneNode;
import dev.ptxy.engine.objects.Triangle;
import dev.ptxy.engine.objects.assets.Asset;
import dev.ptxy.engine.objects.assets.AssetPaths;
import dev.ptxy.engine.objects.properties.Material;
import dev.ptxy.engine.shader.Texture;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;

public final class GLTFLoader {

    /**
     * Lädt ein GLTF File und gibt eine Map von SceneNodes zurück,
     * wobei Nodes ohne Assets ignoriert werden.
     */
    public static Map<String, SceneNode> loadSceneNodes(String path) {
        AIScene scene = aiImportFile(
                AssetPaths.asset(path).toString(),
                aiProcess_Triangulate |
                        aiProcess_JoinIdenticalVertices |
                        aiProcess_FlipUVs |
                        aiProcess_GenNormals
        );

        if (scene == null) {
            throw new RuntimeException("Failed to load GLTF: " + aiGetErrorString());
        }

        Map<String, SceneNode> nodes = new HashMap<>();
        processNode(scene.mRootNode(), scene, nodes, null);
        return nodes;
    }

    private static void processNode(AINode node, AIScene scene, Map<String, SceneNode> outNodes, SceneNode parent) {
        Asset asset = loadMeshIfPresent(node, scene);

        if (asset != null) {
            SceneNode sceneNode = new SceneNode(asset);
            sceneNode.setLocalTransform(convertTransform(node.mTransformation()));
            if (parent != null) parent.addChild(sceneNode);
            outNodes.put(node.mName().dataString(), sceneNode);
            parent = sceneNode;
        }

        for (int i = 0; i < node.mNumChildren(); i++) {
            processNode(AINode.create(node.mChildren().get(i)), scene, outNodes, parent);
        }
    }

    private static Asset loadMeshIfPresent(AINode node, AIScene scene) {
        if (node.mNumMeshes() == 0) return null;

        List<Triangle> triangles = new ArrayList<>();
        List<Material> materials = new ArrayList<>();
        List<Texture> baseColors = new ArrayList<>();
        List<Texture> metallicRoughness = new ArrayList<>();
        List<Texture> normalMaps = new ArrayList<>();

        for (int i = 0; i < node.mNumMeshes(); i++) {
            int meshIndex = node.mMeshes().get(i);
            AIMesh mesh = AIMesh.create(scene.mMeshes().get(meshIndex));

            triangles.addAll(loadTriangles(mesh));

            AIMaterial aiMat = AIMaterial.create(scene.mMaterials().get(mesh.mMaterialIndex()));
            materials.add(loadMaterial(aiMat));
            baseColors.add(loadTexture(aiMat, aiTextureType_BASE_COLOR));
            metallicRoughness.add(loadTexture(aiMat, aiTextureType_METALNESS));
            normalMaps.add(loadTexture(aiMat, aiTextureType_NORMALS));
        }

        return new Asset(
                node.mName().dataString(),
                triangles,
                materials,
                baseColors,
                metallicRoughness,
                normalMaps
        );
    }

    private static List<Triangle> loadTriangles(AIMesh mesh) {
        List<Triangle> tris = new ArrayList<>();
        AIVector3D.Buffer verts = mesh.mVertices();
        AIVector3D.Buffer norms = mesh.mNormals();
        AIVector3D.Buffer tex = mesh.mTextureCoords(0);
        AIFace.Buffer faces = mesh.mFaces();

        for (int f = 0; f < faces.capacity(); f++) {
            AIFace face = faces.get(f);
            if (face.mNumIndices() != 3) continue;

            int i0 = face.mIndices().get(0);
            int i1 = face.mIndices().get(1);
            int i2 = face.mIndices().get(2);

            Vector3f[] v = {
                    new Vector3f(verts.get(i0).x(), verts.get(i0).y(), verts.get(i0).z()),
                    new Vector3f(verts.get(i1).x(), verts.get(i1).y(), verts.get(i1).z()),
                    new Vector3f(verts.get(i2).x(), verts.get(i2).y(), verts.get(i2).z())
            };

            Vector3f[] n = {
                    new Vector3f(norms.get(i0).x(), norms.get(i0).y(), norms.get(i0).z()),
                    new Vector3f(norms.get(i1).x(), norms.get(i1).y(), norms.get(i1).z()),
                    new Vector3f(norms.get(i2).x(), norms.get(i2).y(), norms.get(i2).z())
            };

            Vector2f[] uv = new Vector2f[3];
            if (tex != null) {
                uv[0] = new Vector2f(tex.get(i0).x(), tex.get(i0).y());
                uv[1] = new Vector2f(tex.get(i1).x(), tex.get(i1).y());
                uv[2] = new Vector2f(tex.get(i2).x(), tex.get(i2).y());
            } else {
                uv[0] = uv[1] = uv[2] = new Vector2f();
            }

            tris.add(new Triangle(v, n, uv));
        }

        return tris;
    }

    private static Material loadMaterial(AIMaterial aiMat) {
        Material mat = new Material();
        AIColor4D color = AIColor4D.create();

        if (aiGetMaterialColor(aiMat, AI_MATKEY_BASE_COLOR, 0, 0, color) == 0 ||
                aiGetMaterialColor(aiMat, AI_MATKEY_COLOR_DIFFUSE, 0, 0, color) == 0) {
            mat.setAlbedo(new Vector3f(color.r(), color.g(), color.b()));
        }

        PointerBuffer props = aiMat.mProperties();
        for (int i = 0; i < props.capacity(); i++) {
            AIMaterialProperty prop = AIMaterialProperty.create(props.get(i));
            String key = prop.mKey().dataString();
            ByteBuffer data = prop.mData().order(ByteOrder.nativeOrder());

            if (key.contains("metallicFactor") && data.remaining() >= 4)
                mat.setMetallic(data.getFloat(0));
            if (key.contains("roughnessFactor") && data.remaining() >= 4)
                mat.setRoughness(data.getFloat(0));
        }

        return mat;
    }

    private static Texture loadTexture(AIMaterial mat, int type) {
        AIString path = AIString.calloc();
        Texture tex = null;

        if (aiGetMaterialTexture(mat, type, 0, path, (IntBuffer) null, null, null, null, null, null) == 0) {
            tex = new Texture(AssetPaths.asset(path.dataString()).toString());
        }

        path.free();
        return tex;
    }

    private static Matrix4f convertTransform(AIMatrix4x4 m) {
        return new Matrix4f(
                m.a1(), m.b1(), m.c1(), m.d1(),
                m.a2(), m.b2(), m.c2(), m.d2(),
                m.a3(), m.b3(), m.c3(), m.d3(),
                m.a4(), m.b4(), m.c4(), m.d4()
        );
    }
}
