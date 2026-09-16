package dev.ptxy.engine;

import org.lwjgl.glfw.GLFW;

final class KeyEdge {
    private final int key;
    private boolean wasDown;

    KeyEdge(int key) {
        this.key = key;
    }

    boolean pressed(long windowHandle) {
        boolean down = GLFW.glfwGetKey(windowHandle, key) == GLFW.GLFW_PRESS;
        boolean edge = down && !wasDown;
        wasDown = down;
        return edge;
    }
}
