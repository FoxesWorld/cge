package org.foxesworld.cge.tmp.menu.components;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import org.foxesworld.cge.tmp.menu.xml.TabXml;

import java.util.ArrayList;
import java.util.List;

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
    private float contentWidth = 0f;
    private float contentHeight = 0f;
    private Vector2f buttonBarSize = new Vector2f();

    private final Vector2f tabsWorldPos = new Vector2f();
    private final Vector2f contentLocalCursor = new Vector2f();

    private static final float BUTTON_WIDTH = 180f;
    private static final float BUTTON_HEIGHT = 45f;
    private static final float SPACING = 10f;
    private static final float PADDING = 10f;

    public ViceTabs(String id, AssetManager assetManager, ViceButton.Style buttonStyle, Orientation orientation) {
        super(id);
        this.assetManager = assetManager;
        this.buttonStyle = buttonStyle;
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

    public void finalizeLayout(float contentWidth, float contentHeight) {
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;

        this.buttonBarSize = layoutTabButtons();
        layoutContentArea();

        if (!tabs.isEmpty()) {
            selectTab(0);
        }
    }

    private Vector2f layoutTabButtons() {
        float offset = 0f;
        float maxButtonSize = 0f;

        for (Tab tab : tabs) {
            tab.button().setSize(BUTTON_WIDTH, BUTTON_HEIGHT);
            if (orientation == Orientation.HORIZONTAL) {
                tab.button().setPosition(PADDING + offset, PADDING);
                offset += BUTTON_WIDTH + SPACING;
                maxButtonSize = BUTTON_HEIGHT;
            } else {
                tab.button().setPosition(PADDING, PADDING + offset);
                offset += BUTTON_HEIGHT + SPACING;
                maxButtonSize = BUTTON_WIDTH;
            }
        }

        float barWidth = (orientation == Orientation.HORIZONTAL) ? contentWidth : (maxButtonSize + 2 * PADDING);
        float barHeight = (orientation == Orientation.HORIZONTAL) ? (maxButtonSize + 2 * PADDING) : (offset - SPACING + 2 * PADDING);

        ((Quad) tabBarBackground.getMesh()).updateGeometry(barWidth, barHeight);
        tabBarBackground.setLocalTranslation(0, 0, 0);

        return new Vector2f(barWidth, barHeight);
    }

    private void layoutContentArea() {
        float xOffset = (orientation == Orientation.HORIZONTAL) ? 0 : buttonBarSize.x;
        float yOffset = (orientation == Orientation.HORIZONTAL) ? -contentHeight : 0;

        ((Quad) contentBackground.getMesh()).updateGeometry(contentWidth, contentHeight);
        contentBackground.setLocalTranslation(xOffset, yOffset, -1);

        for (Tab tab : tabs) {
            tab.contentNode().setLocalTranslation(xOffset, yOffset, 0);
            tabsNode.attachChild(tab.contentNode());
        }
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size() || index == activeTabIndex) return;
        activeTabIndex = index;

        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            boolean isActive = (i == index);

            tab.button().setSelected(isActive);
            tab.contentNode().setCullHint(isActive ? Node.CullHint.Inherit : Node.CullHint.Always);

            for (Object item : tab.content()) {
                ((InteractiveComponent) item).setActive(isActive);
            }
        }
    }

    @Override
    public void update(float tpf) {
        for (Tab tab : tabs) {
            tab.button().update(tpf);
        }
    }

    @Override
    public void handleMousePress(Vector2f cursor) {
        tabsWorldPos.set(tabsNode.getWorldTranslation().x, tabsNode.getWorldTranslation().y);

        for (Tab tab : tabs) {
            if (tab.button().intersects(cursor)) {
                tab.button().executeAction();
                return;
            }
        }

        Tab activeTab = getActiveTab();
        if (activeTab == null) return;

        transformToContentSpace(cursor, activeTab);

        for (Object item : activeTab.content()) {
            if (item instanceof ViceSlider slider && slider.intersects(contentLocalCursor)) {
                activeSlider = slider;
                slider.handleDrag(contentLocalCursor);
                return;
            } else if (item instanceof ViceCheckbox checkbox && checkbox.intersects(contentLocalCursor)) {
                checkbox.toggle();
                return;
            }
        }
    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {
        if (activeSlider == null) return;

        Tab activeTab = getActiveTab();
        if (activeTab == null) return;

        transformToContentSpace(cursor, activeTab);
        activeSlider.handleDrag(contentLocalCursor);
    }

    @Override
    public void handleMouseRelease() {
        activeSlider = null;
    }

    @Override
    public float getHeight() {
        return contentHeight;
    }

    @Override
    public float getWidth() {
        return contentWidth;
    }

    @Override
    public void setSize(float width, float height) {
        finalizeLayout(width, height);
    }

    @Override
    public boolean intersects(Vector2f cursor) {
        Vector2f worldPos = new Vector2f(tabsNode.getWorldTranslation().x, tabsNode.getWorldTranslation().y);
        float totalWidth = Math.max(buttonBarSize.x, contentBackground.getLocalTranslation().x + contentWidth);
        float totalHeight = buttonBarSize.y + contentHeight;

        return cursor.x >= worldPos.x && cursor.x <= worldPos.x + totalWidth &&
                cursor.y >= worldPos.y - totalHeight && cursor.y <= worldPos.y + buttonBarSize.y;
    }

    @Override
    public void setActive(boolean active) {}

    @Override
    public void setHovered(boolean hovered) {}

    @Override
    public Node getNode() {
        return tabsNode;
    }

    private Tab getActiveTab() {
        return (activeTabIndex >= 0 && activeTabIndex < tabs.size()) ? tabs.get(activeTabIndex) : null;
    }

    private void transformToContentSpace(Vector2f cursor, Tab activeTab) {
        contentLocalCursor.set(cursor)
                .subtractLocal(tabsWorldPos)
                .subtractLocal(activeTab.contentNode().getLocalTranslation().x, activeTab.contentNode().getLocalTranslation().y);
    }
}