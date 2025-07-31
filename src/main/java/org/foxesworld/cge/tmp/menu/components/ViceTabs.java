package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import java.util.ArrayList;
import java.util.List;

/**
 * A self-contained, interactive tab container for settings menus. It manages its
 * own layout, state, and input delegation to child components.
 */
public final class ViceTabs implements InteractiveComponent, MenuComponent {

    private record Tab(
            ViceButton button,
            Node contentNode,
            List<ViceSlider> sliders,
            List<ViceCheckbox> checkboxes
    ) {}

    private final Node tabsNode = new Node("ViceTabs");
    private final List<Tab> tabs = new ArrayList<>();
    private final AssetManager assetManager;
    private final ViceButton.Style buttonStyle;
    private final Orientation orientation;

    private final Geometry tabBarBackground;
    private final Geometry contentBackground;

    private int activeTabIndex = -1;
    private ViceSlider activeSlider = null;
    private Vector2f buttonBarSize;
    private float contentWidth, contentHeight;

    private final Vector2f tabsWorldPos = new Vector2f();
    private final Vector2f contentLocalCursor = new Vector2f();

    public ViceTabs(AssetManager assetManager, ViceButton.Style buttonStyle, Orientation orientation) {
        this.assetManager = assetManager;
        this.buttonStyle = buttonStyle;
        this.orientation = orientation;

        Material barMat = createBackgroundMaterial(new ColorRGBA(0.05f, 0.05f, 0.05f, 0.75f));
        this.tabBarBackground = new Geometry("TabsBarBackground", new Quad(1, 1));
        tabBarBackground.setMaterial(barMat);

        Material contentMat = createBackgroundMaterial(new ColorRGBA(0.1f, 0.1f, 0.1f, 0.65f));
        this.contentBackground = new Geometry("TabsContentBackground", new Quad(1, 1));
        contentBackground.setMaterial(contentMat);

        tabsNode.attachChild(tabBarBackground);
        tabsNode.attachChild(contentBackground);
    }

