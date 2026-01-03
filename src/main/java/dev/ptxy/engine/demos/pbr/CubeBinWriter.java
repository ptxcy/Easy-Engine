package dev.ptxy.engine.demos.pbr;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CubeBinWriter {
    public static void main(String[] args) throws Exception {
        float[] vertices = {
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f
        };
        float[] normals = {
            -1,-1,-1, 1,-1,-1, 1,1,-1, -1,1,-1,
            -1,-1,1, 1,-1,1, 1,1,1, -1,1,1
        };
        float[] uvs = {
            0,0, 1,0, 1,1, 0,1,
            0,0, 1,0, 1,1, 0,1
        };
        short[] indices = {
            0,1,2, 2,3,0,
            4,5,6, 6,7,4,
            0,4,7, 7,3,0,
            1,5,6, 6,2,1,
            3,2,6, 6,7,3,
            0,1,5, 5,4,0
        };

        try (FileOutputStream fos = new FileOutputStream("cube.bin")) {
            ByteBuffer buffer = ByteBuffer.allocate(vertices.length*4 + normals.length*4 + uvs.length*4 + indices.length*2);
            buffer.order(ByteOrder.nativeOrder());

            for (float f : vertices) buffer.putFloat(f);
            for (float f : normals) buffer.putFloat(f);
            for (float f : uvs) buffer.putFloat(f);
            for (short s : indices) buffer.putShort(s);

            fos.write(buffer.array());
        }

        System.out.println("cube.bin erstellt!");
    }
}
