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
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.Orientation;
import org.foxesworld.cge.tmp.menu.xml.TabXml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Набор вкладок (Tab control) для кастомного GUI
 * <p>
 * Ключевые особенности и ожидания:
 * <ul>
 *     <li>Каждая вкладка содержит кнопку (ViceButton) и узел контента (Node) с дочерними UIComponent.</li>
 *     <li>Контент активной вкладки прикреплён к сцене, остальной контент — отключён (detach / cullHint = Always).</li>
 *     <li>Класс не предполагает параллельного доступа из нескольких потоков игрового движка — вызовы должны
 *     происходить в основном потоке рендеринга/логики.</li>
 * </ul>
 *
 * Улучшения по сравнению с оригиналом:
 * <ul>
 *     <li>Документация и Javadoc для публичных методов.</li>
 *     <li>Разбиение логики раскладки на методы для горизонтальной и вертикальной ориентации.</li>
 *     <li>Упрощённая и прозрачная работа с attach/detach узлов (safeAttach/safeDetach).</li>
 *     <li>Дополнительные геттеры/манипуляторы (removeTab, getTabCount, getActiveIndex).</li>
 *     <li>Минимальная оптимизация локальных временных векторов и предотвращение чрезмерных try/catch.
 *     </li>
 * </ul>
 */
public final class ViceTabs extends UIComponent implements InteractiveComponent {

    /** Внутренний контейнер вкладки. */
    private static final class Tab {
        final ViceButton button;
        final Node contentNode;
        final List<UIComponent> content;

        Tab(ViceButton button, Node contentNode, List<UIComponent> content) {
            this.button = Objects.requireNonNull(button);
            this.contentNode = Objects.requireNonNull(contentNode);
            this.content = (content == null) ? new ArrayList<>() : new ArrayList<>(content);
        }
    }

    private final List<Tab> tabs = new ArrayList<>();
    private final BuildContext buildContext;
    private final ViceButton.Style buttonStyle;
    private final Orientation orientation;

    private final Geometry tabBarBackground;
    private final Geometry contentBackground;

    private int activeTabIndex = -1;
    private ViceSlider activeSlider = null;

    // размеры контента в локальных GUI-пикселях (с учётом dpiScale)
    private float contentWidth = 0f;
    private float contentHeight = 0f;

    // размер панели кнопок
    private final Vector2f buttonBarSize = new Vector2f();

    // рабочие параметры — можно менять через сеттеры
    private float preferredButtonWidth = 280f;
    private float preferredButtonHeight = 45f;
    private float minButtonWidth = 48f;
    private float minButtonHeight = 20f;
    private float spacing = 10f;
    private float padding = 10f;

    // временные переменные для преобразований (чтобы не аллоцировать каждый кадр)
    private final Vector3f tmpWorld = new Vector3f();
    private final Vector3f tmpLocal = new Vector3f();
    private final Vector2f tmp2 = new Vector2f();

    private int hoveredButtonIndex = -1;

    // Z offsets for layering
    private static final float TAB_BAR_Z = 0f;
    private static final float CONTENT_BG_Z = -1f;

    /**
     * Конструктор.
     *
     * @param id          идентификатор компонента
     * @param buildContext контекст сборки (доступ к приложению, звукам и т.п.)
     * @param buttonStyle  стиль кнопки (если null — используется стиль по умолчанию)
     * @param orientation  ориентация панели (HORIZONTAL или VERTICAL)
     */
    public ViceTabs(String id, BuildContext buildContext, ViceButton.Style buttonStyle, Orientation orientation) {
        super(id);
        this.buildContext = Objects.requireNonNull(buildContext, "buildContext");
        // безопасно используем переданный стиль — если он null, берем дефолтный
        this.buttonStyle = (buttonStyle != null) ? buttonStyle : ViceButton.Style.getViceStyle();
        this.buttonStyle.setCornerRadius(0);
        this.buttonStyle.setBackgroundColor("#ffffff");
        this.buttonStyle.setHoverBackgroundColor("#000000");

        this.orientation = (orientation == null) ? Orientation.HORIZONTAL : orientation;

        this.tabBarBackground = createBackground(new ColorRGBA(0.05f, 0.05f, 0.05f, 0.75f), "TabBarBG");
        this.contentBackground = createBackground(new ColorRGBA(0.1f, 0.1f, 0.1f, 0.65f), "ContentBG");
        this.attachChild(tabBarBackground);
        this.attachChild(contentBackground);
    }

