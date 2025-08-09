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
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.MenuComponent;
import org.foxesworld.cge.tmp.menu.components.utils.Orientation;
import org.foxesworld.cge.tmp.menu.xml.TabXml;

import java.util.ArrayList;
import java.util.List;

/**
 * ViceTabs — вкладки с улучшенной адаптивностью вычисления зоны контента.
 * Добавлена поддержка hover-эффекта для кнопок.
 *
 * CHANGED: Компоненты внутри контента теперь получают координаты курсора в своей локальной системе координат.
 */
public final class ViceTabs extends UIComponent implements InteractiveComponent, MenuComponent {

    private record Tab(ViceButton button, Node contentNode, List<Object> content) {}

    private final Node tabsNode = new Node("ViceTabs");
    private final List<Tab> tabs = new ArrayList<>();
    private final AssetManager assetManager;
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
    private float preferredButtonWidth = 180f;
    private float preferredButtonHeight = 45f;
    private float minButtonWidth = 48f;
    private float minButtonHeight = 20f;
    private float spacing = 10f;
    private float padding = 10f;

    // локальная координата курсора внутри области контента (x: вправо от левого края, y: вверх от нижнего края)
    // (оставляем как вспомогательный, но основные проверки теперь происходят в локале каждого компонента)
    private final Vector2f contentLocalCursor = new Vector2f();

    // индекс кнопки, над которой сейчас hover (-1 если никакой)
    private int hoveredButtonIndex = -1;

    private static final float QUAD_TEX_BUFFER = 1f; // небольшой буфер, если нужен

    // CHANGED: временные буферы для преобразований мировых -> локальных (чтобы не аллоцировать каждый кадр)
    private final Vector3f tmpWorld = new Vector3f();
    private final Vector3f tmpLocal = new Vector3f();
    private final Vector2f tmp2 = new Vector2f();

    public ViceTabs(String id, AssetManager assetManager, ViceButton.Style buttonStyle, Orientation orientation) {
        super(id);
        this.assetManager = assetManager;
        this.buttonStyle = buttonStyle != null ? buttonStyle : ViceButton.Style.getViceStyle();
        this.orientation = orientation;

        this.tabBarBackground = createBackground(new ColorRGBA(0.05f, 0.05f, 0.05f, 0.75f), "TabBarBG");
        this.contentBackground = createBackground(new ColorRGBA(0.1f, 0.1f, 0.1f, 0.65f), "ContentBG");

        tabsNode.attachChild(tabBarBackground);
        tabsNode.attachChild(contentBackground);
    }

