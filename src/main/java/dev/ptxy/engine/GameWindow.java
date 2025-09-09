package dev.ptxy.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class GameWindow {
    private static final Logger LOG = LoggerFactory.getLogger(GameWindow.class.getName());
    private static GameWindow currentActiveWindow;
    private final Integer width;
    private final Integer height;
    private final String title;
    private final Long monitor;
    private final Long share;
    private long windowHandle;

    private GameWindow(Integer width, Integer height, String title, Long monitor, Long share) {
        this.width = width;
        this.height = height;
        this.title = title;
        this.monitor = monitor;
        this.share = share;
        windowHandle = glfwCreateWindow(width, height, title, monitor, share);
        if (windowHandle == NULL)
            throw new RuntimeException("Failed to create the GLFW window");
    }

    public static GameWindow getActiveWindow() {
        if (currentActiveWindow != null) return currentActiveWindow;
        throw new RuntimeException("Tryed to get the current Window Object before Instanciating it (Window Object was null)");
    }

    public static void createWindow(Integer width, Integer height, String title, Long monitor, Long share) {
        if (currentActiveWindow != null) {
            glfwFreeCallbacks(GameWindow.getActiveWindow().getWindowHandle());
            glfwDestroyWindow(GameWindow.getActiveWindow().getWindowHandle());
        }

        currentActiveWindow = new GameWindow(width, height, title, monitor, share);
    }

    public static void createWindowFromSystemProperties() {
        if (currentActiveWindow != null) {
            glfwFreeCallbacks(GameWindow.getActiveWindow().getWindowHandle());
            glfwDestroyWindow(GameWindow.getActiveWindow().getWindowHandle());
        }

        int width = Integer.parseInt(System.getProperty("WIDTH", "1000"));
        int height = Integer.parseInt(System.getProperty("WIDTH", "1000"));
        String title = System.getProperty("WINDOW_TITLE", "Game");
        long monitor = Long.parseLong(System.getProperty("WINDOW_MONITOR", "0"));
        long share = Long.parseLong(System.getProperty("WINDOW_SHARE", "0"));
        currentActiveWindow = new GameWindow(width, height, title, monitor, share);
        LOG.info("Instantiated Window with Attributes: {}", currentActiveWindow);
    }

    public static void clearWindow() {
        if (currentActiveWindow != null) {
            glfwFreeCallbacks(GameWindow.getActiveWindow().getWindowHandle());
            glfwDestroyWindow(GameWindow.getActiveWindow().getWindowHandle());
        }
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public String getTitle() {
        return title;
    }

    public Long getMonitor() {
        return monitor;
    }

    public Long getShare() {
        return share;
    }

    public long getWindowHandle() {
        return windowHandle;
    }

    @Override
    public String toString() {
        return "GameWindow: {" +
                "width=" + width +
                ", height=" + height +
                ", title='" + title + '\'' +
                ", monitor=" + monitor +
                ", share=" + share +
                ", window=" + windowHandle +
                '}';
    }
}
