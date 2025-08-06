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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced service for working with TrueType fonts (TTF).
 * <p>
 * Provides global caching, efficient updates, and thread-safe loader registration.
 */
public class TTFrenderer {

    /** Global cache keyed by "fontPath|style|masterSize" */
    private static final Map<String, TrueTypeFont<?, ?>> FONT_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean loaderRegistered = false;

    private final AssetManager assetManager;

    // Track current font parameters for reloading
    private String fontPath;
    private Style fontStyle;
    private int fontMasterSize;

    private TrueTypeFont<?, ?> font;
    private StringContainer textData;
    private TrueTypeContainer textGeometry;

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
     * Loads or retrieves a cached TrueTypeFont atlas.
     * @param fontPath   path to the TTF file in assets (non-null)
     * @param style      font style (non-null)
     * @param masterSize base size for atlas generation (must be >0)
     */
    public void generateFont(String fontPath, Style style, int masterSize) {
        Objects.requireNonNull(fontPath, "fontPath cannot be null");
        Objects.requireNonNull(style, "style cannot be null");
        if (masterSize <= 0) {
            throw new IllegalArgumentException("masterSize must be positive");
        }
        this.fontPath = fontPath;
        this.fontStyle = style;
        this.fontMasterSize = masterSize;

        String cacheKey = fontPath + '|' + style.name() + '|' + masterSize;
        this.font = FONT_CACHE.computeIfAbsent(cacheKey, key -> {
            TrueTypeKeyMesh keyMesh = new TrueTypeKeyMesh(fontPath, style, masterSize);
            return (TrueTypeFont<?, ?>) assetManager.loadAsset(keyMesh);
        });
    }

    /**
     * Creates or updates the rendered text geometry.
     * @param color text color (non-null)
     * @param text  string to render (non-null)
     */
    public void generateText(ColorRGBA color, String text) {
        Objects.requireNonNull(color, "color cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
        ensureFontLoaded();
        if (textData == null) {
            textData = new StringContainer(font, text);
            textGeometry = font.getFormattedText(textData, color);
        } else {
            textData.setText(text);
            textGeometry.updateGeometry();
            setColor(color);
        }
    }

    /** Update text and rebuild mesh. */
    public void setText(String text) {
        ensureTextGenerated();
        Objects.requireNonNull(text, "text cannot be null");
        textData.setText(text);
        textGeometry.updateGeometry();
    }

    /** Adjust geometry scale without regenerating atlas. */
    public void setScale(float scaleFactor) {
        ensureFontLoaded();
        font.setScale(scaleFactor);
        if (textGeometry != null) {
            textGeometry.updateGeometry();
        }
    }

    /**
     * Rebuilds atlas at a different master size. Use sparingly.
     * @param newMasterSize new base size (must be >0)
     */
    public void setMasterSize(int newMasterSize) {
        ensureTextGenerated();
        if (newMasterSize <= 0) {
            throw new IllegalArgumentException("newMasterSize must be positive");
        }
        // Preserve current text and color
        ColorRGBA currentColor = (ColorRGBA) textGeometry.getMaterial().getParam("Color").getValue();
        String currentText = textData.getText();
        // Regenerate font atlas with new size
        generateFont(fontPath, fontStyle, newMasterSize);
        // Regenerate text geometry
        generateText(currentColor, currentText);
    }

    /** Change text color efficiently. */
    public void setColor(ColorRGBA color) {
        ensureTextGenerated();
        Objects.requireNonNull(color, "color cannot be null");
        Material mat = textGeometry.getMaterial();
        mat.setColor("Color", color);
    }

    private void ensureFontLoaded() {
        if (font == null) {
            throw new IllegalStateException("generateFont() must be called first");
        }
    }

    private void ensureTextGenerated() {
        if (textData == null || textGeometry == null) {
            throw new IllegalStateException("generateText() must be called first");
        }
    }

    public TrueTypeFont<?, ?> getFont() { return font; }
    public StringContainer getTextData() { return textData; }
    public TrueTypeContainer getTextGeometry() { return textGeometry; }
}
