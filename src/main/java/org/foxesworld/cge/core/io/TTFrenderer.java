package org.foxesworld.cge.core.io;

import com.atr.jme.font.TrueTypeFont;
import com.atr.jme.font.asset.TrueTypeKeyMesh;
import com.atr.jme.font.asset.TrueTypeLoader;
import com.atr.jme.font.shape.TrueTypeContainer;
import com.atr.jme.font.util.StringContainer;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Централизованный сервис для загрузки и кеширования TTF шрифтов.
 */
public class TTFrenderer {

    private final AssetManager assetManager;
    private String fontPath;
    private ColorRGBA currentColor;

    private com.atr.jme.font.util.Style style;

    // Кэш шрифтов по ключу "path|style|size"
    private final Map<String, TrueTypeFont<?, ?>> fontCache = new ConcurrentHashMap<>();

    private TrueTypeFont<?, ?>    ttf;
    private StringContainer       stringContainer;
    private TrueTypeContainer     ttc;

    public TTFrenderer(AssetManager assetManager) {
        this.assetManager = assetManager;
        // Регистрируем загрузчик TTF-ассетов единожды
        this.assetManager.registerLoader(TrueTypeLoader.class, "ttf");
    }

    /**
     * Получает шрифт из кеша или загружает его если не найден.
     *
     * @param fontPath Путь к TTF-файлу в assets (с расширением .ttf).
     * @param style    Стиль (REGULAR, BOLD и т.д.).
     * @param size     "Мастер-размер" для рендеринга в атлас (качество).
     */
    @SuppressWarnings("unchecked")
    public void genTTF(String fontPath, com.atr.jme.font.util.Style style, int size) {
        this.fontPath = fontPath;
        this.style = style;
        String key = fontPath + '|' + style.name() + '|' + size;
        this.ttf = (TrueTypeFont<?, ?>) fontCache.computeIfAbsent(key, k -> {
            TrueTypeKeyMesh ttk = new TrueTypeKeyMesh(fontPath, style, size);
            return assetManager.loadAsset(ttk);
        });
    }

    /**
     * Генерирует текстовой контейнер (TrueTypeContainer) с заданным цветом и текстом.
     *
     * @param colorRGBA Цвет текста.
     * @param text      Сам текст.
     */
    public void genTTC(ColorRGBA colorRGBA, String text) {
        this.currentColor = colorRGBA;
        if (ttf == null) {
            throw new IllegalStateException("genTTF must be called before genTTC");
        }
        // Создаём или обновляем контейнер
        if (stringContainer == null) {
            stringContainer = new StringContainer(ttf, text);
        } else {
            stringContainer.setText(text);
        }
        ttc = ttf.getFormattedText(stringContainer, colorRGBA);
    }

    /** Обновляет текст в существующем контейнере. */
    public void setText(String text) {
        if (stringContainer == null) {
            throw new IllegalStateException("genTTC must be called before setText");
        }
        stringContainer.setText(text);
    }

    /** Меняет масштаб шрифта. */
    public void setScale(int size) {
        if (ttf == null) {
            throw new IllegalStateException("genTTF must be called before setScale");
        }
        fontCache.remove(fontPath + "|" + style.name() + "|" + size);
        this.genTTF(fontPath, style, size);
        this.genTTC(currentColor, stringContainer.toString());
    }

    public void setScale(float scaleFactor) {
        ttf.setScale(scaleFactor);
        if (ttc != null) {
            ttc.updateGeometry();    // пересобрать меш под новым scale
        }
    }

    /** Устанавливает новый цвет контейнера. */
    public void setColor(ColorRGBA color) {
        if (ttc == null) {
            throw new IllegalStateException("genTTC must be called before setColor");
        }
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        ttc.setMaterial(mat);
    }

    /** Возвращает текущий TrueTypeFont. */
    public TrueTypeFont<?, ?> getTtf() {
        return ttf;
    }

    /** Возвращает текущий StringContainer. */
    public StringContainer getStringContainer() {
        return stringContainer;
    }

    /** Возвращает текущий TrueTypeContainer (mesh + материал). */
    public TrueTypeContainer getTtc() {
        return ttc;
    }
}
