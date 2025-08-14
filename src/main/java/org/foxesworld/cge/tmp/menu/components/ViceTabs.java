package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.Orientation;
import org.foxesworld.cge.tmp.menu.xml.TabXml;

import java.util.ArrayList;
import java.util.List;


public final class ViceTabs extends UIComponent implements InteractiveComponent {

    private record Tab(ViceButton button, Node contentNode, List<UIComponent> content) {}

    //private final Node tabsNode = new Node("ViceTabs");
    private final List<Tab> tabs = new ArrayList<>();
    private final BuildContext buildContext;
    private final ViceButton.Style buttonStyle;
    private final Orientation orientation;

    private final Geometry tabBarBackground;
    private final Geometry contentBackground;

    private int activeTabIndex = -1;
    private ViceSlider activeSlider = null;

    // размеры контента (в локальных пикселях GUI, уже с учётом dpiScale где применяется)
    private float contentWidth = 0f;
    private float contentHeight = 0f;

    // размер панели кнопок (ширина, высота)
    private final Vector2f buttonBarSize = new Vector2f();

    // рабочие параметры (не менять жестко — лучше через сеттеры)
    private float preferredButtonWidth = 280f;
    private float preferredButtonHeight = 45f;
    private float minButtonWidth = 48f;
    private float minButtonHeight = 20f;
    private float spacing = 10f;
    private float padding = 10f;

    // вспомогательные переменные преобразований
    private final Vector3f tmpWorld = new Vector3f();
    private final Vector3f tmpLocal = new Vector3f();
    private final Vector2f tmp2 = new Vector2f();

    private int hoveredButtonIndex = -1;

    public ViceTabs(String id, BuildContext buildContext, ViceButton.Style buttonStyle, Orientation orientation) {
        super(id);
        this.buildContext = buildContext;
        this.buttonStyle = buttonStyle != null ? buttonStyle : ViceButton.Style.getViceStyle();
        this.buttonStyle.setCornerRadius(0);
        this.buttonStyle.setBackgroundColor("#ffffff");
        this.buttonStyle.setHoverBackgroundColor("#000000");
        this.orientation = orientation;

        this.tabBarBackground = createBackground(new ColorRGBA(0.05f, 0.05f, 0.05f, 0.75f), "TabBarBG");
        this.contentBackground = createBackground(new ColorRGBA(0.1f, 0.1f, 0.1f, 0.65f), "ContentBG");

        this.attachChild(tabBarBackground);
        this.attachChild(contentBackground);
    }

