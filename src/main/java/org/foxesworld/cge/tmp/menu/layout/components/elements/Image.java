package org.foxesworld.cge.tmp.menu.layout.components.elements;

import com.jme3.app.Application;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

public class Image {
    private Geometry logoGeom;
    private BitmapText fallbackLogo;

    public Image(Application app, float menuX, float menuY, float menuWidth, float menuHeight) {
        float logoH = 180;
        try {
            Texture logo = app.getAssetManager().loadTexture("assets/theme/logo.png");
            float logoW = logoH * logo.getImage().getWidth() / logo.getImage().getHeight();
            Quad logoQuad = new Quad(logoW, logoH);
            logoGeom = new Geometry("MenuLogo", logoQuad);
            Material logoMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            logoMat.setTexture("ColorMap", logo);
            logoMat.setColor("Color", ColorRGBA.White);
            logoMat.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
            logoGeom.setMaterial(logoMat);
            logoGeom.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
            logoGeom.setLocalTranslation(menuX + menuWidth / 2f - logoW / 2f, menuY + menuHeight + logoH / 2f + 24, 1);
        } catch (Exception e) {
            fallbackLogo = new BitmapText(app.getAssetManager().loadFont("Interface/Fonts/Default.fnt"), false);
            fallbackLogo.setText("CALISTA GAME");
            fallbackLogo.setColor(new ColorRGBA(0.14f, 0.16f, 0.21f, 0.94f));
            fallbackLogo.setSize(48);
            fallbackLogo.setLocalTranslation(menuX + 52, menuY + menuHeight + logoH / 2f + 32, 1);
        }
    }

    public void attachTo(Node node) {
        if (logoGeom != null) node.attachChild(logoGeom);
        else if (fallbackLogo != null) node.attachChild(fallbackLogo);
    }
    public void detach() {
        if (logoGeom != null) logoGeom.removeFromParent();
        if (fallbackLogo != null) fallbackLogo.removeFromParent();
    }
}