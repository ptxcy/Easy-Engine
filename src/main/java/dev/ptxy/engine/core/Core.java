package dev.ptxy.engine.core;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.stackPush;

import dev.ptxy.engine.config.Config;
import dev.ptxy.engine.shader.ShaderCompiler;
import java.nio.IntBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

public final class Core {
    private static final Logger log = LogManager.getLogger(Core.class);

    private Vector4f clearColor = new Vector4f(0.53f, 0.81f, 0.98f, 1f);

    public Core() {
        init();
    }

    public void changeClearColor(Vector4f color) {
        this.clearColor = color;
    }

    public void run(SceneRenderer sceneRenderer) {
        log.info("Starting game loop");
        loop(sceneRenderer);
        sceneRenderer.shutdown();
        log.info("Engine shutting down");
        GameWindow.clearWindow();
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        long initStart = System.nanoTime();

        long t = System.nanoTime();
        log.info("Initializing GLFW");
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            log.fatal("Unable to initialize GLFW");
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        log.debug("GLFW ready in {}ms", elapsed(t));

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // Mac Os hard dependency start
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        // Mac Os hard dependency end

        t = System.nanoTime();
        GameWindow.createWindowFromConfig();
        log.debug("Window created in {}ms", elapsed(t));

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(GameWindow.getActiveWindow().getWindowHandle(), pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                    GameWindow.getActiveWindow().getWindowHandle(),
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2);
        }

        glfwMakeContextCurrent(GameWindow.getActiveWindow().getWindowHandle());
        glfwSwapInterval(Config.getWindowConfig().vsync() ? 1 : 0);
        glfwShowWindow(GameWindow.getActiveWindow().getWindowHandle());
        glfwFocusWindow(GameWindow.getActiveWindow().getWindowHandle());

        log.info("Engine initialized in {}ms", elapsed(initStart));
    }

    private void loop(SceneRenderer sceneRenderer) {
        long glStart = System.nanoTime();

        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glClearColor(clearColor.x, clearColor.y, clearColor.z, clearColor.w);
        ShaderCompiler.preloadConfiguredShaders(
                "shader/base/vertex.glsl", "shader/base/fragment.glsl");

        log.info("OpenGL context ready in {}ms — entering game loop", elapsed(glStart));

        long fpsTimer = System.nanoTime();
        int frameCount = 0;
        double lastFrameTime = glfwGetTime();

        while (!glfwWindowShouldClose(GameWindow.getActiveWindow().getWindowHandle())) {
            double frameTime = glfwGetTime();
            float deltaTime = (float) (frameTime - lastFrameTime);
            lastFrameTime = frameTime;

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            sceneRenderer.renderScene(deltaTime);

            glfwSwapBuffers(GameWindow.getActiveWindow().getWindowHandle());

            sceneRenderer.beforePollEvents();
            glfwPollEvents();
            sceneRenderer.afterPollEvents();

            frameCount++;
            long now = System.nanoTime();
            if (now - fpsTimer >= 1_000_000_000L) {
                sceneRenderer.onFps(frameCount);
                frameCount = 0;
                fpsTimer = now;
            }
        }
    }

    private static long elapsed(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000L;
    }
}