    private Material createBackgroundMaterial(ColorRGBA color) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        return mat;
    }

    public void addTab(String title, Node content, List<ViceSlider> sliders, List<ViceCheckbox> checkboxes) {
        int index = tabs.size();
        ViceButton tabButton = new ViceButton(assetManager, title, buttonStyle, () -> selectTab(index));
        tabs.add(new Tab(tabButton, content, sliders, checkboxes));
        tabsNode.attachChild(tabButton.getNode());
        tabsNode.attachChild(content);
    }

    public void finalizeLayout(float contentWidth, float contentHeight) {
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        this.buttonBarSize = layoutTabButtons();
        layoutContentArea(contentWidth, contentHeight, buttonBarSize);

        if (!tabs.isEmpty()) {
            selectTab(0, true);
        }
    }

    private Vector2f layoutTabButtons() {
        float currentOffset = 0;
        float maxDimension = 0;
        float spacing = 10f;
        float btnWidth = 180f, btnHeight = 45f;
        float padding = 10f;
        for (Tab tab : tabs) {
            tab.button().setSize(btnWidth, btnHeight);
            tab.button().setLabelSize(28f);

            if (orientation == Orientation.HORIZONTAL) {
                tab.button().setPosition(padding + currentOffset, padding);
                currentOffset += btnWidth + spacing;
                maxDimension = Math.max(maxDimension, btnHeight);
            } else { // VERTICAL
                tab.button().setPosition(padding, padding + currentOffset);
                currentOffset += btnHeight + spacing;
                maxDimension = Math.max(maxDimension, btnWidth);
            }
        }

        float barWidth = (orientation == Orientation.HORIZONTAL) ? (currentOffset - spacing + 2 * padding) : (maxDimension + 2 * padding);
        float barHeight = (orientation == Orientation.HORIZONTAL) ? (maxDimension + 2 * padding) : (currentOffset - spacing + 2 * padding);

        ((Quad)tabBarBackground.getMesh()).updateGeometry(barWidth, barHeight);
        tabBarBackground.setLocalTranslation(0, 0, 0); // Панель кнопок теперь в (0,0)

        return new Vector2f(barWidth, barHeight);
    }

    private void layoutContentArea(float width, float height, Vector2f buttonBarSize) {
        float xOffset, yOffset;
        if (orientation == Orientation.HORIZONTAL) {
            xOffset = 0;
            yOffset = -height; // Контент строго ПОД панелью кнопок
        } else { // VERTICAL
            xOffset = buttonBarSize.x; // Контент строго СПРАВА от панели кнопок
            yOffset = 0;
        }

        ((Quad)contentBackground.getMesh()).updateGeometry(width, height);
        contentBackground.setLocalTranslation(xOffset, yOffset, -1); // Дальше от камеры

        for(Tab tab : tabs) {
            tab.contentNode().setLocalTranslation(xOffset, yOffset, 0);
        }
    }

    private void selectTab(int index, boolean instant) {
        if (index < 0 || index >= tabs.size() || index == activeTabIndex) return;

        this.activeTabIndex = index;

        // Проходим по ВСЕМ вкладкам и ВСЕМ их компонентам
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            boolean isNowActive = (i == index);

            // Управляем кнопкой-вкладкой
            tab.button().setSelected(isNowActive);
            tab.button().setUnderlineVisible(!isNowActive);

            // Управляем видимостью контента
            tab.contentNode().setCullHint(isNowActive ? Node.CullHint.Inherit : Node.CullHint.Always);

            // --- НОВАЯ КЛЮЧЕВАЯ ЛОГИКА ---
            // Управляем интерактивностью дочерних компонентов
            for (ViceSlider slider : tab.sliders()) {
                slider.setActive(isNowActive);
            }
            for (ViceCheckbox checkbox : tab.checkboxes()) {
                checkbox.setActive(isNowActive);
            }
        }
    }

    private void selectTab(int index) {
        selectTab(index, false);
    }

    @Override
    public void update(float tpf) {
        // Делегируем обновление анимации всем дочерним компонентам
        for (Tab tab : tabs) {
            tab.button().update(tpf);
            if (tab.contentNode().getCullHint() == Node.CullHint.Inherit) {
                tab.sliders().forEach(s -> s.update(tpf));
                tab.checkboxes().forEach(c -> c.update(tpf));
            }
        }
    }

    // --- Implementation of InteractiveComponent ---

    @Override
    public void setActive(boolean active) {

    }

    @Override
    public void setHovered(boolean hovered) {
        // Контейнер сам по себе не имеет состояния наведения, он управляет дочерними.
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        tabsWorldPos.set(tabsNode.getWorldTranslation().x, tabsNode.getWorldTranslation().y);

        // 1. Проверяем клик по кнопкам-вкладкам
        for (Tab tab : tabs) {
            if (tab.button().intersects(cursor)) {
                tab.button().executeAction();
                return;
            }
        }

        // 2. Делегируем клик в контент активной вкладки
        Tab activeTab = getActiveTab();
        if (activeTab == null) return;

        contentLocalCursor.set(cursor)
                .subtractLocal(tabsWorldPos)
                .subtractLocal(activeTab.contentNode().getLocalTranslation().x, activeTab.contentNode.getLocalTranslation().y);

        for (ViceSlider slider : activeTab.sliders()) {
            if (slider.intersects(contentLocalCursor)) {
                activeSlider = slider;
                activeSlider.handleDrag(contentLocalCursor);
                return;
            }
        }
        for (ViceCheckbox checkbox : activeTab.checkboxes()) {
            if (checkbox.intersects(contentLocalCursor)) {
                checkbox.toggle();
                return;
            }
        }
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {
        if (activeSlider != null) {
            Tab activeTab = getActiveTab();
            if (activeTab == null) return;
            contentLocalCursor.set(cursor)
                    .subtractLocal(tabsWorldPos)
                    .subtractLocal(activeTab.contentNode().getLocalTranslation().x, activeTab.contentNode().getLocalTranslation().y);
            activeSlider.handleDrag(contentLocalCursor);
        }
    }

    @Override
    public void handleMouseRelease() {
        activeSlider = null;
    }

    @Override
    public boolean intersects(Vector2f cursor) {
        // Проверяем, находится ли курсор в пределах всего компонента
        Vector2f worldPos = new Vector2f(tabsNode.getWorldTranslation().x, tabsNode.getWorldTranslation().y);
        float totalWidth = Math.max(buttonBarSize.x, contentBackground.getLocalTranslation().x + contentWidth);
        float totalHeight = buttonBarSize.y + contentHeight; // Примерная общая высота

        return cursor.x >= worldPos.x && cursor.x <= worldPos.x + totalWidth &&
                cursor.y >= worldPos.y - totalHeight && cursor.y <= worldPos.y + buttonBarSize.y;
    }

    private Tab getActiveTab() {
        return (activeTabIndex >= 0 && activeTabIndex < tabs.size()) ? tabs.get(activeTabIndex) : null;
    }

    @Override
    public Node getNode() {
        return tabsNode;
    }
}