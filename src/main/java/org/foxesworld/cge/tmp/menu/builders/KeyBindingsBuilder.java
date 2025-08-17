package org.foxesworld.cge.tmp.menu.builders;

import com.jme3.app.SimpleApplication;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;
import org.foxesworld.cge.tmp.menu.components.KeyBindingsComponent;
import org.foxesworld.cge.tmp.menu.input.KeyBindingsManager;
import org.foxesworld.cge.tmp.menu.xml.KeyBindingsXml;
import org.foxesworld.cge.tmp.menu.xml.KeyBindXml;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Optional;

/**
 * Builder for &lt;KeyBindings&gt; — iterates through all KeyBind elements and adds them to UI.
 *
 * Behaviour:
 *  - normalizes & validates model
 *  - creates KeyBindingsManager and loads definitions from model.toXmlInputStream()
 *  - applies mappings to InputManager
 *  - creates KeyBindingsComponent and explicitly adds every bind in the XML order
 */
public class KeyBindingsBuilder implements ComponentBuilder<KeyBindingsXml> {

    private static final Logger LOG = LoggerFactory.getLogger(KeyBindingsBuilder.class);

    private final XmlMenuBuilder mainBuilder;

    public KeyBindingsBuilder(XmlMenuBuilder mainBuilder) {
        this.mainBuilder = mainBuilder;
    }

    @Override
    public KeyBindingsComponent build(KeyBindingsXml model, Node parent, BuildContext context) {
        if (model == null) {
            LOG.warn("KeyBindingsXml model is null — nothing to build");
            return null;
        }

        // Normalize + validate
        try {
            model.normalize();
            model.validate();
        } catch (Exception ex) {
            LOG.warn("KeyBindingsXml normalization/validation failed: {}", ex.getMessage());
            // continue best-effort
        }

        // Create manager
        SimpleApplication app = (SimpleApplication) context.mainMenuAppState().getApplication();
        KeyBindingsManager manager = new KeyBindingsManager(app.getInputManager());

        // Load definitions into manager from DTO (ensures consistent parsing rules)
        try (InputStream is = model.toXmlInputStream()) {
            manager.loadDefinitionsFromXml(is);
        } catch (Exception ex) {
            LOG.warn("Failed to load keybindings into manager from DTO", ex);
        }

        // Apply all mappings so InputManager contains mapping entries
        try {
            manager.applyAllToInputManager();
        } catch (Exception ex) {
            LOG.warn("Failed to apply keybindings to InputManager", ex);
        }

        // Create UI component
        KeyBindingsComponent comp = new KeyBindingsComponent(model.id, context, manager);

        // Iterate through XML binds (preserves XML order) and add each to the component.
        // We get the corresponding KeyBind from manager (which was populated above).
        try {
            for (KeyBindXml kbXml : model.getBindsSafe()) {
                if (kbXml == null) continue;
                Optional<KeyBindingsManager.KeyBind> maybe = manager.getBind(kbXml.id);
                if (maybe.isPresent()) {
                    comp.addBinding(maybe.get());
                    LOG.debug("Added binding row for id='{}' action='{}' default='{}'", kbXml.id, kbXml.action, kbXml.defaultKey);
                } else {
                    // If manager doesn't have this id (shouldn't happen), log and skip
                    LOG.warn("KeyBind '{}' present in XML but missing in manager; skipping row.", kbXml.id);
                }
            }
        } catch (Exception ex) {
            LOG.warn("Exception while iterating/adding keybind rows; falling back to rebuildRows()", ex);
            try {
                comp.rebuildRows();
            } catch (Exception ex2) {
                LOG.error("Fallback rebuildRows() also failed", ex2);
            }
        }

        // Finalize layout — choose width/height by count of binds (sane defaults)
        int bindsCount = model.getBindsSafe().size();
        float width = Math.min(900f, 520f);
        float perRow = 46f;
        float height = Math.max(120f, bindsCount * perRow + 24f);
        try {
            comp.finalizeLayout(width, height);
        } catch (Exception ex) {
            LOG.warn("Failed to finalize layout for KeyBindingsComponent", ex);
        }

        // Position component using fields inherited from ComponentXml (x/y/alignX) if present
        try {
            // ComponentXml usually exposes x,y,alignX as public strings/numbers — MenuUtils handles parsing.
            // Here we defensively read via fields; if missing, calculatePosition will default to (0,0).
            String xStr = null, yStr = null, alignX = null;
            try {
                var cls = model.getClass();
                var fx = cls.getField("x");
                var fy = cls.getField("y");
                var fa = cls.getField("alignX");
                Object ox = fx.get(model);
                Object oy = fy.get(model);
                Object oa = fa.get(model);
                xStr = (ox != null) ? String.valueOf(ox) : null;
                yStr = (oy != null) ? String.valueOf(oy) : null;
                alignX = (oa instanceof String) ? (String) oa : null;
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // fields not present — ignore, MenuUtils will handle nulls
            }

            Vector2f pos = MenuUtils.calculatePosition(xStr, yStr, alignX, app.getCamera());
            pos.x -= width / 2f; // center by width
            comp.getNode().setLocalTranslation(pos.x, pos.y, 0f);
        } catch (Exception ex) {
            LOG.warn("Failed to position KeyBindingsComponent — attaching at (0,0)", ex);
            comp.getNode().setLocalTranslation(0f, 0f, 0f);
        }

        // Attach and return
        parent.attachChild(comp.getNode());
        return comp;
    }
}
