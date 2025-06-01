package org.foxesworld.cge.ui.novaUi.tmp;

import com.jme3.scene.Node;
import org.foxesworld.cge.CalistaGameEngine;

public class JMEGUIContext implements IGUIContext {
    private final CalistaGameEngine engine;

    public JMEGUIContext(CalistaGameEngine engine) {
        this.engine = engine;
    }

    @Override
    public Node getGuiNode() {
        return engine.getGuiNode();
    }

    @Override
    public int getWidth() {
        return engine.getCamera().getWidth();
    }

    @Override
    public int getHeight() {
        return engine.getCamera().getHeight();
    }
}
