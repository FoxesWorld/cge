package org.foxesworld.cge.tmp.menu.components;

import com.atr.jme.font.shape.TrueTypeContainer;
import com.atr.jme.font.util.Style;
import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.core.io.TTFrenderer;
import org.foxesworld.cge.core.utils.ColorUtils;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.components.utils.MenuComponent;
import org.foxesworld.cge.tmp.menu.xml.TextXml;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ViceText extends UIComponent implements InteractiveComponent, MenuComponent {

    private final Node textNode;
    private final AssetManager assetManager;
    private final TextXml textXml;

    private final String fontSizeRaw;
    private final Application app;

    private TTFrenderer ttfRenderer;
    private TrueTypeContainer ttc;

    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;

    public ViceText(Application app, AssetManager assetManager, TextXml textXml) {
        super(textXml.id);
        this.app = app;
        this.assetManager = assetManager;
        this.textXml = textXml;
        this.fontSizeRaw = textXml.fontSize;
        ttfRenderer = new TTFrenderer(assetManager);
        this.textNode = new Node("ViceText: " + textXml.text);
        recreateFont(); // инициализация шрифта и текста
    }

    private void recreateFont() {
        float fontSize = parseFontSize(fontSizeRaw);
        if (ttfRenderer != null && ttc != null) {
            textNode.detachChild(ttc);
        }

        ttfRenderer.generateFont("assets/Interface/fonts/Docker One.ttf", Style.Plain, Math.round(fontSize));
        ttfRenderer.generateText(ColorUtils.fromHexString(textXml.color), textXml.text);
        ttc = ttfRenderer.getTextGeometry();
        textNode.attachChild(ttc);
    }

    private float parseFontSize(String raw) {
        String value = Optional.ofNullable(raw)
                .map(String::trim)
                .map(String::toLowerCase)
                .orElse("");

        int width = app.getContext().getSettings().getWidth();
        int height = app.getContext().getSettings().getHeight();

        try {
            if (value.endsWith("vh")) {
                return height * Float.parseFloat(value.substring(0, value.length() - 2)) / 100f;
            }
            if (value.endsWith("vw")) {
                return width * Float.parseFloat(value.substring(0, value.length() - 2)) / 100f;
            }
            if (value.endsWith("%")) {
                return height * Float.parseFloat(value.substring(0, value.length() - 1)) / 100f;
            }
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            LoggerFactory.getLogger(getClass()).error("Invalid fontSize '{}', defaulting to 32px", raw, e);
            return 32f;
        }
    }

    @Override
    public void update(float tpf) {
        int currentWidth = app.getContext().getSettings().getWidth();
        int currentHeight = app.getContext().getSettings().getHeight();

        if (currentWidth != lastWindowWidth || currentHeight != lastWindowHeight) {
            lastWindowWidth = currentWidth;
            lastWindowHeight = currentHeight;
            recreateFont();
        }
    }

    public void setPosition(float x, float y) {
        float textWidth = ttc.getWidth();
        textNode.setLocalTranslation(x - textWidth / 2f, y, 0);
    }

    public Node getNode() {
        return textNode;
    }

    @Override
    public boolean intersects(Vector2f cursor) {
        return false;
    }
    @Override
    public void setActive(boolean active) {}

    @Override
    public void setHovered(boolean hovered) {}

    @Override
    public void handleMousePress(Vector2f cursor) {}

    @Override
    public void handleMouseDrag(Vector2f cursor) {}

    @Override
    public void handleMouseRelease() {}

    @Override
    public float getHeight() {
        return ttc.getTextHeight();
    }

    @Override
    public void setSize(float width, float height) {}

    @Override
    public float getWidth() {
        return ttc.getTextWidth();
    }
}
