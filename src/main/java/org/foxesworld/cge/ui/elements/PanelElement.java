package org.foxesworld.cge.ui.elements;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.AbstractUIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * PanelElement — контейнер, который автоматически расширяется под размеры своего содержимого,
 * полностью включает его внутри себя и поддерживает гибкую регистрацию свойств и метрик для детей.
 */
public class PanelElement extends AbstractUIElement {
    private static final Logger logger = LoggerFactory.getLogger(PanelElement.class);

    private final CalistaGameEngine calistaGameEngine;
    private final List<UIElement> children = new ArrayList<>();

    private float margin = 0f;
    private float padding = 0f;
    private ColorRGBA bgColor = new ColorRGBA(0f, 0f, 0f, 0.5f);

    private boolean autoWidth = false;
    private boolean autoHeight = false;

    private float fixedWidth = 0f;
    private float fixedHeight = 0f;

    private String align = "top-left";

    private Geometry bgGeom;

    /** Для регистрации обработчиков свойств: key → Consumer<String> */
    private final Map<String, Consumer<String>> propertyHandlers = new HashMap<>();

    /** Регистрация метрик для разных классов UIElement */
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
        propertyHandlers.put("bgColor", v -> setBgColor(parseColor(v)));
        propertyHandlers.put("width", v -> {
            if ("auto".equalsIgnoreCase(v)) setAutoWidth(true);
            else setFixedWidth(Float.parseFloat(v));
        });
        propertyHandlers.put("height", v -> {
            if ("auto".equalsIgnoreCase(v)) setAutoHeight(true);
            else setFixedHeight(Float.parseFloat(v));
        });
        propertyHandlers.put("margin", v -> setMargin(Float.parseFloat(v)));
        propertyHandlers.put("padding", v -> setPadding(Float.parseFloat(v)));
        propertyHandlers.put("align", this::setAlign);
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
    }

    public void setMargin(float m) {
        this.margin = m;
        logger.debug("Panel '{}' margin set to {}", id, m);
    }

    public void setPadding(float p) {
        this.padding = p;
        logger.debug("Panel '{}' padding set to {}", id, p);
    }

    public void setBgColor(ColorRGBA color) {
        this.bgColor = color.clone();
        if (bgGeom != null) {
            bgGeom.getMaterial().setColor("Color", bgColor);
        }
    }

    public void setFixedWidth(float w) {
        this.fixedWidth = w;
        this.autoWidth = false;
    }

    public void setFixedHeight(float h) {
        this.fixedHeight = h;
        this.autoHeight = false;
    }

    public void setAutoWidth(boolean auto) {
        this.autoWidth = auto;
    }

    public void setAutoHeight(boolean auto) {
        this.autoHeight = auto;
    }

    public void setAlign(String a) {
        this.align = a;
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

    public void buildBackgroundGeom() {
        Quad quad = new Quad(1f, 1f);
        bgGeom = new Geometry("BG_" + id, quad);
        Material mat = new Material(calistaGameEngine.getAssetManager(), "Common/MatDefs/Gui/Gui.j3md");
        mat.setColor("Color", bgColor);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        bgGeom.setMaterial(mat);
        node.attachChild(bgGeom);
    }

    public void addChild(UIElement child) {
        children.add(child);
        if (child instanceof AbstractUIElement) {
            ((AbstractUIElement) child).setParentPanel(this);
        }
        node.attachChild(child.getNode());
    }

    public void removeChild(UIElement child) {
        children.remove(child);
        node.detachChild(child.getNode());
    }

    /**
     * Пересчитывает размер панели так, чтобы полностью вместить всех детей:
     * для каждого ребенка учитывается rawX + ширина и rawY + высота (от _верхнего_ края).
     * newWidth = max(rawX + childWidth) + padding
     * newHeight = max(rawY + childHeight) + padding
     *
     * После этого ставит фон (bgGeom) нужного размера и позиционирует детей:
     * каждый ребенок располагается так, что его верхний левый угол == (rawX + padding, newHeight - padding - rawY).
     */
    public void recomputeSizeAndRepositionChildren() {
        float contentMaxX = 0f;
        float contentMaxY = 0f;

        // 1) Вычисляем максимальный правый и нижний край содержимого относительно верхнего левого
        for (UIElement ue : children) {
            ChildMetrics metrics = lookupMetrics(ue);
            if (metrics == null) continue;

            float rawX = metrics.getRawX(ue);
            float rawY = metrics.getRawY(ue);
            float w = metrics.getWidth(ue);
            float h = metrics.getHeight(ue);

            // rawY отсчитывается от верхнего края; rawY + h = расстояние от верха до нижнего края ребенка
            contentMaxX = Math.max(contentMaxX, rawX + w);
            contentMaxY = Math.max(contentMaxY, rawY + h);
        }

        // 2) Итоговые размеры панели
        float newW = autoWidth ? (contentMaxX + padding) : fixedWidth;
        float newH = autoHeight ? (contentMaxY + padding) : fixedHeight;

        // 3) Обновляем bgGeom: если не создан, создаем; иначе меняем размер
        if (bgGeom == null) {
            buildBackgroundGeom();
        }
        if (bgGeom != null) {
            bgGeom.setMesh(new Quad(newW, newH));
            bgGeom.setLocalTranslation(0f, 0f, 0f);
        }

        // 4) Позиционируем детей. Верхний левый угол ребенка => (rawX + padding, newH - padding - rawY)
        for (UIElement ue : children) {
            positionChild(ue, newW, newH);
        }
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
        float h = metrics.getHeight(ue);

        // px = rawX + padding
        // py = panelH - padding - rawY - h    (чтобы верхний левый угол ребенка оказался выше нижнего края)
        float px = rawX + padding;
        float py = panelH - padding - rawY - h;
        ue.getNode().setLocalTranslation(px, py, 0f);
    }

    private void applyOwnAlign(UIElement ue, float panelW, float panelH) {
        String al = ue.getOwnAlign().trim().toLowerCase();
        Node n = ue.getNode();
        ChildMetrics metrics = lookupMetrics(ue);
        if (metrics == null) return;

        float ew = metrics.getWidth(ue);
        float eh = metrics.getHeight(ue);

        float px = 0f, py = 0f;
        if ("top-left".equals(al)) {
            px = padding;
            py = panelH - padding;
        } else if ("top-right".equals(al)) {
            px = panelW - padding - ew;
            py = panelH - padding;
        } else if ("bottom-left".equals(al)) {
            px = padding;
            py = eh + padding;
        } else if ("bottom-right".equals(al)) {
            px = panelW - padding - ew;
            py = eh + padding;
        } else if ("center".equals(al)) {
            px = (panelW - ew) / 2f;
            py = (panelH + eh) / 2f;
        } else if (al.contains(",")) {
            String[] coords = al.split(",");
            try {
                px = padding + Float.parseFloat(coords[0].trim());
                py = panelH - padding - Float.parseFloat(coords[1].trim());
            } catch (NumberFormatException ex) {
                logger.warn("Panel '{}' invalid ownAlign coords: {}", id, al);
            }
        } else {
            logger.warn("Panel '{}' unknown ownAlign '{}'", id, al);
        }
        n.setLocalTranslation(px, py, 0f);
    }

    /**
     * Привязывает саму панель к экрану (если parentPanel==null) или к родительской панели.
     * Вычисляем localTranslation() у узла panelNode по align + margin.
     */
    public void repositionRecursively(float parentW, float parentH) {
        if (parentPanel != null) {
            parentW = parentPanel.getCurrentWidth();
            parentH = parentPanel.getCurrentHeight();
        }

        float w = getCurrentWidth();
        float h = getCurrentHeight();
        float px = 0f, py = 0f;

        String a = align.trim().toLowerCase();
        if ("top-left".equals(a)) {
            px = margin;
            py = parentH - margin;
        } else if ("top-right".equals(a)) {
            px = parentW - margin - w;
            py = parentH - margin;
        } else if ("bottom-left".equals(a)) {
            px = margin;
            py = h + margin;
        } else if ("bottom-right".equals(a)) {
            px = parentW - margin - w;
            py = h + margin;
        } else if ("center".equals(a)) {
            px = (parentW - w) / 2f;
            py = (parentH + h) / 2f;
        } else if (a.contains(",")) {
            String[] coords = a.split(",");
            try {
                px = margin + Float.parseFloat(coords[0].trim());
                py = parentH - margin - Float.parseFloat(coords[1].trim());
            } catch (NumberFormatException ex) {
                logger.warn("Panel '{}' invalid align coords: {}", id, align);
            }
        } else {
            logger.warn("Panel '{}' unknown align '{}'", id, align);
        }

        if (parentPanel == null) {
            calistaGameEngine.getGuiNode().attachChild(node);
            node.setLocalTranslation(px, py, 0f);
        } else {
            parentPanel.getNode().attachChild(node);
            node.setLocalTranslation(px, py, 0f);
        }

        for (UIElement ue : children) {
            if (ue instanceof PanelElement) {
                ((PanelElement) ue).repositionRecursively(0f, 0f);
            }
        }
    }

    public float getCurrentWidth() {
        if (bgGeom == null) return 0f;
        Mesh m = bgGeom.getMesh();
        if (m instanceof Quad) {
            return ((Quad) m).getWidth();
        }
        return 0f;
    }

    public float getCurrentHeight() {
        if (bgGeom == null) return 0f;
        Mesh m = bgGeom.getMesh();
        if (m instanceof Quad) {
            return ((Quad) m).getHeight();
        }
        return 0f;
    }

    public float getRawPosX() {
        return 0f;
    }

    public float getRawPosY() {
        return 0f;
    }

    private ColorRGBA parseColor(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            logger.warn("Panel '{}' invalid color string '{}'", id, value);
            return bgColor;
        }
        try {
            float r = Float.parseFloat(parts[0].trim());
            float g = Float.parseFloat(parts[1].trim());
            float b = Float.parseFloat(parts[2].trim());
            float a = Float.parseFloat(parts[3].trim());
            return new ColorRGBA(r, g, b, a);
        } catch (NumberFormatException e) {
            logger.warn("Panel '{}' failed to parse color '{}'", id, value);
            return bgColor;
        }
    }
}
