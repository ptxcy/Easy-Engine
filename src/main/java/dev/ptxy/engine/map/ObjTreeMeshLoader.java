package dev.ptxy.engine.map;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal loader for triangulated Wavefront OBJ files, used to load procedurally generated tree
 * models as a static mesh instead of hand-rolled vertex arrays. Only v/vt/vn/f are supported (no
 * quads, materials, or groups) since that is all the generator scripts under scratchpad emit.
 *
 * <p>The vt.x channel is repurposed to carry a per-vertex "heightT" material-blend scalar (see
 * ChunkManager's tree shaders) instead of a real texture coordinate.
 */
final class ObjTreeMeshLoader {

    private static final int OUTPUT_FLOATS_PER_VERTEX = 7;

    private ObjTreeMeshLoader() {}

    static float[] load(String classpathResource) {
        List<float[]> positions = new ArrayList<>();
        List<Float> heightT = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<Float> out = new ArrayList<>();

        try (InputStream stream = ObjTreeMeshLoader.class.getResourceAsStream(classpathResource)) {
            if (stream == null) {
                throw new IllegalStateException("OBJ resource not found: " + classpathResource);
            }
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split("\\s+");
                switch (tokens[0]) {
                    case "v" ->
                            positions.add(
                                    new float[] {
                                        Float.parseFloat(tokens[1]),
                                        Float.parseFloat(tokens[2]),
                                        Float.parseFloat(tokens[3])
                                    });
                    case "vt" -> heightT.add(Float.parseFloat(tokens[1]));
                    case "vn" ->
                            normals.add(
                                    new float[] {
                                        Float.parseFloat(tokens[1]),
                                        Float.parseFloat(tokens[2]),
                                        Float.parseFloat(tokens[3])
                                    });
                    case "f" -> {
                        for (int i = 1; i <= 3; i++) {
                            String[] idx = tokens[i].split("/");
                            float[] pos = positions.get(Integer.parseInt(idx[0]) - 1);
                            float ht = heightT.get(Integer.parseInt(idx[1]) - 1);
                            float[] normal = normals.get(Integer.parseInt(idx[2]) - 1);
                            out.add(pos[0]);
                            out.add(pos[1]);
                            out.add(pos[2]);
                            out.add(normal[0]);
                            out.add(normal[1]);
                            out.add(normal[2]);
                            out.add(ht);
                        }
                    }
                    default -> {}
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load OBJ resource: " + classpathResource, e);
        }

        float[] arr = new float[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        if (arr.length % OUTPUT_FLOATS_PER_VERTEX != 0) {
            throw new IllegalStateException("Malformed OBJ vertex data from " + classpathResource);
        }
        return arr;
    }
}
