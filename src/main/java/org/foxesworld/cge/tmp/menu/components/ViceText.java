package org.foxesworld.cge.tmp.menu.components;

import com.atr.jme.font.shape.TrueTypeContainer;
import com.jme3.app.Application;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import org.foxesworld.cge.core.io.TTFrenderer;
import org.foxesworld.cge.core.utils.ColorUtils;
import org.foxesworld.cge.tmp.menu.BuildContext;
import org.foxesworld.cge.tmp.menu.components.utils.InteractiveComponent;
import org.foxesworld.cge.tmp.menu.xml.TextXml;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * UI текстовый элемент с улучшенным позиционированием и масштабированием.
 * Поддерживает layout box (setSize), режим scaleToFit, pixel snapping и стиль.
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
    private float baseFontSize; // base px used when generating font

    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;

    private Anchor anchor = Anchor.CENTER;
    private Vector2f position = new Vector2f(); // position of layout box origin (top-left)

    private Style style;

    // layout box: where text should be laid out (in pixels, parent coordinates)
    // By default equals full window size.
    private float layoutWidth = Float.POSITIVE_INFINITY;
    private float layoutHeight = Float.POSITIVE_INFINITY;

    // behavior flags
    private boolean scaleToFit = false; // если true — масштабируем текст чтобы он поместился в layout
    private boolean pixelSnap = true;    // округлять позицию до пикселя

    // текущ applied scale (localScale applied to ttc)
    private float appliedScale = 1f;

    public ViceText(BuildContext context, TextXml textXml) {
        this(context, textXml, Style.fromTextXml(textXml));
    }

    public ViceText(BuildContext context, TextXml textXml, Style style) {
        super(textXml.id);
        this.app = context.mainMenuAppState().getGameEngine();
        this.textXml = textXml;
        this.fontSizeRaw = textXml.fontSize;
        this.style = style != null ? style : Style.defaultStyle();
        this.ttfRenderer = new TTFrenderer(app.getAssetManager());

        this.baseWidth = app.getContext().getSettings().getWidth();
        this.baseHeight = app.getContext().getSettings().getHeight();
        this.baseFontSize = parseFontSize(fontSizeRaw, baseWidth, baseHeight) * getDPIScale();

        // default layout = full window
        this.layoutWidth = baseWidth;
        this.layoutHeight = baseHeight;

        createFontOnce();
        updateScaleAndPosition();
    }

    private void createFontOnce() {
        int fontSizePx = Math.max(6, Math.round(baseFontSize));
        ttfRenderer.generateFont(style.fontPath, style.fontWeight, fontSizePx);
        ttfRenderer.generateText(style.color, textXml.text);
        ttc = ttfRenderer.getTextGeometry();
        this.attachChild(ttc);

        // ensure neutral scale initially
        appliedScale = 1f;
        ttc.setLocalScale(appliedScale);
    }

    private float getDPIScale() {
        int baseDpi = 96;
        int dpi = app.getContext().getSettings().getSamples();
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

    /**
     * Recalculate scale and position. Called on resize, setSize, setAnchor/setPosition etc.
     * - scaleFactor is desired size / baseFontSize
     * - if scaleToFit enabled, we additionally clamp scale so text fits in layout
     */
    private void updateScaleAndPosition() {
        if (ttc == null) return;

        int currentWidth = app.getContext().getSettings().getWidth();
        int currentHeight = app.getContext().getSettings().getHeight();

        float currentSize = parseFontSize(fontSizeRaw, currentWidth, currentHeight) * getDPIScale();
        float desiredScale = currentSize / baseFontSize;

        // neutral text geometry sizes (without localScale)
        float geomW = ttc.getTextWidth();
        float geomH = ttc.getTextHeight();

        float finalScale = desiredScale;

        if (scaleToFit && geomW > 0f && geomH > 0f) {
            float maxW = Float.isInfinite(layoutWidth) ? Float.POSITIVE_INFINITY : layoutWidth;
            float maxH = Float.isInfinite(layoutHeight) ? Float.POSITIVE_INFINITY : layoutHeight;

            // compute scale that would make geometry fit into layout
            float scaleForWidth = maxW / geomW;
            float scaleForHeight = maxH / geomH;

            float fitScale = Math.min(scaleForWidth, scaleForHeight);

            // only scale down (avoid upscaling beyond desiredScale unless fitScale > desiredScale and we want upscaling)
            finalScale = Math.min(desiredScale, fitScale);
            // if fitScale is huge (layout infinite) then finalScale remains desiredScale
        }

        appliedScale = finalScale;

        ttc.setLocalScale(appliedScale);

        // update position after scale change
        updatePosition();
    }

    private void updatePosition() {
        if (ttc == null) return;

        float textWidth = getWidth();   // already multiplied by localScale
        float textHeight = getHeight();

        // layout origin (top-left) is position.x, position.y
        float layoutX = position.x;
        float layoutY = position.y;

        float tx = layoutX;
        float ty = layoutY;

        switch (anchor) {
            case TOP_LEFT:
                tx = layoutX;
                ty = layoutY;
                break;
            case TOP_CENTER:
                tx = layoutX + (layoutWidth - textWidth) / 2f;
                ty = layoutY;
                break;
            case TOP_RIGHT:
                tx = layoutX + layoutWidth - textWidth;
                ty = layoutY;
                break;
            case CENTER_LEFT:
                tx = layoutX;
                ty = layoutY + (layoutHeight - textHeight) / 2f;
                break;
            case CENTER:
                tx = layoutX + (layoutWidth - textWidth) / 2f;
                ty = layoutY + (layoutHeight - textHeight) / 2f;
                break;
            case CENTER_RIGHT:
                tx = layoutX + layoutWidth - textWidth;
                ty = layoutY + (layoutHeight - textHeight) / 2f;
                break;
            case BOTTOM_LEFT:
                tx = layoutX;
                ty = layoutY + layoutHeight - textHeight;
                break;
            case BOTTOM_CENTER:
                tx = layoutX + (layoutWidth - textWidth) / 2f;
                ty = layoutY + layoutHeight - textHeight;
                break;
            case BOTTOM_RIGHT:
                tx = layoutX + layoutWidth - textWidth;
                ty = layoutY + layoutHeight - textHeight;
                break;
        }

        if (pixelSnap) {
            tx = Math.round(tx);
            ty = Math.round(ty);
        }

        // TrueTypeContainer expects Y-up or Y-down depending on your coordinate system.
        // Original code used setLocalTranslation(x,y,0) directly. We keep same.
        this.setLocalTranslation(tx, ty, 0);
    }

    @Override
    public void update(float tpf) {
        int currentWidth = app.getContext().getSettings().getWidth();
        int currentHeight = app.getContext().getSettings().getHeight();

        if (currentWidth != lastWindowWidth || currentHeight != lastWindowHeight) {
            lastWindowWidth = currentWidth;
            lastWindowHeight = currentHeight;
            // if window size changed, layout default (if user didn't call setSize) should track window
            if (Float.isInfinite(layoutWidth) || Float.isInfinite(layoutHeight)) {
                layoutWidth = currentWidth;
                layoutHeight = currentHeight;
            }
            updateScaleAndPosition();
        }
    }

    // --- public API improvements ---

    /**
     * Set layout box (in pixels). position is treated as top-left corner of this box.
     * Use Float.POSITIVE_INFINITY to indicate 'no limit' (default).
     */
    @Override
    public void setSize(float width, float height) {
        if (width > 0) this.layoutWidth = width; else this.layoutWidth = Float.POSITIVE_INFINITY;
        if (height > 0) this.layoutHeight = height; else this.layoutHeight = Float.POSITIVE_INFINITY;
        updateScaleAndPosition();
    }

    public void setLayoutOrigin(float x, float y) {
        this.position.set(x, y);
        updateScaleAndPosition();
    }

    @Override
    public float getHeight() {
        if (ttc == null) return 0f;
        return ttc.getTextHeight() * ttc.getLocalScale().y;
    }

    @Override
    public float getWidth() {
        if (ttc == null) return 0f;
        return ttc.getTextWidth() * ttc.getLocalScale().x;
    }

    public void setPosition(float x, float y) {
        setLayoutOrigin(x, y);
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
        updateScaleAndPosition();
    }

    public void setScaleToFit(boolean scaleToFit) {
        this.scaleToFit = scaleToFit;
        updateScaleAndPosition();
    }

    public void setPixelSnap(boolean pixelSnap) {
        this.pixelSnap = pixelSnap;
        updateScaleAndPosition();
    }

    @Override
    public boolean intersects(Vector2f cursor) {
        // simple bbox check against layout or text depending on needs
        float tx = getLocalTranslation().x;
        float ty = getLocalTranslation().y;
        float w = getWidth();
        float h = getHeight();
        return cursor.x >= tx && cursor.x <= tx + w && cursor.y >= ty && cursor.y <= ty + h;
    }

    @Override
    public void setActive(boolean active) { /* noop */ }

    @Override
    public void setHovered(boolean hovered) { /* noop */ }

    @Override
    public void handleMousePress(Vector2f cursor) { /* noop */ }

    @Override
    public void handleMouseDrag(Vector2f cursor) { /* noop */ }

    @Override
    public void handleMouseRelease() { /* noop */ }

    /**
     * Сеттер стиля: регенерирует шрифт/текст в соответствии с новым стилем.
     */
    public void setStyle(Style newStyle) {
        if (newStyle == null) return;
        this.style = newStyle;
        if (ttc != null) {
            this.detachChild(ttc);
            ttc = null;
        }
        createFontOnce();
        updateScaleAndPosition();
    }

    // Style class unchanged
    public static final class Style {
        public final String fontPath;
        public final com.atr.jme.font.util.Style fontWeight;
        public final ColorRGBA color;
        public final float letterSpacing;
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
            ColorRGBA col = ColorUtils.fromHexString(Optional.ofNullable(xml.color).orElse("#FFFFFF"));
            return new Style(font, w, col, 0f, 0f, false, new ColorRGBA(0,0,0,0.5f), new Vector2f(1f, -1f));
        }
    }

    public Style getStyle() {
        return style;
    }

    // simple logging helpers
    private void log(String fmt, Object... args) { System.out.println("[ViceText] " + String.format(fmt, args)); }
    private void logErr(String fmt, Object... args) { System.err.println("[ViceText] " + String.format(fmt, args)); }
}
