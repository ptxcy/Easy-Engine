package shader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL30.*;

public class ShaderCompiler {
    public static Integer shaderProgramId;

    public static void initShader(String vertexPath, String fragmentPath) {
        shaderProgramId = compile(vertexPath, fragmentPath);
    }

    public static int compile(String vertexPath, String fragmentPath) {
        String vertexSource = readShaderFile(vertexPath);
        String fragmentSource = readShaderFile(fragmentPath);

        int vertexShader = compileShader(vertexSource, GL_VERTEX_SHADER);
        int fragmentShader = compileShader(fragmentSource, GL_FRAGMENT_SHADER);

        int shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);

        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(shaderProgram);
            System.err.println("Shader Program Linking Error:\n" + log);
            throw new RuntimeException("Shader program linking failed");
        }

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        return shaderProgram;
    }

    private static int compileShader(String source, int type) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            String typeName = type == GL_VERTEX_SHADER ? "VERTEX" : "FRAGMENT";
            System.err.println(typeName + " Shader Compilation Error:\n" + log);
            throw new RuntimeException(typeName + " shader compilation failed");
        }

        return shader;
    }

    private static String readShaderFile(String path) {
        try {
            var stream = ShaderCompiler.class.getClassLoader().getResourceAsStream(path);
            if (stream == null) {
                throw new RuntimeException("Shader file not found: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader file: " + path, e);
        }
    }
}
