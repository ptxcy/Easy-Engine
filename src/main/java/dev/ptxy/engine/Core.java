package dev.ptxy.engine;

import dev.ptxy.engine.render.SceneRenderer;
import dev.ptxy.engine.shader.ShaderCompiler;
import dev.ptxy.engine_demos.Scene;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class Core {
    public Core() {
        init();
    }

    public void run(SceneRenderer sceneRenderer) {
        loop(sceneRenderer);
        GameWindow.clearWindow();
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if ( !glfwInit() ){
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // Mac Os hard dependency start
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        // Mac Os hard dependency end

        GameWindow.createWindowFromSystemProperties();

        try ( MemoryStack stack = stackPush() ) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(GameWindow.getActiveWindow().getWindowHandle(), pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                    GameWindow.getActiveWindow().getWindowHandle(),
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        glfwMakeContextCurrent(GameWindow.getActiveWindow().getWindowHandle());
        glfwSwapInterval(1);
        glfwShowWindow(GameWindow.getActiveWindow().getWindowHandle());
        glfwFocusWindow(GameWindow.getActiveWindow().getWindowHandle());
    }

        private void loop(SceneRenderer sceneRenderer) {
            GL.createCapabilities();
            glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            ShaderCompiler.initShader("dev/ptxy/engine/shader/vertex.glsl","dev/ptxy/engine/shader/fragment.glsl");

            while ( !glfwWindowShouldClose(GameWindow.getActiveWindow().getWindowHandle()) ) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                sceneRenderer.renderScene();

                glfwSwapBuffers(GameWindow.getActiveWindow().getWindowHandle());

                glfwPollEvents();
            }
        }
}
