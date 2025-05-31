package org.foxesworld.cge.ui.elements;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;
import com.jme3.math.ColorRGBA;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.ui.AbstractUIElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TextElement — текстовый лейбл на GUI.
 * Доработано так, чтобы при обновлении текста не “расползались” размеры панели.
 *
 * Поддерживает:
 *   - атрибуты color="r,g,b,a", fontSize="20", fontPath="...", posX="..", posY="..", align=".."
 *   - onClick="methodName" (через RawInputListener)
 */
public class TextElement extends AbstractUIElement implements RawInputListener {
    private static final Logger logger = LoggerFactory.getLogger(TextElement.class);

    private BitmapText bitmapText;
    private AssetManager assetManager;
    private CalistaGameEngine calistaGameEngine;

    // Сырые (относительные) координаты, задаются через XML-параметры posX и posY.
    private float rawPosX = 0f;
    private float rawPosY = 0f;

    // Текущий цвет и путь к шрифту
    private ColorRGBA color = ColorRGBA.White.clone();
    private String fontPath;
    private float fontSize;

    public TextElement(CalistaGameEngine engine, String id, PanelElement parent, String defaultFontPath, float defaultFontSize) {
        this.id = id;
        this.calistaGameEngine = engine;
        this.parentPanel = parent;
        this.assetManager = engine.getAssetManager();
        this.fontPath = defaultFontPath;
        this.fontSize = defaultFontSize;
        this.node.setName("Text_" + id);

        // Инициализируем BitmapText
        BitmapFont font = assetManager.loadFont(fontPath);
        bitmapText = new BitmapText(font, false);
        bitmapText.setSize(fontSize);
        bitmapText.setColor(color);
        bitmapText.setText(""); // пока пуст*/
        // **Устанавливаем смещение так, чтобы (0,0) узла было в левом-верхнем углу текста.**
        bitmapText.setLocalTranslation(0f, bitmapText.getLineHeight(), 0f);
        node.attachChild(bitmapText);
    }

    /** Устанавливает текст и обновляет смещение */
    public void setText(String newText) {
        bitmapText.setText(newText);
        // Обновляем локальное смещение, т.к. после установки текста высота строки может измениться
        bitmapText.setLocalTranslation(0, bitmapText.getLineHeight(), 0f);
    }

    /** Возвращает фактическую ширину строки (в пикселях) */
    public float getWidth() {
        return bitmapText.getLineWidth();
    }

    /** Возвращает фактическую высоту строки (в пикселях) */
    public float getHeight() {
        return bitmapText.getLineHeight();
    }

    public float getRawPosX() {
        return rawPosX;
    }

    public float getRawPosY() {
        return rawPosY;
    }

    @Override
    public boolean hasOwnAlign() {
        return ownAlign != null;
    }

    @Override
    public String getOwnAlign() {
        return ownAlign;
    }

    @Override
    public void setProperty(String key, String value) {
        switch (key) {
            case "text":
                setText(value);
                break;
            case "color":
                this.color = parseColor(value);
                bitmapText.setColor(color);
                break;
            case "fontPath":
                this.fontPath = value;
                try {
                    BitmapFont f = assetManager.loadFont(fontPath);
                    //bitmapText.setFont(f);
                } catch (Exception e) {
                    logger.warn("TextElement '{}' failed to load font '{}'", id, fontPath);
                }
                // После смены шрифта нужно сбросить смещение по высоте
                bitmapText.setLocalTranslation(0, bitmapText.getLineHeight(), 0f);
                break;
            case "fontSize":
                this.fontSize = Float.parseFloat(value);
                bitmapText.setSize(fontSize);
                bitmapText.setLocalTranslation(0, bitmapText.getLineHeight(), 0f);
                break;
            case "posX":
                this.rawPosX = Float.parseFloat(value);
                break;
            case "posY":
                this.rawPosY = Float.parseFloat(value);
                break;
            case "align":
                this.ownAlign = value;
                break;
            default:
                logger.warn("TextElement '{}' unknown property '{}'", id, key);
                break;
        }
    }

    @Override
    public void setOnClickHandler(String methodName, Object eventHandlerTarget) {
        super.setOnClickHandler(methodName, eventHandlerTarget);
        // Подписываемся как RawInputListener, чтобы ловить клики
        calistaGameEngine.getInputManager().addRawInputListener(this);
    }

    /** Разбор строки "r,g,b,a" → ColorRGBA */
    private ColorRGBA parseColor(String s) {
        String[] parts = s.split(",");
        try {
            float r = Float.parseFloat(parts[0].trim());
            float g = Float.parseFloat(parts[1].trim());
            float b = Float.parseFloat(parts[2].trim());
            float a = Float.parseFloat(parts[3].trim());
            return new ColorRGBA(r, g, b, a);
        } catch (Exception e) {
            logger.warn("TextElement '{}' failed to parse color '{}'", id, s);
            return ColorRGBA.White.clone();
        }
    }

    public BitmapText getBitmapText() {
        return bitmapText;
    }

    /**
     * Обрабатываем нажатия мыши. Если клик попадёт в AABB текста,
     * вызываем triggerClick().
     */
    @Override
    public void onMouseButtonEvent(MouseButtonEvent evt) {
        // Срабатываем только на нажатие (не отпускание)
        if (!evt.isPressed()) return;

        // Получаем экранные координаты клика
        float clickX = evt.getX();
        float clickY = evt.getY();

        // Позиция текста (левый-верхний угол) в GUI-координатах:
        float tx = bitmapText.getLocalTranslation().x;
        float ty = bitmapText.getLocalTranslation().y;

        // Ширина и высота текста
        float w = getWidth();
        float h = getHeight();

        // В JME GUI-координаты: (0,0) – левый-нижний угол экрана,
        // BitmapText рисуется снизу-вверх, поэтому верхний край текста = ty,
        // низ текста = ty – h. Проверка попадания (AABB).
        boolean insideX = (clickX >= tx && clickX <= tx + w);
        boolean insideY = (clickY <= ty && clickY >= ty - h);

        if (insideX && insideY) {
            logger.debug("TextElement '{}' clicked at ({},{})", id, clickX, clickY);
            triggerClick();
        }
    }

    // Остальные методы RawInputListener остаются пустыми:
    @Override public void beginInput() { }
    @Override public void endInput() { }
    @Override public void onMouseMotionEvent(MouseMotionEvent evt) { }
    @Override public void onKeyEvent(KeyInputEvent evt) { }
    @Override public void onTouchEvent(TouchEvent evt) { }
    @Override public void onJoyAxisEvent(JoyAxisEvent evt) { }
    @Override public void onJoyButtonEvent(JoyButtonEvent evt) { }
}