    private Geometry createBackground(ColorRGBA color, String name) {
        AssetManager am = buildContext.mainMenuAppState().getGameEngine().getAssetManager();
        Material mat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        Geometry bg = new Geometry(name, new Quad(1f, 1f));
        bg.setMaterial(mat);
        return bg;
    }


    public void addTab(TabXml tabXml, Node content, List<UIComponent> createdObjects) {
        int index = tabs.size();
        String name = "tab-" + index;
        ViceButton button = new ViceButton(name, buildContext.mainMenuAppState().getGameEngine().getAssetManager(), tabXml.title, buttonStyle,
                () -> selectTab(index), tabXml.iconPath, tabXml.iconSize);

        tabs.add(new Tab(button, content, createdObjects));
        this.attachChild(button.getNode());
    }

    public boolean removeTab(int index) {
        if (index < 0 || index >= tabs.size()) return false;
        Tab t = tabs.remove(index);
        safeDetachChild(t.contentNode);
        safeDetachChild(t.button.getNode());

        if (tabs.isEmpty()) {
            activeTabIndex = -1;
        } else {
            if (activeTabIndex == index) {
                selectTab(Math.max(0, index - 1));
            } else if (activeTabIndex > index) {
                activeTabIndex--;
            }
        }
        return true;
    }

    public void finalizeLayout(float totalWidth, float totalHeight) {
        final float dpi = Math.max(1f, dpiScale);
        final float pad = padding * dpi;
        final float sp = spacing * dpi;

        if (orientation == Orientation.HORIZONTAL) {
            layoutHorizontal(totalWidth, totalHeight, dpi, pad, sp);
        } else {
            layoutVertical(totalWidth, totalHeight, dpi, pad, sp);
        }

        // ensure we have a valid active tab
        if (!tabs.isEmpty() && (activeTabIndex < 0 || activeTabIndex >= tabs.size())) {
            selectTab(0);
        }
    }

    private void layoutHorizontal(float totalWidth, float totalHeight, float dpi, float pad, float sp) {
        int n = Math.max(1, tabs.size());
        float prefBW = preferredButtonWidth * dpi;
        float prefBH = preferredButtonHeight * dpi;
        float minBW = minButtonWidth * dpi;

        float availableForButtons = Math.max(0f, totalWidth - pad * 2f);
        float buttonW = prefBW;
        float totalNeeded = n * prefBW + (n - 1) * sp;
        if (totalNeeded > availableForButtons) {
            float candidate = (availableForButtons - (n - 1) * sp) / (float) n;
            buttonW = Math.max(minBW, candidate);
        }

        float buttonH = prefBH;
        float barH = buttonH + pad * 2f;
        float contentH = Math.max(0f, totalHeight - barH);
        float contentW = totalWidth;

        this.contentWidth = contentW;
        this.contentHeight = contentH;
        this.buttonBarSize.set(totalWidth, barH);

        float offsetX = pad + Math.max(0f, (availableForButtons - (n * buttonW + (n - 1) * sp)) / 2f);
        float x = offsetX;
        float y = pad;

        for (Tab t : tabs) {
            t.button.setSize(buttonW, buttonH);
            t.button.setPosition(x, totalHeight - barH + y);
            x += buttonW + sp;
        }

        updateBackgroundGeometry(tabBarBackground, totalWidth, barH, 0f, totalHeight - barH, TAB_BAR_Z);
        updateBackgroundGeometry(contentBackground, contentW, contentH, 0f, 0f, CONTENT_BG_Z);

        // ensure content nodes are placed at content origin (attach/detach handled in selectTab)
        for (Tab t : tabs) {
            t.contentNode.setLocalTranslation(0f, 0f, 0f);
        }
    }

