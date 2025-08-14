package org.foxesworld.cge.tmp.menu.components;

import com.atr.jme.font.shape.TrueTypeContainer;
import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import org.foxesworld.cge.core.io.TTFrenderer;
import org.foxesworld.cge.core.utils.ColorUtils;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.xml.TextXml;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * UI текстовый элемент с адаптивным масштабированием и выравниванием
 * Оптимизирован: шрифт создаётся один раз, при ресайзе масштабируется геометрия
 *
 * Теперь поддерживает текстовый Style (вложенный класс Style).
 */
public final class ViceText extends UIComponent implements InteractiveComponent {

    public enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    private final Application app;
    private final TextXml textXml;
    private final String fontSizeRaw;

    private TTFrenderer ttfRenderer;
    private TrueTypeContainer ttc;

    private int baseWidth;
    private int baseHeight;
    private float baseFontSize;

    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;

    private Anchor anchor = Anchor.CENTER;
    private Vector2f position = new Vector2f();

    // новый: стиль текста
    private Style style;

    // Конструктор: прежний сохраняется и использует стиль из xml или дефолтный
    public ViceText(Application app, AssetManager assetManager, TextXml textXml) {
        this(app, assetManager, textXml, Style.fromTextXml(textXml));
    }

    // Новый: конструктор с явным стилем
    public ViceText(Application app, AssetManager assetManager, TextXml textXml, Style style) {
        super(textXml.id);
        this.app = app;
        this.textXml = textXml;
        this.fontSizeRaw = textXml.fontSize;
        this.style = style != null ? style : Style.defaultStyle();
        this.ttfRenderer = new TTFrenderer(assetManager);
        //this.textNode = new Node("ViceText: " + textXml.text);

        this.baseWidth = app.getContext().getSettings().getWidth();
        this.baseHeight = app.getContext().getSettings().getHeight();
        this.baseFontSize = parseFontSize(fontSizeRaw, baseWidth, baseHeight) * getDPIScale();

        createFontOnce();
        updateScale();
    }

    private void createFontOnce() {
        // Генерируем шрифт согласно style
        int fontSizePx = Math.max(6, Math.round(baseFontSize)); // нижняя граница
        ttfRenderer.generateFont(style.fontPath, style.fontWeight, fontSizePx);
        ttfRenderer.generateText(style.color, textXml.text);
        ttc = ttfRenderer.getTextGeometry();
        this.attachChild(ttc);
    }

    private float getDPIScale() {
        int baseDpi = 96;
        int dpi = app.getContext().getSettings().getSamples(); // Если хранится в Settings
        if (dpi <= 0) dpi = baseDpi;
        return dpi / (float) baseDpi;
    }

