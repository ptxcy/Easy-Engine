package dev.ptxy.engine.shader;

import static org.lwjgl.opengl.GL30.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

/**
 * Loads a classpath PNG into an OpenGL 2D texture via STBImage (already an lwjgl-stb dependency for
 * the debug GUI's font rendering) — deliberately not javax.imageio/AWT, which risks conflicting
 * with GLFW's -XstartOnFirstThread requirement on macOS.
 */
public final class TextureLoader {

    private TextureLoader() {}

    public static int load(String classpathResource) {
        ByteBuffer fileBuffer = readResourceToDirectBuffer(classpathResource);

        ByteBuffer pixels;
        int width;
        int height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(true);
            pixels = STBImage.stbi_load_from_memory(fileBuffer, w, h, channels, 4);
            if (pixels == null) {
                throw new IllegalStateException(
                        "Failed to decode texture "
                                + classpathResource
                                + ": "
                                + STBImage.stbi_failure_reason());
            }
            width = w.get(0);
            height = h.get(0);
        }

        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(
                GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);

        STBImage.stbi_image_free(pixels);
        return textureId;
    }

    private static ByteBuffer readResourceToDirectBuffer(String classpathResource) {
        try (InputStream stream = TextureLoader.class.getResourceAsStream(classpathResource)) {
            if (stream == null) {
                throw new IllegalStateException("Texture resource not found: " + classpathResource);
            }
            byte[] bytes = stream.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read texture: " + classpathResource, e);
        }
    }
}