    private void layoutVertical(float totalWidth, float totalHeight, float dpi, float pad, float sp) {
        int n = Math.max(1, tabs.size());
        float prefBW = preferredButtonWidth * dpi;
        float prefBH = preferredButtonHeight * dpi;
        float minBH = minButtonHeight * dpi;

        float availableForButtons = Math.max(0f, totalHeight - pad * 2f);
        float buttonH = prefBH;
        float totalNeeded = n * prefBH + (n - 1) * sp;
        if (totalNeeded > availableForButtons) {
            float candidate = (availableForButtons - (n - 1) * sp) / (float) n;
            buttonH = Math.max(minBH, candidate);
        }

        float buttonW = prefBW;
        float barW = buttonW + pad * 2f;
        float contentW = Math.max(0f, totalWidth - barW);
        float contentH = totalHeight;

        this.contentWidth = contentW;
        this.contentHeight = contentH;
        this.buttonBarSize.set(barW, totalHeight);

        float offsetY = totalHeight - pad - buttonH;
        for (Tab t : tabs) {
            t.button.setSize(buttonW, buttonH);
            t.button.setPosition(pad, offsetY);
            offsetY -= (buttonH + sp);
        }

        updateBackgroundGeometry(tabBarBackground, barW, totalHeight, 0f, 0f, TAB_BAR_Z);
        updateBackgroundGeometry(contentBackground, contentW, contentH, barW, 0f, CONTENT_BG_Z);

        for (Tab t : tabs) {
            // small vertical offset to avoid overlapping tab bar visually
            t.contentNode.setLocalTranslation(barW, -15f, 0f);
        }
    }

    private void updateBackgroundGeometry(Geometry geom, float w, float h, float tx, float ty, float tz) {
        Quad q = (Quad) geom.getMesh();
        q.updateGeometry(Math.max(0f, w), Math.max(0f, h));
        geom.setLocalTranslation(tx, ty, tz);
    }

    public void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (index == activeTabIndex) return;

        int prev = activeTabIndex;
        activeTabIndex = index;

        // deactivate previous tab
        if (prev >= 0 && prev < tabs.size()) {
            Tab prevTab = tabs.get(prev);
            safeDetachChild(prevTab.contentNode);
            for (UIComponent comp : prevTab.content) {
                if (comp instanceof InteractiveComponent ic) {
                    ic.setActive(false);
                }
                try {
                    comp.getNode().setCullHint(Node.CullHint.Always);
                } catch (Exception ignored) {
                }
            }
        }

        // activate new tab
        Tab newTab = tabs.get(activeTabIndex);
        safeAttachChild(newTab.contentNode);

        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            boolean active = (i == activeTabIndex);
            t.button.setSelected(active);

            if (t.contentNode.getParent() == this) {
                t.contentNode.setCullHint(active ? Node.CullHint.Inherit : Node.CullHint.Always);
            } else {
                t.contentNode.setCullHint(Node.CullHint.Always);
            }

