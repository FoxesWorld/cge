package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.foxesworld.cge.core.utils.ColorUtils; // Предполагается, что у вас есть этот утилитный класс
import org.foxesworld.cge.tmp.menu.actions.MenuAction;
import org.foxesworld.cge.tmp.menu.components.MenuComponent;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.xml.*;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.foxesworld.cge.tmp.menu.xml.builders.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A highly extensible, registration-based menu builder. It constructs a complete menu scene
 * from an XML file by dispatching build tasks to registered component builders and collects
 * all created interactive components into a unified list.
 */
public final class XmlMenuBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmlMenuBuilder.class);

    private final BuildContext context;
    private final Map<Class<? extends ComponentXml>, ComponentBuilder<?>> componentBuilders = new HashMap<>();

    // A single, unified list for all created interactive components.
    private final List<MenuComponent> allComponents = new ArrayList<>();

    public XmlMenuBuilder(Application app, ViceButton.Style buttonStyle) {
        this.context = new BuildContext(app, buttonStyle);
        registerDefaultBuilders();
    }

    private void registerDefaultBuilders() {
        registerComponentBuilder(TitleXml.class, new TitleBuilder());
        registerComponentBuilder(ButtonXml.class, new ButtonBuilder());
        registerComponentBuilder(SliderXml.class, new SliderBuilder());
        registerComponentBuilder(CheckboxXml.class, new CheckboxBuilder());
        registerComponentBuilder(TabsXml.class, new TabsBuilder(this));
    }

    public <T extends ComponentXml> void registerComponentBuilder(Class<T> modelClass, ComponentBuilder<T> builder) {
        componentBuilders.put(modelClass, builder);
        LOGGER.debug("Registered component builder for {}", modelClass.getSimpleName());
    }

    public MenuData build(String xmlPath) {
        allComponents.clear();
        Node uiRoot = new Node("XmlUIRoot");

        try (InputStream is = XmlMenuBuilder.class.getClassLoader().getResourceAsStream(xmlPath)) {
            JAXBContext jaxbContext = JAXBContext.newInstance(
                    MenuXml.class, ScreenXml.class, SceneXml.class, ButtonXml.class,
                    TitleXml.class, TabsXml.class, TabXml.class, SliderXml.class, CheckboxXml.class
            );
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            MenuXml menu = (MenuXml) unmarshaller.unmarshal(is);

            if (menu.screen != null) {
                if (menu.screen.bgColor != null) createScreenBackground(menu.screen, uiRoot);
                if (menu.screen.components != null) {
                    for (ComponentXml component : menu.screen.components) {
                        buildComponent(component, uiRoot);
                    }
                }
            }
            return new MenuData(menu.scene, uiRoot, allComponents);
        } catch (Exception e) {
            LOGGER.error("Failed to build menu from XML file: {}", xmlPath, e);
            return MenuData.createEmpty();
        }
    }

    /**
     * The main factory method. It finds the appropriate builder for the given XML model,
     * delegates the build task, and adds the created component to a unified list if it's a MenuComponent.
     */
    public Object buildComponent(ComponentXml model, Node parent) {
        ComponentBuilder builder = componentBuilders.get(model.getClass());
        if (builder != null) {
            Object component = builder.build(model, parent, context);

            // Add the newly created component to the unified list if it implements the interface.
            if (component instanceof MenuComponent menuComponent) {
                allComponents.add(menuComponent);
            }
            return component;
        } else {
            LOGGER.warn("No component builder registered for XML model type: {}", model.getClass().getSimpleName());
            return null;
        }
    }

    private void createScreenBackground(ScreenXml model, Node parent) {
        Camera camera = context.app().getCamera();
        Quad backgroundQuad = new Quad(camera.getWidth(), camera.getHeight());
        Geometry backgroundGeom = new Geometry("ScreenBackground", backgroundQuad);
        ColorRGBA color = ColorRGBA.Black;
        try {
            color = ColorUtils.fromHexString(model.bgColor);
        } catch (Exception e) {
            LOGGER.error("Invalid HEX color format for bgColor: '{}'. Using default.", model.bgColor);
        }
        if (model.bgAlpha != null) color.a = model.bgAlpha;
        Material mat = new Material(context.app().getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        backgroundGeom.setMaterial(mat);
        backgroundGeom.setLocalTranslation(0, 0, -1);
        parent.attachChild(backgroundGeom);
    }

    // --- UTILITY METHODS (public static for access from builders) ---

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
        else x = 0;
        x += parseSize(xStr, screenWidth);
        float y = parseSize(yStr, screenHeight);
        return new Vector2f(x, y);
    }

    public static Runnable createActionFromClassName(String className, Application app) {
        if (className == null || className.isBlank()) return () -> {};
        try {
            Class<?> actionClass = Class.forName(className);
            MenuAction menuAction = (MenuAction) actionClass.getDeclaredConstructor().newInstance();
            return () -> menuAction.execute(app);
        } catch (Exception e) {
            LOGGER.error("Failed to create action from class name: '{}'. Button will be inactive.", className, e);
            return () -> {};
        }
    }
}