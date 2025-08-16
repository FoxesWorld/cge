package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import org.foxesworld.cge.tmp.menu.actions.MenuAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class providing common helper methods for the menu system.
 */
public final class MenuUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(MenuUtils.class);

    private MenuUtils() {} // Private constructor for utility class

    public static float parseSize(String sizeStr, float totalSize) {
        if (sizeStr == null || sizeStr.isBlank()) return 0;
        if (sizeStr.endsWith("%")) {
            float percent = Float.parseFloat(sizeStr.substring(0, sizeStr.length() - 1));
            return totalSize * (percent / 100f);
        } else {
            return Float.parseFloat(sizeStr);
        }
    }

    public static Vector2f calculatePosition(String xStr, String yStr, String align, Camera camera) {
        float screenWidth = camera.getWidth();
        float screenHeight = camera.getHeight();
        float x;
        if ("CENTER_X".equalsIgnoreCase(align)) x = screenWidth / 2f;
        else if ("RIGHT".equalsIgnoreCase(align)) x = screenWidth;
        else x = 0; // Default to LEFT
        x += parseSize(xStr, screenWidth);
        float y = parseSize(yStr, screenHeight);
        return new Vector2f(x, y);
    }

    public static Runnable createActionFromClassName(String className, Application app) {
        if (className == null || className.isBlank()) return () -> {};
        try {
            Class<?> actionClass = Class.forName(className);
            MenuAction menuAction = (MenuAction) actionClass.getDeclaredConstructor().newInstance();
            return () -> menuAction.execute(app.getStateManager().getState(MainMenuAppState.class));
        } catch (Exception e) {
            LOGGER.error("Failed to create action from class name: '{}'. Button will be inactive.", className, e);
            return () -> {};
        }
    }
}