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
 * A self-contained tab container for settings menus, supporting both horizontal and vertical layouts.
 * This component now correctly handles coordinate systems and layout to prevent visual overlaps.
 */
public final class ViceTabs implements MenuComponent {

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

    // Optimization: Reusable vectors to avoid creating new objects in the update loop
    private final Vector2f tabsWorldPos = new Vector2f();
    private final Vector2f buttonWorldPos = new Vector2f();
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

    /**
     * Finalizes the layout after all tabs have been added and selects the first tab.
     */
    public void finalizeLayout(float contentWidth, float contentHeight) {
        Vector2f buttonBarSize = layoutTabButtons();
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
        if (index < 0 || index >= tabs.size()) return;
        this.activeTabIndex = index;
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).button().setSelected(i == index);
            tabs.get(i).contentNode().setCullHint(i == index ? Node.CullHint.Inherit : Node.CullHint.Always);
        }
    }

    private void selectTab(int index) {
        selectTab(index, false);
    }

    public void update(float tpf, Vector2f cursor) {
        ViceButton hoveredButton = null;
        tabsWorldPos.set(tabsNode.getLocalTranslation().x, tabsNode.getLocalTranslation().y);

        for (Tab tab : tabs) {
            buttonWorldPos.set(tabsWorldPos).addLocal(tab.button().getPosition());
            if (isCursorInside(cursor, buttonWorldPos, tab.button().getWidth(), tab.button().getHeight())) {
                hoveredButton = tab.button();
                break;
            }
        }

        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            tab.button().setSelected(i == activeTabIndex || tab.button() == hoveredButton);
            tab.button().update(tpf);
        }
    }

    public void handleMousePress(Vector2f cursor) {
        tabsWorldPos.set(tabsNode.getLocalTranslation().x, tabsNode.getLocalTranslation().y);

        for (Tab tab : tabs) {
            buttonWorldPos.set(tabsWorldPos).addLocal(tab.button().getPosition());
            if (isCursorInside(cursor, buttonWorldPos, tab.button().getWidth(), tab.button().getHeight())) {
                tab.button().executeAction();
                return;
            }
        }

        Tab activeTab = getActiveTab();
        if (activeTab == null) return;

        contentLocalCursor.set(cursor)
                .subtractLocal(tabsWorldPos)
                .subtractLocal(activeTab.contentNode().getLocalTranslation().x, activeTab.contentNode().getLocalTranslation().y);

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

    private boolean isCursorInside(Vector2f cursor, Vector2f pos, float width, float height) {
        return cursor.x >= pos.x && cursor.x <= pos.x + width &&
                cursor.y >= pos.y && cursor.y <= pos.y + height;
    }

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

    public void handleMouseRelease() {
        activeSlider = null;
    }

    private Tab getActiveTab() {
        return (activeTabIndex >= 0 && activeTabIndex < tabs.size()) ? tabs.get(activeTabIndex) : null;
    }

    public Node getNode() { return tabsNode; }

    @Override
    public void update(float tpf) {

    }

    @Override
    public boolean intersects(Vector2f cursor) {
        return false;
    }
}