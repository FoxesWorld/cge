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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private TTFrenderer measureRenderer;
    private TrueTypeContainer ttc;

    private int baseWidth;
    private int baseHeight;
    private float baseFontSize;

    private int lastWindowWidth = -1;
    private int lastWindowHeight = -1;

    private Anchor anchor = Anchor.CENTER;
    private Vector2f position = new Vector2f();

    private Style style;

    private float layoutWidth = Float.POSITIVE_INFINITY;
    private float layoutHeight = Float.POSITIVE_INFINITY;

    private boolean scaleToFit = false;
    private boolean pixelSnap = true;

    private float appliedScale = 1f;

    private boolean wrapEnabled = true;

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
        this.measureRenderer = new TTFrenderer(app.getAssetManager());

        this.baseWidth = app.getContext().getSettings().getWidth();
        this.baseHeight = app.getContext().getSettings().getHeight();
        this.baseFontSize = parseFontSize(fontSizeRaw, baseWidth, baseHeight) * getDPIScale();

        this.layoutWidth = baseWidth;
        this.layoutHeight = baseHeight;

        createFontOnce();
        updateScaleAndPosition();
    }

    private void createFontOnce() {
        int fontSizePx = Math.max(6, Math.round(baseFontSize));
        ttfRenderer.generateFont(style.fontPath, style.fontWeight, fontSizePx);
        measureRenderer.generateFont(style.fontPath, style.fontWeight, fontSizePx);

        ttfRenderer.generateText(style.color, textXml.text);
        ttc = ttfRenderer.getTextGeometry();
        this.attachChild(ttc);
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

    private float measureWidth(String s) {
        if (s == null || s.isEmpty()) return 0f;
        measureRenderer.generateText(style.color, s);
        TrueTypeContainer mt = measureRenderer.getTextGeometry();
        if (mt == null) return 0f;
        return mt.getTextWidth();
    }

    private String buildWrappedString(String text, float maxGeomWidth) {
        if (!wrapEnabled || maxGeomWidth <= 0f || Float.isInfinite(maxGeomWidth)) {
            return text;
        }
        String[] paragraphs = text.split("\n", -1);
        List<String> outParagraphs = new ArrayList<>(paragraphs.length);

        for (String para : paragraphs) {
            String trimmedPara = para.replaceAll("\\s+", " ").trim();
            if (trimmedPara.isEmpty()) {
                outParagraphs.add("");
                continue;
            }

            String[] words = trimmedPara.split(" ");
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                String w = words[i];
                if (current.length() == 0) {
                    float wWidth = measureWidth(w);
                    if (wWidth <= maxGeomWidth) {
                        current.append(w);
                    } else {
                        List<String> parts = breakLongWord(w, maxGeomWidth);
                        if (!parts.isEmpty()) {
                            current.append(parts.get(0));
                            outParagraphs.add(current.toString());
                            for (int p = 1; p < parts.size(); p++) outParagraphs.add(parts.get(p));
                            current = new StringBuilder();
                        }
                    }
                } else {
                    String cand = current.toString() + " " + w;
                    float candWidth = measureWidth(cand);
                    if (candWidth <= maxGeomWidth) {
                        current.append(" ").append(w);
                    } else {
                        outParagraphs.add(current.toString());
                        current = new StringBuilder();
                        float wWidth = measureWidth(w);
                        if (wWidth <= maxGeomWidth) {
                            current.append(w);
                        } else {
                            List<String> parts = breakLongWord(w, maxGeomWidth);
                            if (!parts.isEmpty()) {
                                current.append(parts.get(0));
                                outParagraphs.add(current.toString());
                                for (int p = 1; p < parts.size(); p++) outParagraphs.add(parts.get(p));
                                current = new StringBuilder();
                            }
                        }
                    }
                }
            }

            if (current.length() > 0) outParagraphs.add(current.toString());
        }

        StringBuilder res = new StringBuilder();
        for (int i = 0; i < outParagraphs.size(); i++) {
            if (i > 0) res.append('\n');
            res.append(outParagraphs.get(i));
        }
        return res.toString();
    }

    private List<String> breakLongWord(String word, float maxGeomWidth) {
        List<String> parts = new ArrayList<>();
        if (word == null || word.isEmpty()) return parts;
        int start = 0;
        int len = word.length();
        while (start < len) {
            int lo = start + 1;
            int hi = len;
            int best = start;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                String candidate = word.substring(start, mid);
                float w = measureWidth(candidate);
                if (w <= maxGeomWidth) { best = mid; lo = mid + 1; }
                else hi = mid - 1;
            }
            if (best == start) {
                best = Math.min(start + 1, len);
            }
            parts.add(word.substring(start, best));
            start = best;
        }
        return parts;
    }

    private void updateScaleAndPosition() {
        if (ttc == null) return;
        int currentWidth = app.getContext().getSettings().getWidth();
        int currentHeight = app.getContext().getSettings().getHeight();
        float currentSize = parseFontSize(fontSizeRaw, currentWidth, currentHeight) * getDPIScale();
        float desiredScale = currentSize / baseFontSize;

        float geomW = ttc.getTextWidth();
        float geomH = ttc.getTextHeight();

        float finalScale = desiredScale;
        if (scaleToFit && geomW > 0f && geomH > 0f) {
            float maxW = Float.isInfinite(layoutWidth) ? Float.POSITIVE_INFINITY : layoutWidth;
            float maxH = Float.isInfinite(layoutHeight) ? Float.POSITIVE_INFINITY : layoutHeight;
            float scaleForWidth = maxW / geomW;
            float scaleForHeight = maxH / geomH;
            float fitScale = Math.min(scaleForWidth, scaleForHeight);
            finalScale = Math.min(desiredScale, fitScale);
        }
        appliedScale = finalScale;

        if (wrapEnabled && !Float.isInfinite(layoutWidth) && layoutWidth > 0f) {
            float maxGeomWidth = layoutWidth / Math.max(1e-6f, appliedScale);
            String wrapped = buildWrappedString(textXml.text == null ? "" : textXml.text, maxGeomWidth);
            try {
                if (ttc != null && ttc.getParent() != null) this.detachChild(ttc);
            } catch (Throwable ignored) {}
            ttfRenderer.generateText(style.color, wrapped);
            ttc = ttfRenderer.getTextGeometry();
            if (ttc.getParent() == null) this.attachChild(ttc);
        } else {
            try {
                if (ttc != null && ttc.getParent() != null) this.detachChild(ttc);
            } catch (Throwable ignored) {}
            ttfRenderer.generateText(style.color, textXml.text == null ? "" : textXml.text);
            ttc = ttfRenderer.getTextGeometry();
            if (ttc.getParent() == null) this.attachChild(ttc);
        }

        ttc.setLocalScale(appliedScale);
        updatePosition();
    }

    private void updatePosition() {
        if (ttc == null) return;
        float textWidth = getWidth();
        float textHeight = getHeight();
        float layoutX = position.x;
        float layoutY = position.y;
        float tx = layoutX;
        float ty = layoutY;
        switch (anchor) {
            case TOP_LEFT: tx = layoutX; ty = layoutY; break;
            case TOP_CENTER: tx = layoutX + (layoutWidth - textWidth) / 2f; ty = layoutY; break;
            case TOP_RIGHT: tx = layoutX + layoutWidth - textWidth; ty = layoutY; break;
            case CENTER_LEFT: tx = layoutX; ty = layoutY + (layoutHeight - textHeight) / 2f; break;
            case CENTER: tx = layoutX + (layoutWidth - textWidth) / 2f; ty = layoutY + (layoutHeight - textHeight) / 2f; break;
            case CENTER_RIGHT: tx = layoutX + layoutWidth - textWidth; ty = layoutY + (layoutHeight - textHeight) / 2f; break;
            case BOTTOM_LEFT: tx = layoutX; ty = layoutY + layoutHeight - textHeight; break;
            case BOTTOM_CENTER: tx = layoutX + (layoutWidth - textWidth) / 2f; ty = layoutY + layoutHeight - textHeight; break;
            case BOTTOM_RIGHT: tx = layoutX + layoutWidth - textWidth; ty = layoutY + layoutHeight - textHeight; break;
        }
        if (pixelSnap) {
            tx = Math.round(tx);
            ty = Math.round(ty);
        }
        this.setLocalTranslation(tx, ty, 0);
    }

    @Override
    public void update(float tpf) {
        int currentWidth = app.getContext().getSettings().getWidth();
        int currentHeight = app.getContext().getSettings().getHeight();
        if (currentWidth != lastWindowWidth || currentHeight != lastWindowHeight) {
            lastWindowWidth = currentWidth;
            lastWindowHeight = currentHeight;
            if (Float.isInfinite(layoutWidth) || Float.isInfinite(layoutHeight)) {
                layoutWidth = currentWidth;
                layoutHeight = currentHeight;
            }
            updateScaleAndPosition();
        }
    }

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
        float tx = getLocalTranslation().x;
        float ty = getLocalTranslation().y;
        float w = getWidth();
        float h = getHeight();
        return cursor.x >= tx && cursor.x <= tx + w && cursor.y >= ty && cursor.y <= ty + h;
    }

    @Override
    public void setActive(boolean active) {}

    @Override
    public void setHovered(boolean hovered) {}

    @Override
    public void handleMouseEnter(Vector2f cursor) {

    }

    @Override
    public void handleMouseExit(Vector2f cursor) {

    }

    @Override
    public void handleMouseMove(Vector2f cursor) {

    }

    @Override
    public void handleMouseClick(Vector2f cursor) {

    }

    @Override
    public void handleMousePress(Vector2f cursor) {}

    @Override
    public void handleMouseDoubleClick(Vector2f cursor) {

    }

    @Override
    public void handleMouseRelease(Vector2f cursor) {

    }

    @Override
    public void handleMouseDrag(Vector2f cursor) {}

    @Override
    public void handleMouseScroll(Vector2f cursor, float delta) {

    }

    @Override
    public void handleDoubleClick(Vector2f cursor) {

    }

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

    public void setText(String newText) {
        if (newText == null) newText = "";
        textXml.text = newText;
        updateScaleAndPosition();
    }

    public void setWrappedText(String newText) {
        setText(newText);
    }

    public void setWrapEnabled(boolean enabled) {
        this.wrapEnabled = enabled;
        updateScaleAndPosition();
    }

    public boolean isWrapEnabled() {
        return wrapEnabled;
    }

    public Style getStyle() {
        return style;
    }

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
}
