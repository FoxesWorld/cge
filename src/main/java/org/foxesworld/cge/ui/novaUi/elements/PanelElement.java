package org.foxesworld.cge.ui.novaUi.elements;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.novaUi.AbstractUIElement;
import org.foxesworld.cge.ui.novaUi.elements.image.ImageElement;
import org.foxesworld.cge.ui.novaUi.elements.progress.ProgressElement;
import org.foxesworld.cge.ui.novaUi.elements.text.TextElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

import static java.lang.Float.parseFloat;

/**
 * PanelElement — a container that automatically sizes itself to fit all its children,
 * includes them entirely within its bounds, and supports flexible registration of
 * properties and metrics for children. Now also supports a built-in layout manager.
 *
 * Supported properties (via setProperty or XML attributes):
 *  • bgColor    : background color as "r,g,b,a"
 *  • width      : fixed width (float) or "auto" (to size to children)
 *  • height     : fixed height (float) or "auto" (to size to children)
 *  • margin     : margin outside this panel (affects parent positioning)
 *  • padding    : padding inside this panel (controls spacing from edges)
 *  • align      : alignment relative to parent ("none" [default], "vertical", or "horizontal")
 *  • spacing    : spacing in pixels between children (float)
 *
 * ChildMetrics registry must include any UIElement subclass whose raw position and
 * size are used for automatic layout and sizing.
 */
public class PanelElement extends AbstractUIElement {
    private static final Logger logger = LoggerFactory.getLogger(PanelElement.class);

    private final CalistaGameEngine calistaGameEngine;
    private final List<UIElement> children = new ArrayList<>();

    private float margin  = 0f;
    private float padding = 0f;
    private ColorRGBA bgColor = new ColorRGBA(0f, 0f, 0f, 0.5f);

    private boolean autoWidth  = false;
    private boolean autoHeight = false;
    private float fixedWidth   = 0f;
    private float fixedHeight  = 0f;

    private String align = "top-left";

    // New layout properties:
    //   "none"       — no automatic layout; children positioned by their own rawX/rawY
    //   "vertical"   — stack children top-to-bottom
    //   "horizontal" — stack children left-to-right
    private String layout  = "none";
    private float spacing  = 0f;

    private Geometry bgGeom;

    /** Property handlers: key → Consumer<String> */
    private final Map<String, Consumer<String>> propertyHandlers = new HashMap<>();

    /** Registry of ChildMetrics for known UIElement subclasses */
    private final List<Map.Entry<Class<? extends UIElement>, ChildMetrics>> metricsRegistry = new ArrayList<>();

    public PanelElement(CalistaGameEngine calistaGameEngine, String id, PanelElement parent) {
        super();
        this.calistaGameEngine = calistaGameEngine;
        this.id = id;
        this.parentPanel = parent;
        this.node.setName("Panel_" + id);
        initPropertyHandlers();
        initMetricsRegistry();
        logger.debug("PanelElement created: id='{}'", id);
    }

    private void initPropertyHandlers() {
        propertyHandlers.put("bgColor", v -> {
            bgColor = parseColorOrDefault(v, bgColor);
            if (bgGeom != null) {
                bgGeom.getMaterial().setColor("Color", bgColor);
            }
        });
        propertyHandlers.put("width", v -> {
            if ("auto".equalsIgnoreCase(v)) {
                setAutoWidth(true);
            } else {
                setFixedWidth(parseFloatOrDefault(v, fixedWidth));
            }
            recalcAndRepositionSelfAndAncestors();
        });
        propertyHandlers.put("height", v -> {
            if ("auto".equalsIgnoreCase(v)) {
                setAutoHeight(true);
            } else {
                setFixedHeight(parseFloatOrDefault(v, fixedHeight));
            }
            recalcAndRepositionSelfAndAncestors();
        });
        propertyHandlers.put("margin", v -> {
            setMargin(parseFloatOrDefault(v, margin));
            recalcAndRepositionSelfAndAncestors();
        });
        propertyHandlers.put("padding", v -> {
            setPadding(parseFloatOrDefault(v, padding));
            recalcAndRepositionSelfAndAncestors();
        });
        propertyHandlers.put("align", v -> {
            setAlign(v);
            recalcAndRepositionSelfAndAncestors();
        });
        propertyHandlers.put("layout", v -> {
            String val = v.trim().toLowerCase();
            if (Set.of("none", "vertical", "horizontal").contains(val)) {
                setLayout(val);
            } else {
                logger.warn("Panel '{}' invalid layout '{}', defaulting to 'none'", id, v);
                setLayout("none");
            }
            recalcAndRepositionSelfAndAncestors();
        });
        propertyHandlers.put("spacing", v -> {
            setSpacing(parseFloatOrDefault(v, spacing));
            recalcAndRepositionSelfAndAncestors();
        });
    }