    private float parseFontSize(String raw, int width, int height) {
        String value = Optional.ofNullable(raw)
                .map(String::trim)
                .map(String::toLowerCase)
                .orElse("");

        try {
            if (value.endsWith("vmin")) {
                int minSide = Math.min(width, height);
                return minSide * Float.parseFloat(value.substring(0, value.length() - 4)) / 100f;
            }
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

    private void updateScale() {
        int currentWidth = app.getContext().getSettings().getWidth();
        int currentHeight = app.getContext().getSettings().getHeight();

        float currentSize = parseFontSize(fontSizeRaw, currentWidth, currentHeight) * getDPIScale();
        float scaleFactor = currentSize / baseFontSize;

        if (ttc != null) {
            ttc.setLocalScale(scaleFactor);
            updatePosition();
        }
    }

    private void updatePosition() {
        float textWidth = getWidth();
        float textHeight = getHeight();
        float x = position.x;
        float y = position.y;

        switch (anchor) {
            case TOP_LEFT:
                this.setLocalTranslation(x, y, 0);
                break;
            case TOP_CENTER:
                this.setLocalTranslation(x - textWidth / 2f, y, 0);
                break;
            case TOP_RIGHT:
                this.setLocalTranslation(x - textWidth, y, 0);
                break;
            case CENTER_LEFT:
                this.setLocalTranslation(x, y - textHeight / 2f, 0);
                break;
            case CENTER:
                this.setLocalTranslation(x - textWidth / 2f, y - textHeight / 2f, 0);
                break;
            case CENTER_RIGHT:
                this.setLocalTranslation(x - textWidth, y - textHeight / 2f, 0);
                break;
            case BOTTOM_LEFT:
                this.setLocalTranslation(x, y - textHeight, 0);
                break;
            case BOTTOM_CENTER:
                this.setLocalTranslation(x - textWidth / 2f, y - textHeight, 0);
                break;
            case BOTTOM_RIGHT:
                this.setLocalTranslation(x - textWidth, y - textHeight, 0);
                break;
        }
    }

    @Override
    public void update(float tpf) {
        int currentWidth = app.getContext().getSettings().getWidth();
        int currentHeight = app.getContext().getSettings().getHeight();

        if (currentWidth != lastWindowWidth || currentHeight != lastWindowHeight) {
            lastWindowWidth = currentWidth;
            lastWindowHeight = currentHeight;
            updateScale();
        }
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
        updatePosition();
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
        updatePosition();
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
        if (ttc == null) return 0f;
        return ttc.getTextHeight() * ttc.getLocalScale().y;
    }

    @Override
    public void setSize(float width, float height) {}

    @Override
    public float getWidth() {
        if (ttc == null) return 0f;
        return ttc.getTextWidth() * ttc.getLocalScale().x;
    }

    /**
     * Сеттер стиля: регенерирует шрифт/текст в соответствии с новым стилем.
     */
    public void setStyle(Style newStyle) {
        if (newStyle == null) return;
        this.style = newStyle;
        // удаляем старую геометрию и создаём заново
        if (ttc != null) {
            this.detachChild(ttc);
            ttc = null;
        }
        createFontOnce();
        updateScale();
    }

    public static final class Style {
        public final String fontPath;
        public final com.atr.jme.font.util.Style fontWeight; // используй com.atr... полностью
        public final ColorRGBA color;
        public final float letterSpacing; // не используется сейчас, но доступно для будущей логики
        public final float lineSpacing;
        public final boolean useShadow;
        public final ColorRGBA shadowColor;
        public final Vector2f shadowOffset;

        public Style(String fontPath,
                     com.atr.jme.font.util.Style fontWeight,
                     ColorRGBA color,
                     float letterSpacing,
                     float lineSpacing,
                     boolean useShadow,
                     ColorRGBA shadowColor,
                     Vector2f shadowOffset) {
            this.fontPath = fontPath;
            this.fontWeight = fontWeight;
            this.color = color;
            this.letterSpacing = letterSpacing;
            this.lineSpacing = lineSpacing;
            this.useShadow = useShadow;
            this.shadowColor = shadowColor;
            this.shadowOffset = shadowOffset;
        }

        public static Style defaultStyle() {
            return new Style(
                    "assets/Interface/fonts/Docker One.ttf",
                    com.atr.jme.font.util.Style.Plain,
                    ColorRGBA.White.clone(),
                    0f,
                    0f,
                    false,
                    new ColorRGBA(0,0,0,0.5f),
                    new Vector2f(1f, -1f)
            );
        }

        public static Style fromTextXml(TextXml xml) {
            String font = Optional.ofNullable(xml.fontPath).filter(s -> !s.isBlank()).orElse("assets/Interface/fonts/Docker One.ttf");
            com.atr.jme.font.util.Style w = com.atr.jme.font.util.Style.Plain;
            // Если в xml есть поле weight, можно распарсить (просто пример; TextXml может не иметь)
            // try { w = com.atr.jme.font.util.Style.valueOf(xml.weight.toUpperCase()); } catch(...) {}

            ColorRGBA col = ColorUtils.fromHexString(Optional.ofNullable(xml.color).orElse("#FFFFFF"));

            return new Style(font, w, col, 0f, 0f, false, new ColorRGBA(0,0,0,0.5f), new Vector2f(1f, -1f));
        }
    }

    public Style getStyle() {
        return style;
    }
}