    private Geometry createBackground(ColorRGBA color, String name) {
        Material mat = new Material(buildContext.app().getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        Geometry bg = new Geometry(name, new Quad(1, 1));
        bg.setMaterial(mat);
        return bg;
    }

    /**
     * Добавляет вкладку. ВАЖНО: contentNode НЕ будет автоматически прикреплён к сцене —
     * это делается только для активной вкладки в selectTab(...)
     */
    public void addTab(TabXml tabXml, Node content, List<UIComponent> createdObjects) {
        int index = tabs.size();
        ViceButton button = new ViceButton("tab-" + index, buildContext.app().getAssetManager(), tabXml.title, buttonStyle, () -> selectTab(index), tabXml.iconPath, tabXml.iconSize);
        // store tab — but do NOT attach content to scene yet
        tabs.add(new Tab(button, content, new ArrayList<>(createdObjects)));
        this.attachChild(button.getNode());
    }

    /**
     * Подготовить финальную раскладку. ВАЖНО: contentNodes не будут автоматически прикреплены здесь —
     * прикрепление произойдёт в selectTab для активной вкладки.
     */
    public void finalizeLayout(float totalWidth, float totalHeight) {
        float dpi = Math.max(1f, dpiScale);

        float pad = padding * dpi;
        float sp = spacing * dpi;
        float prefBW = preferredButtonWidth * dpi;
        float prefBH = preferredButtonHeight * dpi;
        float minBW = minButtonWidth * dpi;
        float minBH = minButtonHeight * dpi;

        if (orientation == Orientation.HORIZONTAL) {
            int n = Math.max(1, tabs.size());
            float availableForButtons = Math.max(0f, totalWidth - pad * 2);
            float buttonW = prefBW;
            float totalNeeded = n * prefBW + (n - 1) * sp;
            if (totalNeeded > availableForButtons) {
                float candidate = (availableForButtons - (n - 1) * sp) / (float) n;
                buttonW = Math.max(minBW, candidate);
            }
            float buttonH = prefBH;
            float barH = buttonH + pad * 2;
            float contentH = Math.max(0f, totalHeight - barH);
            float contentW = totalWidth;

            this.contentWidth = contentW;
            this.contentHeight = contentH;
            this.buttonBarSize.set(totalWidth, barH);

            float offsetX = pad + Math.max(0f, (availableForButtons - (n * buttonW + (n - 1) * sp)) / 2f);
            float x = offsetX;
            float y = pad;

            for (Tab t : tabs) {
                t.button().setSize(buttonW, buttonH);
                t.button().setPosition(x, totalHeight - barH + y);
                x += buttonW + sp;
            }

            ((Quad) tabBarBackground.getMesh()).updateGeometry(totalWidth, barH);
            tabBarBackground.setLocalTranslation(0, totalHeight - barH, 0);

            ((Quad) contentBackground.getMesh()).updateGeometry(contentW, contentH);
            contentBackground.setLocalTranslation(0, 0, -1);

            // set content nodes' local translation but DO NOT attach here — attach only active one in selectTab
            for (Tab t : tabs) {
                t.contentNode().setLocalTranslation(0, 0, 0); // content will appear at origin of content area
            }

        } else {
            int n = Math.max(1, tabs.size());
            float availableForButtons = Math.max(0f, totalHeight - pad * 2);
            float buttonH = preferredButtonHeight * dpi;
            float totalNeeded = n * buttonH + (n - 1) * sp;
            if (totalNeeded > availableForButtons) {
                float candidate = (availableForButtons - (n - 1) * sp) / (float) n;
                buttonH = Math.max(minBH, candidate);
            }
            float buttonW = preferredButtonWidth * dpi;
            float barW = buttonW + pad * 2;
            float contentW = Math.max(0f, totalWidth - barW);
            float contentH = totalHeight;

            this.contentWidth = contentW;
            this.contentHeight = contentH;
            this.buttonBarSize.set(barW, totalHeight);

            float offsetY = totalHeight - pad - buttonH;
            for (Tab t : tabs) {
                t.button().setSize(buttonW, buttonH);
                t.button().setPosition(pad, offsetY);
                offsetY -= (buttonH + sp);
            }

            ((Quad) tabBarBackground.getMesh()).updateGeometry(barW, totalHeight);
            tabBarBackground.setLocalTranslation(0, 0, 0);

            ((Quad) contentBackground.getMesh()).updateGeometry(contentW, contentH);
            contentBackground.setLocalTranslation(barW, 0, -1);

            for (Tab t : tabs) {
                t.contentNode().setLocalTranslation(barW, -15, 0);
            }
        }

        // ensure we have a valid active tab
        if (!tabs.isEmpty() && (activeTabIndex < 0 || activeTabIndex >= tabs.size())) {
            selectTab(0);
        }
    }

    /**
     * Выбирать вкладку. Предыдущая вкладка полностью отключается (детачится и деактивирует интерактивные элементы).
     * Новая вкладка прикрепляется и активируется.
     */
    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (index == activeTabIndex) return;

        int prev = activeTabIndex;
        activeTabIndex = index;

        // Deactivate previous tab: detach its contentNode and deactivate components
        if (prev >= 0 && prev < tabs.size()) {
            Tab prevTab = tabs.get(prev);
            try {
                // detach content node from tabsNode if attached
                if (prevTab.contentNode().getParent() == this) {
                    this.detachChild(prevTab.contentNode());
                }
            } catch (Exception ex) {
                // ignore detach failures
            }
            // deactivate interactive children
            for (UIComponent comp : prevTab.content()) {
                if (comp instanceof InteractiveComponent ic) {
                    try { ic.setActive(false); } catch (Exception ignored) {}
                }
                // defensively set cull hint to Always to be sure it's fully invisible (optional)
                try { comp.getNode().setCullHint(Node.CullHint.Always); } catch (Exception ignored) {}
            }
        }

        // Activate new tab: attach its content node and activate components
        Tab newTab = tabs.get(activeTabIndex);
        // Ensure the content node is attached exactly once
        if (newTab.contentNode().getParent() != this) {
            this.attachChild(newTab.contentNode());
        }

        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            boolean active = (i == activeTabIndex);
            t.button().setSelected(active);
            // button nodes remain attached; content nodes are managed above
            if (t.contentNode().getParent() == this) {
                t.contentNode().setCullHint(active ? Node.CullHint.Inherit : Node.CullHint.Always);
            } else {
                t.contentNode().setCullHint(Node.CullHint.Always);
            }

            for (UIComponent comp : t.content()) {
                if (comp instanceof InteractiveComponent ic) {
                    try { ic.setActive(active); } catch (Exception ignored) {}
                }
                // For additional safety, set cull hints for each component node when not active
                try {
                    comp.getNode().setCullHint(active ? Node.CullHint.Inherit : Node.CullHint.Always);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void update(float tpf) {
        // update only buttons + active tab content components (if needed)
        for (Tab t : tabs) t.button().update(tpf);

        Tab active = getActiveTab();
        if (active != null) {
            for (UIComponent c : active.content()) {
                try { c.update(tpf); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Обновление hover-состояния кнопок (как раньше).
     */
    public void handleMouseMove(Vector2f cursor) {
        int foundIndex = -1;
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            if (t.button().intersects(cursor)) {
                foundIndex = i;
                break;
            }
        }

        if (foundIndex == hoveredButtonIndex) return;

        if (hoveredButtonIndex >= 0 && hoveredButtonIndex < tabs.size()) {
            tabs.get(hoveredButtonIndex).button().setHovered(false);
        }

        if (foundIndex >= 0) {
            tabs.get(foundIndex).button().setHovered(true);
            buildContext.app().getSoundManager().play("ui.toggle");
        }

        hoveredButtonIndex = foundIndex;
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        // update hover
        handleMouseMove(cursor);

        // check buttons first
        for (Tab t : tabs) {
            if (t.button().intersects(cursor)) {
                t.button().executeAction();
                return;
            }
        }

        // forward only to active content (components in other tabs are detached/deactivated)
        Tab active = getActiveTab();
        if (active == null) return;

        for (Object item : active.content()) {
            if (item instanceof UIComponent mc && item instanceof InteractiveComponent ic) {
                Node itemNode = mc.getNode();
                tmpWorld.set(cursor.x, cursor.y, 0f);
                itemNode.worldToLocal(tmpWorld, tmpLocal);
                tmp2.set(tmpLocal.x, tmpLocal.y);

                if (ic instanceof ViceSlider slider && slider.intersects(tmp2)) {
                    activeSlider = slider;
                    slider.handleDrag(tmp2);
                    return;
                } else if (ic instanceof ViceCheckbox checkbox && checkbox.intersects(tmp2)) {
                    checkbox.toggle();
                    return;
                } else {
                    if (((UIComponent) ic).intersects(tmp2)) {
                        ic.handleMousePress(tmp2);
                        return;
                    }
                }
            } else if (item instanceof InteractiveComponent ic) {
                // fallback: test against contentNode-local coords
                Node contentNode = active.contentNode();
                tmpWorld.set(cursor.x, cursor.y, 0f);
                contentNode.worldToLocal(tmpWorld, tmpLocal);
                tmp2.set(tmpLocal.x, tmpLocal.y);

                if (ic.intersects(tmp2)) {
                    ic.handleMousePress(tmp2);
                    return;
                }
            }
        }
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {
        handleMouseMove(cursor);

        if (activeSlider == null) return;
        Tab active = getActiveTab();
        if (active == null) return;

        Node sliderNode = activeSlider.getNode();
        tmpWorld.set(cursor.x, cursor.y, 0f);
        sliderNode.worldToLocal(tmpWorld, tmpLocal);
        tmp2.set(tmpLocal.x, tmpLocal.y);
        activeSlider.handleDrag(tmp2);
    }

    @Override
    public void handleMouseRelease() {
        if (activeSlider != null) {
            activeSlider = null;
        }

        Tab active = getActiveTab();
        if (active == null) return;
        for (Object item : active.content()) {
            if (item instanceof InteractiveComponent ic) ic.handleMouseRelease();
        }
    }

    @Override
    public float getWidth() {
        return buttonBarSize.x + contentWidth;
    }

    @Override
    public float getHeight() {
        return Math.max(buttonBarSize.y, contentHeight);
    }

    @Override
    public void setSize(float width, float height) {
        finalizeLayout(width, height);
    }

    @Override
    public boolean intersects(Vector2f cursor) {
        Vector2f world = new Vector2f(this.getWorldTranslation().x, this.getWorldTranslation().y);
        float w = getWidth();
        float h = getHeight();
        return cursor.x >= world.x && cursor.x <= world.x + w &&
                cursor.y >= world.y && cursor.y <= world.y + h;
    }

    @Override
    public void setActive(boolean active) { /* nop */ }

    @Override
    public void setHovered(boolean hovered) { /* nop */ }


    private Tab getActiveTab() {
        return (activeTabIndex >= 0 && activeTabIndex < tabs.size()) ? tabs.get(activeTabIndex) : null;
    }

    // setters
    public void setPreferredButtonSize(float w, float h) { this.preferredButtonWidth = w; this.preferredButtonHeight = h; }
    public void setMinButtonSize(float w, float h) { this.minButtonWidth = w; this.minButtonHeight = h; }
    public void setSpacing(float spacing) { this.spacing = spacing; }
    public void setPadding(float padding) { this.padding = padding; }
}