    private void initMetricsRegistry() {
        metricsRegistry.add(new AbstractMap.SimpleEntry<>(TextElement.class, new ChildMetrics() {
            public float getRawX(UIElement ue)    { return ((TextElement) ue).getRawPosX(); }
            public float getRawY(UIElement ue)    { return ((TextElement) ue).getRawPosY(); }
            public float getWidth(UIElement ue)   { return ((TextElement) ue).getWidth(); }
            public float getHeight(UIElement ue)  { return ((TextElement) ue).getHeight(); }
        }));
        metricsRegistry.add(new AbstractMap.SimpleEntry<>(ImageElement.class, new ChildMetrics() {
            public float getRawX(UIElement ue)    { return ((ImageElement) ue).getRawPosX(); }
            public float getRawY(UIElement ue)    { return ((ImageElement) ue).getRawPosY(); }
            public float getWidth(UIElement ue)   { return ((ImageElement) ue).getWidth(); }
            public float getHeight(UIElement ue)  { return ((ImageElement) ue).getHeight(); }
        }));
        metricsRegistry.add(new AbstractMap.SimpleEntry<>(PanelElement.class, new ChildMetrics() {
            public float getRawX(UIElement ue)    { return ((PanelElement) ue).getRawPosX(); }
            public float getRawY(UIElement ue)    { return ((PanelElement) ue).getRawPosY(); }
            public float getWidth(UIElement ue)   { return ((PanelElement) ue).getCurrentWidth(); }
            public float getHeight(UIElement ue)  { return ((PanelElement) ue).getCurrentHeight(); }
        }));
        metricsRegistry.add(new AbstractMap.SimpleEntry<>(ProgressElement.class, new ChildMetrics() {
            public float getRawX(UIElement ue)    { return ((ProgressElement) ue).getRawPosX(); }
            public float getRawY(UIElement ue)    { return ((ProgressElement) ue).getRawPosY(); }
            public float getWidth(UIElement ue)   { return ((ProgressElement) ue).getWidth(); }
            public float getHeight(UIElement ue)  { return ((ProgressElement) ue).getHeight(); }
        }));
    }

    /** Set panel margin. */
    public void setMargin(float m) {
        this.margin = m;
        logger.debug("Panel '{}' margin set to {}", id, m);
    }

    /** Set panel padding (space inside panel edges). */
    public void setPadding(float p) {
        this.padding = p;
        logger.debug("Panel '{}' padding set to {}", id, p);
    }

    /** Set panel background color. */
    public void setBgColor(ColorRGBA color) {
        this.bgColor = color.clone();
        if (bgGeom != null) {
            bgGeom.getMaterial().setColor("Color", bgColor);
        }
    }

    /** Set fixed width; disables autoWidth. */
    public void setFixedWidth(float w) {
        this.fixedWidth = w;
        this.autoWidth = false;
        logger.debug("Panel '{}' fixedWidth set to {}", id, w);
    }

    /** Set fixed height; disables autoHeight. */
    public void setFixedHeight(float h) {
        this.fixedHeight = h;
        this.autoHeight = false;
        logger.debug("Panel '{}' fixedHeight set to {}", id, h);
    }

    /** Enable or disable autoWidth. */
    public void setAutoWidth(boolean auto) {
        this.autoWidth = auto;
        logger.debug("Panel '{}' autoWidth set to {}", id, auto);
    }

    /** Enable or disable autoHeight. */
    public void setAutoHeight(boolean auto) {
        this.autoHeight = auto;
        logger.debug("Panel '{}' autoHeight set to {}", id, auto);
    }

    /** Set alignment relative to parent. */
    public void setAlign(String a) {
        this.align = a.trim().toLowerCase();
        logger.debug("Panel '{}' align set to '{}'", id, a);
    }

    /** Set layout mode: "none", "vertical", or "horizontal". */
    public void setLayout(String layout) {
        this.layout = layout;
        logger.debug("Panel '{}' layout set to '{}'", id, layout);
    }

    /** Set spacing (pixels) between children when layout is not "none". */
    public void setSpacing(float s) {
        this.spacing = s;
        logger.debug("Panel '{}' spacing set to {}", id, s);
    }

    @Override
    public boolean hasOwnAlign() {
        return false;
    }

    @Override
    public String getOwnAlign() {
        return null;
    }

    @Override
    public PanelElement getParentPanel() {
        return parentPanel;
    }

