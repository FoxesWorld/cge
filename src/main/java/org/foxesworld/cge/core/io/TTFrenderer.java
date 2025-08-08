package org.foxesworld.cge.core.io;

import com.atr.jme.font.TrueTypeFont;
import com.atr.jme.font.asset.TrueTypeKeyMesh;
import com.atr.jme.font.asset.TrueTypeLoader;
import com.atr.jme.font.shape.TrueTypeContainer;
import com.atr.jme.font.util.StringContainer;
import com.atr.jme.font.util.Style;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Improved TTF renderer with caching and smooth scale animation.
 *
 * Usage pattern:
 * - generateFont(path, style, masterSize)
 * - generateText(color, text)
 * - optionally call animateScaleTo(...) and update(tpf) each frame
 * - if needed, call setMasterSize(...) to rebuild atlas at different base size
 */
public class TTFrenderer {

    /** Global cache keyed by "fontPath|style|masterSize" */
    private static final Map<String, TrueTypeFont<?, ?>> FONT_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean loaderRegistered = false;

    private final AssetManager assetManager;

    // current font atlas parameters
    private String fontPath;
    private Style fontStyle;
    private int fontMasterSize;

    private TrueTypeFont<?, ?> font;
    private StringContainer textData;
    private TrueTypeContainer textGeometry;

    // scale animation state (applies to font.setScale)
    private float currentScale = 1f;
    private float targetScale = 1f;
    private float scaleSpeed = 6f; // higher -> faster interpolation

    public TTFrenderer(AssetManager assetManager) {
        this.assetManager = Objects.requireNonNull(assetManager, "AssetManager cannot be null");
        registerLoaderOnce();
    }

    private void registerLoaderOnce() {
        if (!loaderRegistered) {
            synchronized (TTFrenderer.class) {
                if (!loaderRegistered) {
                    assetManager.registerLoader(TrueTypeLoader.class, "ttf");
                    loaderRegistered = true;
                }
            }
        }
    }

    /**
     * Loads or retrieves cached TrueTypeFont atlas.
     *
     * @param fontPath   path to ttf in assets
     * @param style      style enum
     * @param masterSize base atlas size (px)
     */
    public void generateFont(String fontPath, Style style, int masterSize) {
        Objects.requireNonNull(fontPath, "fontPath");
        Objects.requireNonNull(style, "style");
        if (masterSize <= 0) throw new IllegalArgumentException("masterSize must be > 0");

        this.fontPath = fontPath;
        this.fontStyle = style;
        this.fontMasterSize = masterSize;

        String key = fontPath + '|' + style.name() + '|' + masterSize;
        this.font = FONT_CACHE.computeIfAbsent(key, k -> {
            TrueTypeKeyMesh meshKey = new TrueTypeKeyMesh(fontPath, style, masterSize);
            return (TrueTypeFont<?, ?>) assetManager.loadAsset(meshKey);
        });

        // reset scales when new font atlas selected
        this.currentScale = 1f;
        this.targetScale = 1f;
        if (font != null) font.setScale(currentScale);
    }

    /**
     * Create or update the rendered text geometry.
     *
     * @param color text color
     * @param text  content
     */
    public void generateText(ColorRGBA color, String text) {
        ensureFontLoaded();
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(text, "text");

        if (textData == null) {
            textData = new StringContainer(font, text);
            textGeometry = font.getFormattedText(textData, color);
        } else {
            textData.setText(text);
            // update geometry and color
            textGeometry.updateGeometry();
            setColor(color);
        }

        // ensure geometry uses current scale
        font.setScale(currentScale);
        if (textGeometry != null) textGeometry.updateGeometry();
    }

    /** Update text string (keeps color). */
    public void setText(String text) {
        ensureTextGenerated();
        Objects.requireNonNull(text, "text");
        textData.setText(text);
        textGeometry.updateGeometry();
    }

    /** Instant scale change applied to geometry (no animation). */
    public void setScaleInstant(float scale) {
        ensureFontLoaded();
        if (scale <= 0f) throw new IllegalArgumentException("scale must be > 0");
        this.currentScale = this.targetScale = scale;
        font.setScale(currentScale);
        if (textGeometry != null) textGeometry.updateGeometry();
    }

    /**
     * Animate scale towards newScale using given speed (units/sec-ish).
     * Call update(tpf) every frame to drive animation.
     *
     * @param newScale desired scale multiplier (1.0 = masterSize)
     * @param speed    interpolation speed (higher = faster)
     */
    public void animateScaleTo(float newScale, float speed) {
        ensureFontLoaded();
        if (newScale <= 0f) throw new IllegalArgumentException("newScale must be > 0");
        this.targetScale = newScale;
        if (speed > 0f) this.scaleSpeed = speed;
    }

    /**
     * Update animation. Call once per frame with frame delta.
     *
     * @param tpf time per frame (seconds)
     */
    public void update(float tpf) {
        if (font == null) return;
        if (Math.abs(targetScale - currentScale) > 0.0005f) {
            float alpha = FastMath.clamp(tpf * scaleSpeed, 0f, 1f);
            currentScale = FastMath.interpolateLinear(alpha, currentScale, targetScale);
            font.setScale(currentScale);
            if (textGeometry != null) textGeometry.updateGeometry();
        }
    }

    /**
     * Rebuild atlas at different master size. Expensive; use sparingly.
     * Preserves current text and color if present.
     *
     * @param newMasterSize positive integer
     */
    public void setMasterSize(int newMasterSize) {
        ensureFontLoaded();
        if (newMasterSize <= 0) throw new IllegalArgumentException("newMasterSize must be > 0");

        // preserve state
        String currentText = (textData != null) ? textData.getText() : null;
        ColorRGBA color = null;
        if (textGeometry != null) {
            try {
                Material mat = textGeometry.getMaterial();
                if (mat != null && mat.getParam("Color") != null) {
                    color = (ColorRGBA) mat.getParam("Color").getValue();
                }
            } catch (Exception ignored) { }
        }

        // generate new atlas and re-create geometry
        generateFont(fontPath, fontStyle, newMasterSize);

        if (currentText != null) {
            // if color is null, use white safe default
            generateText((color != null) ? color : ColorRGBA.White, currentText);
        }
    }

    /** Change the color of existing text (fast). */
    public void setColor(ColorRGBA color) {
        ensureTextGenerated();
        Objects.requireNonNull(color, "color");
        Material mat = textGeometry.getMaterial();
        if (mat != null) {
            mat.setColor("Color", color);
        }
    }

    /** Get current formatted text geometry (may be null). */
    public TrueTypeContainer getTextGeometry() { return textGeometry; }

    /** Get current TrueTypeFont atlas (may be null). */
    public TrueTypeFont<?, ?> getFont() { return font; }

    /** Convenience: width in world units (or 0 if no text). */
    public float getTextWidth() {
        return (textGeometry != null) ? textGeometry.getWidth() : 0f;
    }

    /** Convenience: height in world units (or 0 if no text). */
    public float getTextHeight() {
        return (textGeometry != null) ? textGeometry.getHeight() : 0f;
    }

    private void ensureFontLoaded() {
        if (font == null) {
            throw new IllegalStateException("generateFont() must be called before using text features");
        }
    }

    private void ensureTextGenerated() {
        if (textData == null || textGeometry == null) {
            throw new IllegalStateException("generateText() must be called before manipulating text");
        }
    }
}