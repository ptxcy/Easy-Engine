package dev.ptxy.engine.objects.core;

import dev.ptxy.engine.camera.Camera;
import dev.ptxy.engine.objects.primitivs.GameObject;
import dev.ptxy.engine.objects.texture.Texture;
import dev.ptxy.engine.shader.ShaderCompiler;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

public abstract class TexturedGameObject extends GameObject {
    private final static ColorMode COLOR_MODE = ColorMode.SPRITE;
    protected Vector2f[] textureCoords;
    protected Texture texture;
    protected int uvVboId;

    protected int instanceUvVbo() {
        FloatBuffer uvBuffer = memAllocFloat(textureCoords.length * 2);
        for (Vector2f uv : textureCoords) {
            uvBuffer.put(uv.x).put(uv.y);
        }
        uvBuffer.flip();

        int uvVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, uvVbo);
        glBufferData(GL_ARRAY_BUFFER, uvBuffer, GL_STATIC_DRAW);

        // UV Attribute location = 1
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        memFree(uvBuffer);

        return uvVbo;
    }

    @Override
    public void render(Camera camera) {
        if(!isInitialized){
            throw new RuntimeException("Render Method of object was called but the object was never initilized!");
        }
        glUseProgram(ShaderCompiler.shaderProgramId);
        checkGLError("glUseProgram");
        glBindVertexArray(vaoId);
        checkGLError("glBindVertexArray");

        int projectionLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "projection");
        if (projectionLocation != -1) {
            FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
            camera.getProjection()
                    .get(projBuffer);
            glUniformMatrix4fv(projectionLocation, false, projBuffer);
            checkGLError("glUniformMatrix4fv (projection)");
        } else {
            System.err.println("WARN: projection uniform not found.");
        }

        int modelLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "model");
        if (modelLocation != -1) {
            FloatBuffer modelBuffer = BufferUtils.createFloatBuffer(16);
            model.get(modelBuffer);
            glUniformMatrix4fv(modelLocation, false, modelBuffer);
            checkGLError("glUniformMatrix4fv (model)");
        } else {
            System.err.println("WARN: model uniform not found.");
        }

        int viewMatrixLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "view");
        if (viewMatrixLocation != -1) {
            FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
            camera.getViewMatrix()
                    .get(viewBuffer);
            glUniformMatrix4fv(viewMatrixLocation, false, viewBuffer);
            checkGLError("glUniformMatrix4fv (view)");
        } else {
            System.err.println("WARN: view uniform not found.");
        }

        if (COLOR_MODE == ColorMode.SPRITE && texture != null) {
            texture.bind(0);

            int texLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "spriteTexture");
            if (texLocation != -1) {
                glUniform1i(texLocation, 0);
                checkGLError("glUniform1i (spriteTexture)");
            } else {
                System.err.println("WARN: spriteTexture uniform not found.");
            }
        }

        int useTextureLoc = glGetUniformLocation(ShaderCompiler.shaderProgramId, "useTexture");
        if (useTextureLoc != -1) {
            glUniform1i(useTextureLoc, COLOR_MODE.ordinal());
            checkGLError("glUniform1i (useTexture)");
        } else {
            System.err.println("WARN: useTexture uniform not found.");
        }

        glDrawArrays(drawMode, 0, cornerPoints.length);
        checkGLError("glDrawArrays");
        glBindVertexArray(0);
        glUseProgram(0);
    }

    private void checkGLError(String stage) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            System.err.printf("OpenGL ERROR [%s]: 0x%X\n", stage, error);
        }
    }

    protected abstract Texture setTexture();

    protected abstract Vector2f[] setTextureCords();

    protected void init() {
        textureCoords = setTextureCords();
        texture = setTexture();
        cornerPoints = setBaseCornerPoints();
        vaoId = instanceVao();
        glBindVertexArray(vaoId);
        vboId = instanceVbo();
        uvVboId = instanceUvVbo();
        //Unbind everything
        glBindVertexArray(0);
        isInitialized = true;
    }
}