    @Override
    public void setProperty(String key, String value) {
        Consumer<String> handler = propertyHandlers.get(key);
        if (handler != null) {
            handler.accept(value);
        } else {
            logger.warn("Panel '{}' unknown property '{}'", id, key);
        }
    }

    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        super.setOnClickHandler(methodName, eventHandlerTarget);
    }

    /** Build the background quad (1×1), material, and attach to node. */
    public void buildBackgroundGeom() {
        if (bgGeom == null) {
            Quad quad = new Quad(1f, 1f);
            bgGeom = new Geometry("BG_" + id, quad);
            Material mat = new Material(calistaGameEngine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
            mat.setColor("Color", bgColor);
            mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            bgGeom.setMaterial(mat);
            node.attachChild(bgGeom);
        }
    }

    /** Add a child. Child’s parentPanel is set, and child node is attached. */
    public void addChild(UIElement child) {
        children.add(child);
        if (child instanceof AbstractUIElement) {
            ((AbstractUIElement) child).setParentPanel(this);
        }
        node.attachChild(child.getNode());
        recalcAndRepositionSelfAndAncestors();
    }

    /** Remove a child and recalc layout. */
    public void removeChild(UIElement child) {
        children.remove(child);
        node.detachChild(child.getNode());
        recalcAndRepositionSelfAndAncestors();
    }

    /**
     * Recalculates panel size so it fully contains all children:
     *   - If layout is not "none", children are automatically laid out.
     *   - Background is resized, and children are positioned.
     */
    public void recomputeSizeAndRepositionChildren() {
        // 1) Layout pass
        if (!"none".equals(layout)) applyLayoutToChildren();

        // 2) Compute content extents
        float contentMaxX = 0f;
        float contentMaxY = 0f;
        for (UIElement ue : children) {
            ChildMetrics metrics = lookupMetrics(ue);
            if (metrics == null) continue;

            float rawX = metrics.getRawX(ue);
            float rawY = metrics.getRawY(ue);
            float w    = metrics.getWidth(ue);
            float h    = metrics.getHeight(ue);

            contentMaxX = Math.max(contentMaxX, rawX + w);
            contentMaxY = Math.max(contentMaxY, rawY + h);
        }

        // 3) Determine panel size
        float newW = autoWidth  ? (contentMaxX + padding) : fixedWidth;
        float newH = autoHeight ? (contentMaxY + padding) : fixedHeight;

        // 4) Build or resize background
        if (bgGeom == null) buildBackgroundGeom();
        bgGeom.setMesh(new Quad(newW, newH));
        bgGeom.setLocalTranslation(0f, 0f, 0f);

        // 5) Position children
        for (UIElement ue : children) positionChild(ue, newW, newH);
    }

    private ChildMetrics lookupMetrics(UIElement ue) {
        for (var entry : metricsRegistry) {
            if (entry.getKey().isInstance(ue)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void positionChild(UIElement ue, float panelW, float panelH) {
        if (ue.hasOwnAlign()) {
            applyOwnAlign(ue, panelW, panelH);
            return;
        }
        ChildMetrics metrics = lookupMetrics(ue);
        if (metrics == null) return;

        float rawX = metrics.getRawX(ue);
        float rawY = metrics.getRawY(ue);
        float h    = metrics.getHeight(ue);

        float px = rawX + padding;
        float py = panelH - padding - rawY - h;
        ue.getNode().setLocalTranslation(px, py, 0f);
    }

    private void applyOwnAlign(UIElement ue, float panelW, float panelH) {
        String al = ue.getOwnAlign().trim().toLowerCase();
        ChildMetrics metrics = lookupMetrics(ue);
        if (metrics == null) return;

        float ew = metrics.getWidth(ue);
        float eh = metrics.getHeight(ue);
        float px, py;

        if (al.contains(",")) {
            String[] coords = al.split(",");
            try {
                px = padding + parseFloat(coords[0].trim());
                py = panelH - padding - parseFloat(coords[1].trim());
            } catch (Exception ex) {
                logger.warn("Panel '{}' invalid ownAlign coords: {}", id, al);
                px = padding;
                py = panelH - padding;
            }
        } else {
            switch (al) {
                case "top-left":    px = padding;                py = panelH - padding;               break;
                case "top-right":   px = panelW - padding - ew;   py = panelH - padding;               break;
                case "bottom-left": px = padding;                py = eh + padding;                   break;
                case "bottom-right":px = panelW - padding - ew;   py = eh + padding;                   break;
                case "center":      px = (panelW - ew) / 2f;      py = (panelH + eh) / 2f;             break;
                default:
                    logger.warn("Panel '{}' unknown ownAlign '{}'; defaulting to top-left", id, al);
                    px = padding;
                    py = panelH - padding;
            }
        }
        ue.getNode().setLocalTranslation(px, py, 0f);
    }

    /**
     * Attach this panel to parent or GUI root and position it by align & margin.
     */
    public void repositionRecursively(float parentW, float parentH) {
        if (parentPanel != null) {
            parentW = parentPanel.getCurrentWidth();
            parentH = parentPanel.getCurrentHeight();
        }

        float w = getCurrentWidth();
        float h = getCurrentHeight();
        float px, py;

        String a = align.toLowerCase();
        if (a.contains(",")) {
            String[] coords = a.split(",");
            try {
                px = margin + parseFloat(coords[0].trim());
                py = parentH - margin - parseFloat(coords[1].trim());
            } catch (Exception ex) {
                logger.warn("Panel '{}' invalid align coords: {}", id, align);
                px = margin;
                py = parentH - margin;
            }
        } else {
            switch (a) {
                case "top-left":    px = margin;                py = parentH - margin;             break;
                case "top-right":   px = parentW - margin - w;  py = parentH - margin;             break;
                case "bottom-left": px = margin;                py = h + margin;                   break;
                case "bottom-right":px = parentW - margin - w;  py = h + margin;                   break;
                case "center":      px = (parentW - w) / 2f;     py = (parentH + h) / 2f;           break;
                default:
                    logger.warn("Panel '{}' unknown align '{}'; defaulting to top-left", id, align);
                    px = margin;
                    py = parentH - margin;
            }
        }

        if (parentPanel == null) calistaGameEngine.getGuiNode().attachChild(node);
        else parentPanel.getNode().attachChild(node);
        node.setLocalTranslation(px, py, 0f);

        for (UIElement child : children) {
            if (child instanceof PanelElement) {
                ((PanelElement) child).repositionRecursively(0f, 0f);
            }
        }
    }

    /** Returns current panel width (from bgGeom). */
    public float getCurrentWidth() {
        if (bgGeom == null) return 0f;
        Mesh m = bgGeom.getMesh();
        return (m instanceof Quad) ? ((Quad) m).getWidth() : 0f;
    }

    /** Returns current panel height (from bgGeom). */
    public float getCurrentHeight() {
        if (bgGeom == null) return 0f;
        Mesh m = bgGeom.getMesh();
        return (m instanceof Quad) ? ((Quad) m).getHeight() : 0f;
    }

    private ColorRGBA parseColorOrDefault(String s, ColorRGBA def) {
        if (s == null || s.isEmpty()) return def.clone();
        String[] parts = s.split(",");
        if (parts.length != 4) { logger.warn("Panel '{}' invalid color '{}'", id, s); return def.clone(); }
        try {
            return new ColorRGBA(parseFloat(parts[0].trim()), parseFloat(parts[1].trim()),
                    parseFloat(parts[2].trim()), parseFloat(parts[3].trim()));
        } catch (NumberFormatException e) {
            logger.warn("Panel '{}' failed to parse color '{}'", id, s);
            return def.clone();
        }
    }

    private float parseFloatOrDefault(String s, float def) {
        if (s == null || s.isEmpty()) return def;
        try { return parseFloat(s); }
        catch (NumberFormatException e) {
            logger.warn("Panel '{}' cannot parse '{}' as float, using {}", id, s, def);
            return def;
        }
    }

    private void recalcAndRepositionSelfAndAncestors() {
        PanelElement current = this;
        while (current != null) {
            current.recomputeSizeAndRepositionChildren();
            if (current.parentPanel != null) {
                current.repositionRecursively(current.parentPanel.getCurrentWidth(),
                        current.parentPanel.getCurrentHeight());
            } else {
                current.repositionRecursively(calistaGameEngine.getCamera().getWidth(),
                        calistaGameEngine.getCamera().getHeight());
            }
            current = current.parentPanel;
        }
    }

    private void applyLayoutToChildren() {
        if ("vertical".equals(layout)) {
            float yCursor = padding;
            for (UIElement ue : children) {
                ChildMetrics m = lookupMetrics(ue);
                if (m == null) continue;
                float h = m.getHeight(ue);
                if (ue instanceof AbstractUIElement) {
                    ((AbstractUIElement) ue).setRawPosX(padding);
                    ((AbstractUIElement) ue).setRawPosY(yCursor);
                }
                yCursor += h + spacing;
            }
        } else if ("horizontal".equals(layout)) {
            float xCursor = padding;
            for (UIElement ue : children) {
                ChildMetrics m = lookupMetrics(ue);
                if (m == null) continue;
                float w = m.getWidth(ue);
                if (ue instanceof AbstractUIElement) {
                    ((AbstractUIElement) ue).setRawPosX(xCursor);
                    ((AbstractUIElement) ue).setRawPosY(padding);
                }
                xCursor += w + spacing;
            }
        }
    }

    public float getMargin() {
        return margin;
    }

    public float getPadding() {
        return padding;
    }

    public List<UIElement> getChildren() {
        return children;
    }
}
