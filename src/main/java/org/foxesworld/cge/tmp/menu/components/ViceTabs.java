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

public final class ViceTabs implements InteractiveComponent, MenuComponent {

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
    private Vector2f buttonBarSize;
    private float contentWidth, contentHeight;

    private final Vector2f tabsWorldPos = new Vector2f();
    private final Vector2f contentLocalCursor = new Vector2f();

    public ViceTabs(AssetManager assetManager, ViceButton.Style buttonStyle, Orientation orientation) {
        this.assetManager = assetManager;
        this.buttonStyle = buttonStyle;
        this.orientation = orientation;

        this.tabBarBackground = createTabBackground(new ColorRGBA(0.05f, 0.05f, 0.05f, 0.75f), "TabsBarBackground");
        this.contentBackground = createTabBackground(new ColorRGBA(0.1f, 0.1f, 0.1f, 0.65f), "TabsContentBackground");

        tabsNode.attachChild(tabBarBackground);
        tabsNode.attachChild(contentBackground);
    }

    private Geometry createTabBackground(ColorRGBA color, String name) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        Geometry bg = new Geometry(name, new Quad(1, 1));
        bg.setMaterial(mat);
        return bg;
    }

    public void addTab(TabXml tabXml, Node content, List<Object> createdObjects) {
        int index = tabs.size();
        ViceButton tabButton = new ViceButton(assetManager, tabXml.title, buttonStyle, () -> selectTab(index), tabXml.iconPath, tabXml.iconSize);
        tabs.add(new Tab(tabButton, content, createdObjects));
        tabsNode.attachChild(tabButton.getNode());
        tabsNode.attachChild(content);
    }

    public void finalizeLayout(float contentWidth, float contentHeight) {
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;

        this.buttonBarSize = layoutTabButtons();
        layoutContentArea(contentWidth, contentHeight, buttonBarSize);

        if (!tabs.isEmpty()) {
            selectTab(0);
        }
    }

    private Vector2f layoutTabButtons() {
        float offset = 0f;
        float maxDim = 0f;
        float spacing = 10f;
        float btnWidth = 180f, btnHeight = 45f;
        float padding = 10f;

        for (Tab tab : tabs) {
            tab.button().setSize(btnWidth, btnHeight);
            //tab.button().setLabelSize(28f);

            if (orientation == Orientation.HORIZONTAL) {
                tab.button().setPosition(padding + offset, padding);
                offset += btnWidth + spacing;
                maxDim = Math.max(maxDim, btnHeight);
            } else {
                tab.button().setPosition(padding, padding + offset);
                offset += btnHeight + spacing;
                maxDim = Math.max(maxDim, btnWidth);
            }
        }

        float barWidth = (orientation == Orientation.HORIZONTAL) ? this.contentWidth : (maxDim + 2 * padding);
        float barHeight = (orientation == Orientation.HORIZONTAL) ? (maxDim + 2 * padding) : (offset - spacing + 2 * padding);

        ((Quad) tabBarBackground.getMesh()).updateGeometry(barWidth, barHeight);
        tabBarBackground.setLocalTranslation(0, 0, 0);

        return new Vector2f(barWidth, barHeight);
    }

    private void layoutContentArea(float width, float height, Vector2f buttonBarSize) {
        float xOffset = orientation == Orientation.HORIZONTAL ? 0 : buttonBarSize.x;
        float yOffset = orientation == Orientation.HORIZONTAL ? -height : 0;

        ((Quad) contentBackground.getMesh()).updateGeometry(width, height);
        contentBackground.setLocalTranslation(xOffset, yOffset, -1);

        for (Tab tab : tabs) {
            tab.contentNode().setLocalTranslation(xOffset, yOffset, 0);
        }
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size() || index == activeTabIndex) return;

        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            boolean isActive = (i == index);

            tab.button().setSelected(isActive);
            //tab.button().setUnderlineVisible(!isActive);

            tab.contentNode().setCullHint(isActive ? Node.CullHint.Inherit : Node.CullHint.Always);
            if (isActive) {
                for (Object item : tab.content()) {
                    ((InteractiveComponent) item).setActive(true);
                }
                if (tab.contentNode().getParent() == null) {
                    tabsNode.attachChild(tab.contentNode());
                }
            } else {
                for (Object item : tab.content()) {
                    ((InteractiveComponent) item).setActive(false);
                }
                // optionally detach node (depends on scene graph policy)
                tabsNode.detachChild(tab.contentNode());
            }
        }
    }


    @Override
    public void update(float tpf) {
        for (Tab tab : tabs) {
            tab.button().update(tpf);

            //if (isTabActive(tab)) {
                //for (Object item : tab.content()) {
                    //tab.contentNode.attachChild(((MenuComponent) item).getNode());
                //}
            //}
        }
    }

    private boolean isTabActive(Tab tab) {
        return tab.contentNode().getCullHint() == Node.CullHint.Inherit;
    }

    private Tab getActiveTab() {
        return (activeTabIndex >= 0 && activeTabIndex < tabs.size()) ? tabs.get(activeTabIndex) : null;
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
    public void setSize(float width, float height) {

    }

    @Override
    public float getWidth() {
        return contentWidth;
    }

    private void transformToContentSpace(Vector2f cursor, Tab activeTab) {
        contentLocalCursor.set(cursor)
                .subtractLocal(tabsWorldPos)
                .subtractLocal(activeTab.contentNode().getLocalTranslation().x, activeTab.contentNode().getLocalTranslation().y);
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
}
