import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import shader.ShaderCompiler;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class Core {
    public void run() {
        init();
        loop();
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

        private void loop() {
            GL.createCapabilities();
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            ShaderCompiler.initShader("shader/vertex.glsl","shader/fragment.glsl");

            //Simple close Window Handling
            glfwSetKeyCallback(GameWindow.getActiveWindow().getWindowHandle(), (window, key, scancode, action, mods) -> {
                if ( key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE )
                    glfwSetWindowShouldClose(window, true);
            });

            while ( !glfwWindowShouldClose(GameWindow.getActiveWindow().getWindowHandle()) ) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                SceneRenderer.renderScene();

                glfwSwapBuffers(GameWindow.getActiveWindow().getWindowHandle());

                glfwPollEvents();
            }
        }
}