            for (UIComponent comp : t.content) {
                if (comp instanceof InteractiveComponent ic) {
                    ic.setActive(active);
                }
                try {
                    comp.getNode().setCullHint(active ? Node.CullHint.Inherit : Node.CullHint.Always);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void update(float tpf) {
        for (Tab t : tabs) t.button.update(tpf);

        Tab active = getActiveTab();
        if (active != null) {
            for (UIComponent c : active.content) {
                c.update(tpf);
            }
        }
    }

    public void handleMouseMove(Vector2f cursor) {
        int foundIndex = -1;
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            if (t.button.intersects(cursor)) {
                foundIndex = i;
                break;
            }
        }

        if (foundIndex == hoveredButtonIndex) return;

        if (hoveredButtonIndex >= 0 && hoveredButtonIndex < tabs.size()) {
            tabs.get(hoveredButtonIndex).button.setHovered(false);
        }

        if (foundIndex >= 0) {
            tabs.get(foundIndex).button.setHovered(true);
            try {
                buildContext.mainMenuAppState().getGameEngine().getSoundManager().play("ui.toggle");
            } catch (Exception ignored) {
            }
        }

        hoveredButtonIndex = foundIndex;
    }

    @Override
    public void handleMouseClick(Vector2f cursor) {

    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        handleMouseMove(cursor);

        for (Tab t : tabs) {
            if (t.button.intersects(cursor)) {
                t.button.executeAction();
                return;
            }
        }

        Tab active = getActiveTab();
        if (active == null) return;

        for (UIComponent item : active.content) {
            if (!(item instanceof InteractiveComponent)) continue;
            InteractiveComponent ic = (InteractiveComponent) item;

            Node itemNode = item.getNode();
            tmpWorld.set(cursor.x, cursor.y, 0f);
            itemNode.worldToLocal(tmpWorld, tmpLocal);
            tmp2.set(tmpLocal.x, tmpLocal.y);

            if (ic instanceof ViceSlider slider && slider.intersects(tmp2)) {
                activeSlider = slider;
                slider.handleDrag(tmp2);
                return;
            }

            if (ic instanceof ViceCheckbox checkbox && checkbox.intersects(tmp2)) {
                checkbox.toggle();
                return;
            }

            if (ic.intersects(tmp2)) {
                ic.handleMousePress(tmp2);
                return;
            }
        }

        Node contentNode = active.contentNode;
        tmpWorld.set(cursor.x, cursor.y, 0f);
        contentNode.worldToLocal(tmpWorld, tmpLocal);
        tmp2.set(tmpLocal.x, tmpLocal.y);

        for (UIComponent item : active.content) {
            if (item instanceof InteractiveComponent ic && ic.intersects(tmp2)) {
                ic.handleMousePress(tmp2);
                return;
            }
        }
    }

    @Override
    public void handleMouseDoubleClick(Vector2f cursor) {

    }

    @Override
    public void handleMouseRelease(Vector2f cursor) {
        activeSlider = null;

        Tab active = getActiveTab();
        if (active == null) return;
        for (UIComponent item : active.content) {
            if (item instanceof InteractiveComponent ic) ic.handleMouseRelease(cursor);
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
    public void handleMouseScroll(Vector2f cursor, float delta) {

    }

    @Override
    public void handleDoubleClick(Vector2f cursor) {

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
        return cursor.x >= world.x && cursor.x <= world.x + w && cursor.y >= world.y && cursor.y <= world.y + h;
    }

    @Override
    public void setActive(boolean active) {
    }

    @Override
    public void setHovered(boolean hovered) {
    }

    @Override
    public void handleMouseEnter(Vector2f cursor) {

    }

    @Override
    public void handleMouseExit(Vector2f cursor) {

    }

    private Tab getActiveTab() {
        return (activeTabIndex >= 0 && activeTabIndex < tabs.size()) ? tabs.get(activeTabIndex) : null;
    }

    public int getTabCount() { return tabs.size(); }
    public int getActiveIndex() { return activeTabIndex; }

    public void setPreferredButtonSize(float w, float h) { this.preferredButtonWidth = w; this.preferredButtonHeight = h; }
    public void setMinButtonSize(float w, float h) { this.minButtonWidth = w; this.minButtonHeight = h; }
    public void setSpacing(float spacing) { this.spacing = spacing; }
    public void setPadding(float padding) { this.padding = padding; }

    private void safeAttachChild(Node node) {
        if (node == null) return;
        try {
            if (node.getParent() != this) this.attachChild(node);
        } catch (Exception ignored) {
        }
    }

    private void safeDetachChild(Node node) {
        if (node == null) return;
        try {
            if (node.getParent() == this) this.detachChild(node);
        } catch (Exception ignored) {
        }
    }
}