package org.foxesworld.cge.tmp.menu;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.foxesworld.cge.core.utils.ColorUtils;
import org.foxesworld.cge.tmp.menu.components.UIComponent;
import org.foxesworld.cge.tmp.menu.xml.*;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * A highly extensible, registration-based menu builder. It constructs a complete menu scene
 * from an XML file by dispatching build tasks to registered component builders.
 */
public final class XmlMenuBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmlMenuBuilder.class);

    private final BuildContext context;
    private final ComponentRegistry registry;
    private final JAXBContext jaxbContext;

    public XmlMenuBuilder(MainMenuAppState mainMenuAppState) {
        this.context = new BuildContext(mainMenuAppState);
        this.registry = new ComponentRegistry(this);
        this.jaxbContext = createJaxbContext();
    }

    /**
     * Creates and caches the JAXBContext. This is a heavy operation and should only be done once.
     */
    private JAXBContext createJaxbContext() {
        try {
            return JAXBContext.newInstance(
                    MenuXml.class, ScreenXml.class, SceneXml.class, ButtonXml.class,
                    TextXml.class, TabsXml.class, TabXml.class, SliderXml.class, CheckboxXml.class, PanelXml.class, ImageXml.class
            );
        } catch (JAXBException e) {
            throw new RuntimeException("Failed to initialize JAXBContext for menu building.", e);
        }
    }

    public MenuData build(String xmlPath) {
        context.allComponents().clear();
        Node uiRoot = new Node("XmlUIRoot");

        try (InputStream is = XmlMenuBuilder.class.getClassLoader().getResourceAsStream(xmlPath)) {
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
            return new MenuData(menu.scene, uiRoot, context.allComponents());
        } catch (Exception e) {
            LOGGER.error("Failed to build menu from XML file: {}", xmlPath, e);
            return MenuData.createEmpty();
        }
    }

    /**
     * The main factory method. It finds the appropriate builder for the given XML model
     * and delegates the build task to it.
     */
    public UIComponent buildComponent(ComponentXml model, Node parent) {
        ComponentBuilder<ComponentXml> builder = (ComponentBuilder<ComponentXml>) registry.getBuilderFor(model);
        if (builder != null) {
            UIComponent component = builder.build(model, parent, context);
            context.addComponent(component);
            return component;
        } else {
            LOGGER.warn("No component builder registered for XML model type: {}", model.getClass().getSimpleName());
            return null;
        }
    }

    private void createScreenBackground(ScreenXml model, Node parent) {
        Camera camera = context.mainMenuAppState().getGameEngine().getCamera();
        Quad backgroundQuad = new Quad(camera.getWidth(), camera.getHeight());
        Geometry backgroundGeom = new Geometry("ScreenBackground", backgroundQuad);
        ColorRGBA color = ColorRGBA.Black;
        try {
            color = ColorUtils.fromHexString(model.bgColor);
        } catch (Exception e) {
            LOGGER.error("Invalid HEX color format for bgColor: '{}'. Using default.", model.bgColor);
        }
        if (model.bgAlpha != null) color.a = model.bgAlpha;
        Material mat = new Material(context.mainMenuAppState().getGameEngine().getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        backgroundGeom.setMaterial(mat);
        backgroundGeom.setLocalTranslation(0, 0, -1);
        parent.attachChild(backgroundGeom);
    }

    public BuildContext getContext() {
        return context;
    }
}