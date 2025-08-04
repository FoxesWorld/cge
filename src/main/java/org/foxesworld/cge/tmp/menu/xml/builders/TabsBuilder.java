package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;
import org.foxesworld.cge.tmp.menu.components.*;
import org.foxesworld.cge.tmp.menu.xml.*;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link ViceTabs} component from a {@link TabsXml} model.
 * This builder recursively calls the main XmlMenuBuilder to construct the content of each tab.
 */
public class TabsBuilder implements ComponentBuilder<TabsXml> {

    private final XmlMenuBuilder mainBuilder;

    /**
     * Constructs the TabsBuilder.
     * @param mainBuilder A reference to the main XmlMenuBuilder instance for recursive calls.
     */
    public TabsBuilder(XmlMenuBuilder mainBuilder) {
        this.mainBuilder = mainBuilder;
    }

    @Override
    public ViceTabs build(TabsXml model, Node parent, BuildContext context) {
        float innerHeight = 0;
        Orientation orientation = "VERTICAL".equalsIgnoreCase(model.orientation)
                ? Orientation.VERTICAL
                : Orientation.HORIZONTAL;

        ViceTabs tabComponent = new ViceTabs(context.app().getAssetManager(), context.buttonStyle(), orientation);

        for (TabXml tabModel : model.tabs) {
            Node contentNode = new Node("TabContent: " + tabModel.title);
            List<Object> createdObjects = new ArrayList<>();

            if (tabModel.components != null) {
                for (ComponentXml componentModel : tabModel.components) {
                    MenuComponent createdComponent = mainBuilder.buildComponent(componentModel, contentNode);
                    createdObjects.add(createdComponent);
                    innerHeight+= (float) (createdComponent.getHeight() * 2.4);
                }
            }
            tabComponent.addTab(tabModel, contentNode, createdObjects);
        }
        tabComponent.finalizeLayout(model.contentWidth, model.contentHeight - innerHeight);
        Vector2f pos = MenuUtils.calculatePosition(model.x, model.y, model.align, context.app().getCamera());
        float width = MenuUtils.parseSize(model.width, context.app().getCamera().getWidth());
        pos.x -= width / 2f;
        tabComponent.getNode().setLocalTranslation(pos.x, pos.y, 0);
        parent.attachChild(tabComponent.getNode());
        return tabComponent;
    }
}