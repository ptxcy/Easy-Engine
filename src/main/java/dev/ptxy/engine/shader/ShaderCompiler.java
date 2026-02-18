package dev.ptxy.engine.shader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ptxy.engine.objects.assets.SceneNodeRegistry;

import javax.naming.ConfigurationException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL30.*;

public class ShaderCompiler {
    private static final Map<String,Integer> shaderMap = new HashMap<>();

    public static Integer getShader(String name){
       Integer id = shaderMap.get(name);
       if(id == null) throw new RuntimeException("Requested Shader id was null for name: " + name);
       return id;
    }

    public static void preloadConfiguredShaders(String vertexPath, String fragmentPath) {
        //TODO Multithreading
        try (Reader reader = new InputStreamReader(
                SceneNodeRegistry.class.getResourceAsStream("/SceneConfig.json"),
                StandardCharsets.UTF_8
        )) {
            if (reader == null) throw new RuntimeException("SceneConfig.json not found in resources!");

            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            JsonArray arr = json.getAsJsonArray("preloadShader");

            for (int i = 0; i < arr.size(); i++) {
                String path = arr.get(i).getAsString();
                System.out.println("Preloading Shader: " + path);
                Integer shaderId = compile(path + "/vertex.glsl",path + "/fragment.glsl");
                String[] pathParts = path.splitWithDelimiters("/", 2);
                if(pathParts.length <= 1) throw new ConfigurationException("Configured Shader path in Scene.config must at least have 2 dirs shader/name");
                String name = pathParts[pathParts.length - 1];
                shaderMap.put(name,shaderId);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to preload assets from SceneConfig.json", e);
        }
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
        try (var stream = ShaderCompiler.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path)) {
            if (stream == null) {
                throw new RuntimeException("Shader file not found: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader file: " + path, e);
        }
    }
}
