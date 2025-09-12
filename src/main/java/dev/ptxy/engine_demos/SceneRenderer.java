package dev.ptxy.engine_demos;

import dev.ptxy.engine.GameWindow;
import dev.ptxy.engine.camera.SimpleCamera2D;
import dev.ptxy.engine.objects.primitivs.filled.*;
import dev.ptxy.engine.objects.primitivs.unfilled.*;
import dev.ptxy.engine.objects.texture.TexturedSquare;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;

public final class SceneRenderer {
    private final SimpleCamera2D camera = new SimpleCamera2D();

    public SceneRenderer(){
        //Simple close Window Handling and WASD movement
        glfwSetKeyCallback(GameWindow.getActiveWindow()
                .getWindowHandle(), (window, key, scancode, action, mods) -> {
            float moveSpeed = 10.0f;

            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }

            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                switch (key) {
                    case GLFW_KEY_W:
                        camera.getPosition().add(0.0f, moveSpeed, 0.0f);
                        camera.updateCamera();
                        break;
                    case GLFW_KEY_S:
                        camera.getPosition().add(0.0f, -moveSpeed, 0.0f);
                        camera.updateCamera();
                        break;
                    case GLFW_KEY_A:
                        camera.getPosition().add(-moveSpeed, 0.0f, 0.0f);
                        camera.updateCamera();
                        break;
                    case GLFW_KEY_D:
                        camera.getPosition().add(moveSpeed, 0.0f, 0.0f);
                        camera.updateCamera();
                        break;
                    case GLFW_KEY_UP:
                        camera.getScale().add(0.1f,0.1f,0f);
                        camera.updateCamera();
                        break;
                    case GLFW_KEY_DOWN:
                        camera.getScale().add(-0.1f,-0.1f,0f);
                        camera.updateCamera();
                        break;
                }
            }
        });
    }

    public void renderScene() {
        TextureCollection collection = new TextureCollection();
        new Triangle().moveTo2D(0,0).scale2D(100,100).render(camera);
        new Square().moveTo2D(105,0).scale2D(100,100).render(camera);
        new Circle().moveTo2D(210,0).scale2D(100,100).render(camera);
        new Line().moveTo2D(0,105).scale2D(1000,1).render(camera);
        new OutlinedTriangle().moveTo2D(0,110).scale2D(100,100).render(camera);
        new OutlinedSquare().moveTo2D(105,110).scale2D(100,100).render(camera);
        new OutlinedCircle().moveTo2D(210,110).scale2D(100,100).render(camera);
        new TexturedSquare(collection.defaultTexture).moveTo2D(400,400).scale2D(100,100).render(camera);
    }
}
