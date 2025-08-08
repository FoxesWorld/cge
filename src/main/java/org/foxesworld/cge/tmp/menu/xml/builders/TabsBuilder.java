package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;
import org.foxesworld.cge.tmp.menu.components.*;
import org.foxesworld.cge.tmp.menu.components.utils.MenuComponent;
import org.foxesworld.cge.tmp.menu.components.utils.Orientation;
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

        ViceTabs tabComponent = new ViceTabs(model.id, context.app().getAssetManager(), ViceButton.Style.getViceStyle(), orientation);

        for (TabXml tabModel : model.tabs) {
            Node contentNode = new Node("TabContent: " + tabModel.title);
            List<Object> createdObjects = new ArrayList<>();

            if (tabModel.components != null) {
                for (ComponentXml componentModel : tabModel.components) {
                    MenuComponent createdComponent = mainBuilder.buildComponent(componentModel, contentNode);
                    createdObjects.add(createdComponent);
                    innerHeight+= createdComponent.getHeight();
                    System.out.println(createdComponent.getId() + " height - " + createdComponent.getHeight());
                    createdComponent.getNode().setLocalTranslation(25, innerHeight + createdComponent.getHeight(), 0);
                }
            }
            tabComponent.addTab(tabModel, contentNode, createdObjects);
        }
        System.out.println(model.contentHeight);
        tabComponent.finalizeLayout(model.contentWidth, model.contentHeight - innerHeight);
        Vector2f pos = MenuUtils.calculatePosition(model.x, model.y, model.alignX, context.app().getCamera());
        float width = MenuUtils.parseSize(model.width, context.app().getCamera().getWidth());
        pos.x -= width / 2f;
        tabComponent.getNode().setLocalTranslation(pos.x, pos.y, 0);
        parent.attachChild(tabComponent.getNode());
        return tabComponent;
    }
}