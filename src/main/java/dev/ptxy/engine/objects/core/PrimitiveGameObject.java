package dev.ptxy.engine.objects.core;

import dev.ptxy.engine.camera.SimpleCamera2D;
import dev.ptxy.engine.objects.primitivs.GameObject;
import dev.ptxy.engine.shader.ShaderCompiler;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

public abstract class PrimitiveGameObject extends GameObject {
    private final static ColorMode COLOR_MODE = ColorMode.STATIC;
    protected Vector4f staticColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    @Override
    public void render(SimpleCamera2D camera) {
        if(!isInitialized){
            throw new RuntimeException("Render Method of object was called but the object was never initilized!");
        }

        glUseProgram(ShaderCompiler.shaderProgramId);
        checkGLError("glUseProgram");
        glBindVertexArray(vaoId);
        checkGLError("glBindVertexArray");

        if (COLOR_MODE.equals(ColorMode.STATIC)) {
            int colorLocation = glGetUniformLocation(ShaderCompiler.shaderProgramId, "staticInputColor");
            if (colorLocation != -1) {
                glUniform4f(colorLocation, staticColor.x, staticColor.y, staticColor.z, staticColor.w);
                checkGLError("glUniform4f (staticInputColor)");
            } else {
                System.err.println("WARN: staticInputColor uniform not found.");
            }
        }

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

    protected abstract Vector4f setStaticColor();

    protected void init() {
        staticColor = setStaticColor();
        cornerPoints = setBaseCornerPoints();
        vaoId = instanceVao();
        glBindVertexArray(vaoId);
        vboId = instanceVbo();
        //Unbind everything
        glBindVertexArray(0);
        isInitialized = true;
    }
}
