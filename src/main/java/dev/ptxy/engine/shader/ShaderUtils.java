package dev.ptxy.engine.shader;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL20.*;

public class ShaderUtils {
    public static void setUniformInt(int program, String name, int value) {
        int loc = glGetUniformLocation(program, name);
        glUniform1i(loc, value);
    }

    public static void setUniformFloat(int program, String name, float value) {
        int loc = glGetUniformLocation(program, name);
        glUniform1f(loc, value);
    }

    public static void setUniformVec3(int program, String name, Vector3f v) {
        int loc = glGetUniformLocation(program, name);
        glUniform3f(loc, v.x, v.y, v.z);
    }

    public static void setUniformMat4(int program, String name, Matrix4f mat) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            mat.get(fb);
            int loc = glGetUniformLocation(program, name);
            glUniformMatrix4fv(loc, false, fb);
        }
    }
}