    private Geometry createBackground(ColorRGBA color, String name) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        Geometry bg = new Geometry(name, new Quad(1, 1));
        bg.setMaterial(mat);
        return bg;
    }

    public void addTab(TabXml tabXml, Node content, List<Object> createdObjects) {
        int index = tabs.size();
        ViceButton button = new ViceButton("tab-" + index, assetManager, tabXml.title, buttonStyle, () -> selectTab(index), tabXml.iconPath, tabXml.iconSize);
        tabs.add(new Tab(button, content, createdObjects));
        tabsNode.attachChild(button.getNode());
    }

    /**
     * Подготовить финальную раскладку. Принимает доступную ширину и высоту для всей компоненты Tabs.
     * Эти значения — в локальных GUI-пикселях (обычно передаются уже с учётом dpiScale внешнего контейнера).
     */
    public void finalizeLayout(float totalWidth, float totalHeight) {
        // масштабируем внутренние константы с учётом dpiScale (UIComponent содержит поле dpiScale)
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

            for (Tab t : tabs) {
                //t.contentNode().setLocalTranslation(0, 0, 0);
                if (t.contentNode().getParent() == null) tabsNode.attachChild(t.contentNode());
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
                t.contentNode().setLocalTranslation(barW, 0, 0);
                if (t.contentNode().getParent() == null) {
                    tabsNode.attachChild(t.contentNode());
                }
            }
        }

        if (!tabs.isEmpty() && (activeTabIndex < 0 || activeTabIndex >= tabs.size())) {
            selectTab(0);
        }
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size() || index == activeTabIndex) return;
        activeTabIndex = index;

        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            boolean active = (i == index);
            t.button().setSelected(active);
            t.contentNode().setCullHint(active ? Node.CullHint.Inherit : Node.CullHint.Always);

            for (Object o : t.content()) {
                if (o instanceof InteractiveComponent ic) {
                    ic.setActive(active);
                }
            }
        }
    }

    @Override
    public void update(float tpf) {
        for (Tab t : tabs) t.button().update(tpf);
    }

    /**
     * Обрабатывает передвижение курсора — обновляет hover-состояние кнопок.
     * Вызывать из внешней системы при движении мыши или, при отсутствии такой системы,
     * из места, где у тебя доступен текущий курсор.
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
        }

        hoveredButtonIndex = foundIndex;
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        // обновляем hover чтобы избежать рассинхрона при нажатии
        handleMouseMove(cursor);

        // button clicks: test each button directly (buttons know their node/world pos)
        for (Tab t : tabs) {
            if (t.button().intersects(cursor)) {
                t.button().executeAction();
                return;
            }
        }

        // otherwise forward to active content
        Tab active = getActiveTab();
        if (active == null) return;

        // CHANGED: Для каждого элемента контента преобразуем курсор в локальные координаты этого элемента (если возможно)
        for (Object item : active.content()) {
            // case: item is both a MenuComponent (has node) and InteractiveComponent
            if (item instanceof MenuComponent mc && item instanceof InteractiveComponent ic) {
                Node itemNode = mc.getNode();
                // world -> local in item's node
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
                } else if (ic.intersects(tmp2)) {
                    ic.handleMousePress(tmp2);
                    return;
                }
            } else if (item instanceof InteractiveComponent ic) {
                // fallback: use contentNode local coordinates
                Node contentNode = active.contentNode();
                tmpWorld.set(cursor.x, cursor.y, 0f);
                contentNode.worldToLocal(tmpWorld, tmpLocal);
                tmp2.set(tmpLocal.x, tmpLocal.y);

                if (ic instanceof ViceSlider slider && slider.intersects(tmp2)) {
                    activeSlider = slider;
                    slider.handleDrag(tmp2);
                    return;
                } else if (ic instanceof ViceCheckbox checkbox && checkbox.intersects(tmp2)) {
                    checkbox.toggle();
                    return;
                } else if (ic.intersects(tmp2)) {
                    ic.handleMousePress(tmp2);
                    return;
                }
            }
        }
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {
        // support hover update during drag as well
        handleMouseMove(cursor);

        if (activeSlider == null) return;
        Tab active = getActiveTab();
        if (active == null) return;

        // CHANGED: convert to slider's local coords before dragging
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
        Vector2f world = new Vector2f(tabsNode.getWorldTranslation().x, tabsNode.getWorldTranslation().y);
        float w = getWidth();
        float h = getHeight();
        return cursor.x >= world.x && cursor.x <= world.x + w &&
                cursor.y >= world.y && cursor.y <= world.y + h;
    }

    @Override
    public void setActive(boolean active) { /* nop */ }

    @Override
    public void setHovered(boolean hovered) { /* nop */ }

    @Override
    public Node getNode() {
        return tabsNode;
    }

    private Tab getActiveTab() {
        return (activeTabIndex >= 0 && activeTabIndex < tabs.size()) ? tabs.get(activeTabIndex) : null;
    }

    /**
     * Преобразовать курсор из мировых координат в локальные координаты области контента.
     * (Остался для совместимости / внешних вызовов — но внутренняя логика теперь предпочитает
     * преобразовывать в локал каждого конкретного компонента)
     */
    private void transformToContentSpace(Vector2f cursor, Tab activeTab) {
        Vector3f worldPos = new Vector3f(cursor.x, cursor.y, 0f);
        Vector3f local = contentBackground.worldToLocal(worldPos, null);
        contentLocalCursor.set(local.x, local.y);
    }

    // сеттеры для гибкой настройки
    public void setPreferredButtonSize(float w, float h) { this.preferredButtonWidth = w; this.preferredButtonHeight = h; }
    public void setMinButtonSize(float w, float h) { this.minButtonWidth = w; this.minButtonHeight = h; }
    public void setSpacing(float spacing) { this.spacing = spacing; }
    public void setPadding(float padding) { this.padding = padding; }
}