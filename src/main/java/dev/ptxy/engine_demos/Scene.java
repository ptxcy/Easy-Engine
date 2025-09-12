package dev.ptxy.engine_demos;

import dev.ptxy.engine.GameWindow;
import dev.ptxy.engine.camera.SimpleCamera2D;
import dev.ptxy.engine.objects.primitivs.filled.*;
import dev.ptxy.engine.objects.primitivs.unfilled.*;
import dev.ptxy.engine.objects.texture.TexturedSquare;
import org.joml.Vector4f;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;

public final class Scene implements dev.ptxy.engine.render.SceneRenderer {
    private final SimpleCamera2D camera = new SimpleCamera2D();

    public Scene(){
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
        var red = new Vector4f(1f,0f,0f,1f);
        var blue = new Vector4f(0f,1f,0f,1f);
        var green = new Vector4f(0f,0f,1f,1f);
        new Triangle(red).moveTo2D(0,0).scale2D(100,100).render(camera);
        new Square(blue).moveTo2D(105,0).scale2D(100,100).render(camera);
        new Circle(green).moveTo2D(210,0).scale2D(100,100).render(camera);
        new Line(red).moveTo2D(0,105).scale2D(1000,1).render(camera);
        new OutlinedTriangle(red).moveTo2D(0,110).scale2D(100,100).render(camera);
        new OutlinedSquare(blue).moveTo2D(105,110).scale2D(100,100).render(camera);
        new OutlinedCircle(green).moveTo2D(210,110).scale2D(100,100).render(camera);
        new Line(red).moveTo2D(0,215).scale2D(1000,1).render(camera);
        new TexturedSquare(collection.defaultTexture).moveTo2D(0,220).scale2D(100,100).render(camera);
    }
}
