package dev.ptxy.engine.shader;

import static org.lwjgl.opengl.GL30.*;

import dev.ptxy.engine.config.Config;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.naming.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ShaderCompiler {
    private static final Logger log = LogManager.getLogger(ShaderCompiler.class);
    private static final Map<String, Integer> shaderMap = new HashMap<>();

    public static Integer getShader(String name) {
        Integer id = shaderMap.get(name);
        if (id == null)
            throw new RuntimeException("Requested Shader id was null for name: " + name);
        return id;
    }

    public static void preloadConfiguredShaders(String vertexPath, String fragmentPath) {
        String[] arr = Config.getPreloadShaders();
        long totalStart = System.nanoTime();
        log.info("Preloading {} configured shader(s)", arr.length);
        try {
            for (String path : arr) {
                long t = System.nanoTime();
                Integer shaderId = compile(path + "/vertex.glsl", path + "/fragment.glsl");
                String[] pathParts = path.splitWithDelimiters("/", 2);
                if (pathParts.length <= 1)
                    throw new ConfigurationException(
                            "Configured Shader path in Scene.config must at least have 2 dirs"
                                    + " shader/name");
                String name = pathParts[pathParts.length - 1];
                shaderMap.put(name, shaderId);
                log.debug(
                        "Shader \"{}\" compiled and linked in {}ms (id={})",
                        name,
                        elapsed(t),
                        shaderId);
            }
        } catch (ConfigurationException ce) {
            throw new RuntimeException(ce.getMessage());
        }
        log.info("All shaders preloaded in {}ms", elapsed(totalStart));
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
            String info = glGetProgramInfoLog(shaderProgram);
            log.error("Shader program linking failed:\n{}", info);
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
            String info = glGetShaderInfoLog(shader);
            String typeName = type == GL_VERTEX_SHADER ? "VERTEX" : "FRAGMENT";
            log.error("{} shader compilation failed:\n{}", typeName, info);
            throw new RuntimeException(typeName + " shader compilation failed");
        }

        return shader;
    }

    private static String readShaderFile(String path) {
        try (var stream =
                ShaderCompiler.class.getResourceAsStream(
                        path.startsWith("/") ? path : "/" + path)) {
            if (stream == null) {
                throw new RuntimeException("Shader file not found: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader file: " + path, e);
        }
    }

    private static long elapsed(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000L;
    }
}
