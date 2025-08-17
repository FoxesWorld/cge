package org.foxesworld.cge.tmp.menu.builders;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;
import org.foxesworld.cge.tmp.menu.MenuUtils;
import org.foxesworld.cge.tmp.menu.components.ViceButton;
import org.foxesworld.cge.tmp.menu.components.ViceCheckbox;
import org.foxesworld.cge.tmp.menu.xml.CheckboxXml;
import org.foxesworld.cge.tmp.menu.xml.ComponentBuilder;

/**
 * Builds a {@link ViceCheckbox} component from a {@link CheckboxXml} model.
 */
public class CheckboxBuilder implements ComponentBuilder<CheckboxXml> {

    @Override
    public ViceCheckbox build(CheckboxXml model, Node parent, BuildContext context) {
        ViceCheckbox checkbox = new ViceCheckbox(
                context.mainMenuAppState().getGameEngine().getAssetManager(),
                ViceButton.Style.getViceStyle().fontPath,
                model
        );

        float size = MenuUtils.parseSize(model.size, context.mainMenuAppState().getGameEngine().getCamera().getHeight());
        checkbox.setSize(size);

            String[] binds = model.bind.split("\\.");
            checkbox.setChecked((Boolean) MainMenuAppState.getSettingsValue(binds[0], binds[1]));

        Vector2f pos = MenuUtils.calculatePosition(
                model.x,
                model.y,
                model.alignX,
                context.mainMenuAppState().getGameEngine().getCamera()
        );
        checkbox.setPosition(pos.x, pos.y);


        parent.attachChild(checkbox.getNode());
        return checkbox;
    }
}