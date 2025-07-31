package org.foxesworld.cge.tmp.menu.xml.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.xml.ButtonXml;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;
import org.foxesworld.cge.tmp.menu.XmlMenuBuilder;

public class ButtonBuilder implements ComponentBuilder<ButtonXml> {
    @Override
    public Object build(ButtonXml model, Node parent, BuildContext context) {
        Runnable action = XmlMenuBuilder.createActionFromClassName(model.action, context.app());
        ViceButton button = new ViceButton(context.app().getAssetManager(), model.text, context.buttonStyle(), action);

        float width = XmlMenuBuilder.parseSize(model.width, context.app().getCamera().getWidth());
        float height = XmlMenuBuilder.parseSize(model.height, context.app().getCamera().getHeight());
        button.setSize(width, height);
        button.setLabelSize(model.fontSize);

        Vector2f pos = XmlMenuBuilder.calculatePosition(model.x, model.y, model.align, context.app().getCamera());
        pos.x -= width / 2f;
        button.setPosition(pos.x, pos.y);

        parent.attachChild(button.getNode());
        return button;
    }
}