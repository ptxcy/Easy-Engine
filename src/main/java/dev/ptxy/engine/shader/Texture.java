package dev.ptxy.engine.shader;

import dev.ptxy.engine.util.AssetPaths;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

public class Texture {
    private final int textureId;
    private final int width;
    private final int height;

    public Texture(String filePath) {
        //load data
        IntBuffer widthBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer heightBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer channelsBuffer = BufferUtils.createIntBuffer(1);
        STBImage.stbi_set_flip_vertically_on_load(false);
        ByteBuffer imageData = STBImage.stbi_load(filePath, widthBuffer, heightBuffer, channelsBuffer, 4);
        if (imageData == null) {
            imageData = STBImage.stbi_load(AssetPaths.defaultTexture().toString(), widthBuffer, heightBuffer, channelsBuffer, 4);
            if (imageData == null) {
                throw new RuntimeException("Failed to load a texture file: " + filePath + "\n" + STBImage.stbi_failure_reason());
            }
        }

        //set data
        width = widthBuffer.get();
        height = heightBuffer.get();
        textureId = glGenTextures();

        //activate texture
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Set texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        // Upload texture data to GPU
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, imageData);

        // Generate Mipmaps (optional but recommended)
        glGenerateMipmap(GL_TEXTURE_2D);

        // Free image memory (CPU-side)
        STBImage.stbi_image_free(imageData);

        // Unbind the texture
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void bind(int textureUnit) {
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public int getId() {
        return textureId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
