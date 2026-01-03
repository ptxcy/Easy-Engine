package dev.ptxy.engine.demos.pbr;

import dev.ptxy.engine.objects.AbstractMesh;
import dev.ptxy.engine.objects.ChildMesh;
import dev.ptxy.engine.objects.MasterMesh;
import dev.ptxy.engine.objects.Triangle;
import dev.ptxy.engine.shader.Texture;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.assimp.Assimp.*;

public class GLTFLoader {

    /** Lädt die Szene als MasterMesh mit allen Children */
    public static MasterMesh loadScene(String path) {
        AIScene scene = aiImportFile(path,
            aiProcess_Triangulate |
                aiProcess_JoinIdenticalVertices |
                aiProcess_FlipUVs |
                aiProcess_GenNormals
        );

        if (scene == null)
            throw new RuntimeException("Failed to load glTF: " + aiGetErrorString());

        String baseDir = path.substring(0, path.lastIndexOf('/') + 1);

        MasterMesh master = new MasterMesh(new ArrayList<>());

        //TODO Provided MasterMesh but needs childmesh
        processNode(scene.mRootNode(), scene, master, baseDir);

        return master;
    }

    /** Rekursive Verarbeitung von Nodes */
    private static void processNode(AINode node, AIScene scene, AbstractMesh parent, String baseDir) {
        Matrix4f localTransform = assimpMatrixToJOML(node.mTransformation());

        List<Triangle> nodeTriangles = new ArrayList<>();
        for (int i = 0; i < node.mNumMeshes(); i++) {
            int meshIndex = node.mMeshes().get(i);
            AIMesh mesh = AIMesh.create(scene.mMeshes().get(meshIndex));

            List<Triangle> triangles = loadTriangles(mesh);

            AIMaterial aiMat = AIMaterial.create(scene.mMaterials().get(mesh.mMaterialIndex()));
            Material mat = loadMaterialFromNode(aiMat);
            Texture baseColorTex = loadTextureFromMaterial(aiMat, aiTextureType_BASE_COLOR, baseDir);
            Texture metallicRoughnessTex = loadTextureFromMaterial(aiMat, aiTextureType_METALNESS, baseDir);
            Texture normalMapTex = loadTextureFromMaterial(aiMat, aiTextureType_NORMALS, baseDir);

            ChildMesh child = new ChildMesh(triangles);
            child.setMaterial(mat);
            child.setBaseColorTexture(baseColorTex);
            child.setMetallicRoughnessTexture(metallicRoughnessTex);
            child.setNormalMapTexture(normalMapTex);

            child.getLocalTransform().set(localTransform);
            parent.addChild(child);
        }

        ChildMesh child = new ChildMesh(nodeTriangles);
        child.getLocalTransform().set(localTransform);
        parent.addChild(child);

        for (int i = 0; i < node.mNumChildren(); i++) {
            AINode childNode = AINode.create(node.mChildren().get(i));
            processNode(childNode, scene, child, baseDir);
        }
    }


    /** Wandelt einen Assimp Mesh in Triangle-Objekte um */
    private static List<Triangle> loadTriangles(AIMesh mesh) {
        List<Triangle> tris = new ArrayList<>();

        AIVector3D.Buffer verts = mesh.mVertices();
        AIVector3D.Buffer norms = mesh.mNormals();
        AIVector3D.Buffer texCoords = mesh.mTextureCoords(0);
        AIFace.Buffer faces = mesh.mFaces();

        for (int f = 0; f < faces.capacity(); f++) {
            AIFace face = faces.get(f);
            if (face.mNumIndices() != 3) continue;

            int i0 = face.mIndices().get(0);
            int i1 = face.mIndices().get(1);
            int i2 = face.mIndices().get(2);

            Vector3f[] triVerts = new Vector3f[]{
                new Vector3f(verts.get(i0).x(), verts.get(i0).y(), verts.get(i0).z()),
                new Vector3f(verts.get(i1).x(), verts.get(i1).y(), verts.get(i1).z()),
                new Vector3f(verts.get(i2).x(), verts.get(i2).y(), verts.get(i2).z())
            };

            Vector3f[] triNormals = new Vector3f[]{
                new Vector3f(norms.get(i0).x(), norms.get(i0).y(), norms.get(i0).z()),
                new Vector3f(norms.get(i1).x(), norms.get(i1).y(), norms.get(i1).z()),
                new Vector3f(norms.get(i2).x(), norms.get(i2).y(), norms.get(i2).z())
            };

            Vector2f[] triUVs = new Vector2f[3];
            if (texCoords != null) {
                triUVs[0] = new Vector2f(texCoords.get(i0).x(), texCoords.get(i0).y());
                triUVs[1] = new Vector2f(texCoords.get(i1).x(), texCoords.get(i1).y());
                triUVs[2] = new Vector2f(texCoords.get(i2).x(), texCoords.get(i2).y());
            } else {
                triUVs[0] = triUVs[1] = triUVs[2] = new Vector2f(0, 0);
            }

            tris.add(new Triangle(triVerts, triNormals, triUVs));
        }

        return tris;
    }

    private static Material loadMaterialFromNode(AIMaterial aiMat) {
        Material material = new Material();
        AIColor4D color = AIColor4D.create();

        if (aiGetMaterialColor(aiMat, AI_MATKEY_BASE_COLOR, 0, 0, color) == 0 ||
            aiGetMaterialColor(aiMat, AI_MATKEY_COLOR_DIFFUSE, 0, 0, color) == 0) {
            material.setAlbedo(new Vector3f(color.r(), color.g(), color.b()));
        }

        PointerBuffer props = aiMat.mProperties();
        for (int i = 0; i < props.capacity(); i++) {
            AIMaterialProperty prop = AIMaterialProperty.create(props.get(i));
            String key = prop.mKey().dataString();
            ByteBuffer data = prop.mData();
            data.order(ByteOrder.nativeOrder());

            if (key.contains("metallicFactor") && data.remaining() >= 4)
                material.setMetallic(data.getFloat(0));

            if (key.contains("roughnessFactor") && data.remaining() >= 4)
                material.setRoughness(data.getFloat(0));

            if ((key.equals(AI_MATKEY_OPACITY) || key.equals("d") || key.equals("TransparencyFactor"))
                && data.remaining() >= 4) {
                material.setAlpha(data.getFloat(0));
            }
        }

        return material;
    }

    private static Texture loadTextureFromMaterial(AIMaterial mat, int texType, String baseDir) {
        AIString texPath = AIString.calloc();
        Texture texture = null;

        if (aiGetMaterialTexture(mat, texType, 0, texPath, (IntBuffer)null, null, null, null, null, null) == 0) {
            String texFile = texPath.dataString();
            texture = new Texture(baseDir + texFile);
        }

        texPath.free();
        return texture;
    }

    private static Matrix4f assimpMatrixToJOML(AIMatrix4x4 m) {
        return new Matrix4f(
            m.a1(), m.b1(), m.c1(), m.d1(),
            m.a2(), m.b2(), m.c2(), m.d2(),
            m.a3(), m.b3(), m.c3(), m.d3(),
            m.a4(), m.b4(), m.c4(), m.d4()
        );
    }
}
