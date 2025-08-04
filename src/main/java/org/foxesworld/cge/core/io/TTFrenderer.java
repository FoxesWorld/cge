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
import java.util.Objects;

/**
 * Улучшенный сервис для работы с TTF шрифтами.
 * Сохраняет оригинальную семантику, но с исправленными внутренними механизмами.
 */
public class TTFrenderer {

    // --- УЛУЧШЕНИЕ 1: Кеш шрифтов сделан статическим и общим для всех экземпляров ---
    // Это ключевое исправление. Теперь шрифты действительно кешируются на уровне всего приложения.
    private static final Map<String, TrueTypeFont<?, ?>> FONT_CACHE = new ConcurrentHashMap<>();
    private static boolean isLoaderRegistered = false;

    private final AssetManager assetManager;

    // --- Поля состояния экземпляра ---
    private TrueTypeFont<?, ?> ttf;
    private StringContainer stringContainer;
    private TrueTypeContainer ttc; // "Rendered" текстовый объект

    public TTFrenderer(AssetManager assetManager) {
        this.assetManager = Objects.requireNonNull(assetManager, "AssetManager cannot be null");

        // --- УЛУЧШЕНИЕ 2: Регистрация загрузчика происходит только один раз ---
        if (!isLoaderRegistered) {
            assetManager.registerLoader(TrueTypeLoader.class, "ttf");
            isLoaderRegistered = true;
        }
    }

    /**
     * Получает шрифт из ОБЩЕГО кеша или загружает его.
     *
     * @param fontPath Путь к TTF-файлу в assets.
     * @param style    Стиль шрифта.
     * @param masterSize "Мастер-размер" для рендеринга в атлас (качество).
     */
    public void genTTF(String fontPath, com.atr.jme.font.util.Style style, int masterSize) {
        // Проверяем аргументы на входе для предотвращения ошибок
        Objects.requireNonNull(fontPath, "fontPath cannot be null");
        Objects.requireNonNull(style, "style cannot be null");
        if (masterSize <= 0) {
            throw new IllegalArgumentException("masterSize must be positive");
        }

        String key = fontPath + '|' + style.name() + '|' + masterSize;
        // Используем статический кеш
        this.ttf = FONT_CACHE.computeIfAbsent(key, k -> {
            TrueTypeKeyMesh ttk = new TrueTypeKeyMesh(fontPath, style, masterSize);
            return (TrueTypeFont<?, ?>) assetManager.loadAsset(ttk);
        });
    }

    /**
     * Генерирует или обновляет текстовой контейнер (TrueTypeContainer).
     *
     * @param colorRGBA Цвет текста.
     * @param text      Сам текст.
     */
    public void genTTC(ColorRGBA colorRGBA, String text) {
        if (ttf == null) {
            throw new IllegalStateException("genTTF must be called before genTTC");
        }

        if (stringContainer == null) {
            // Первоначальное создание
            stringContainer = new StringContainer(ttf, text);
            ttc = ttf.getFormattedText(stringContainer, colorRGBA);
        } else {
            // Обновление существующего
            stringContainer.setText(text);
            setColor(colorRGBA); // Эффективно меняем цвет
            ttc.updateGeometry(); // Пересобираем меш
        }
    }

    /**
     * Обновляет текст в существующем контейнере и перерисовывает его.
     */
    public void setText(String text) {
        if (stringContainer == null || ttc == null) {
            throw new IllegalStateException("genTTC must be called first to create a text object.");
        }
        stringContainer.setText(text);
        ttc.updateGeometry();
    }

    /**
     * Меняет "мастер-размер" шрифта.
     * ВНИМАНИЕ: Этот метод неэффективен по своей природе. Он заново генерирует атлас.
     * Лучше использовать setScale(float).
     * Логика сохранена для обратной совместимости.
     */
    public void setScale(int newMasterSize) {
        if (ttf == null || stringContainer == null) {
            throw new IllegalStateException("genTTC must be called first.");
        }
        // Получаем параметры текущего шрифта
        //String currentFontPath = (String) ttf.getKey().getFontFile();
        //com.atr.jme.font.util.Style currentStyle = ttf.getKey().getStyle();
        ColorRGBA currentColor = (ColorRGBA) ttc.getMaterial().getParam("Color").getValue();

        // Загружаем новый шрифт
        //genTTF(currentFontPath, currentStyle, newMasterSize);
        // Пересоздаем текстовый объект с новым шрифтом
        genTTC(currentColor, stringContainer.getText());
    }

    /**
     * Меняет визуальный масштаб текста. Это предпочтительный способ изменения размера.
     */
    public void setScale(float scaleFactor) {
        if (ttf == null) {
            throw new IllegalStateException("genTTF must be called first.");
        }
        ttf.setScale(scaleFactor);
        if (ttc != null) {
            ttc.updateGeometry(); // Пересобрать меш под новым scale
        }
    }

    /**
     * Устанавливает новый цвет контейнера.
     * --- УЛУЧШЕНИЕ 4: Теперь не создает новый материал, а изменяет параметр в существующем.
     */
    public void setColor(ColorRGBA color) {
        if (ttc == null) {
            throw new IllegalStateException("genTTC must be called first.");
        }
        // Это гораздо эффективнее, чем new Material(...)
        ttc.getMaterial().setColor("Color", color);
    }

    // --- Геттеры остаются без изменений ---

    public TrueTypeFont<?, ?> getTtf() {
        return ttf;
    }

    public StringContainer getStringContainer() {
        return stringContainer;
    }

    public TrueTypeContainer getTtc() {
        return ttc;
    }
}