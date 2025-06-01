package org.foxesworld.cge.ui.novaUi.tmp;

import com.jme3.scene.Node;

public class FakeGUIContext implements IGUIContext {
    private final Node fakeNode = new Node("FakeGui");
    private int w, h;

    public FakeGUIContext(int width, int height) {
        this.w = width;
        this.h = height;
    }

    @Override
    public Node getGuiNode() {
        return fakeNode;
    }

    @Override
    public int getWidth() {
        return w;
    }

    @Override
    public int getHeight() {
        return h;
    }
}
