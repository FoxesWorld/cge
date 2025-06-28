package org.foxesworld.cge.modules.ui.novaUi.elements.button;

import com.jme3.math.FastMath;
import com.jme3.math.ColorRGBA;

/**
 * ButtonAnimator — эпичная анимация кнопки в стиле GTA V:
 * - Пульсация свечения по контуру.
 * - Плавное увеличение при наведении.
 * - Импульс яркости и размера при клике.
 */
public class ButtonAnimator {

    private final ButtonRenderer renderer;

    private float hoverLerp = 0f;     // 0..1 - степень "наведения"
    private float pressedLerp = 0f;   // 0..1 - степень "нажатия"
    private float highlightPulse = 0f; // для свечения

    private boolean hovered = false;
    private boolean pressed = false;

    private float pressTime = 0f;
    private static final float PRESS_ANIM_DURATION = 0.18f;

    public ButtonAnimator(ButtonRenderer renderer) {
        this.renderer = renderer;
    }

    // Вызывайте при наведении мыши
    public void onHoverEnter() {
        hovered = true;
    }

    public void onHoverExit() {
        hovered = false;
    }

    // Вызывайте при нажатии на кнопку
    public void onPress() {
        pressed = true;
        pressTime = PRESS_ANIM_DURATION;
    }

    // Вызывайте при отпускании кнопки
    public void onRelease() {
        pressed = false;
    }

    // Вызывать каждый кадр
    public void update(float tpf) {
        // Плавный переход hover-эффекта
        float targetHover = hovered ? 1f : 0f;
        hoverLerp += (targetHover - hoverLerp) * FastMath.clamp(tpf * 7f, 0f, 1f);

        // Плавный переход press-эффекта
        float targetPress = (pressed || pressTime > 0f) ? 1f : 0f;
        pressedLerp += (targetPress - pressedLerp) * FastMath.clamp(tpf * 18f, 0f, 1f);

        // Гасим pressTime
        if (pressTime > 0f) {
            pressTime -= tpf;
            if (pressTime < 0f) pressTime = 0f;
        }

        // Пульсация свечения
        highlightPulse += tpf * 2.2f;
        float glowPulse = 0.5f + 0.5f * FastMath.sin(highlightPulse);

        // Размер: базовый + hover + press
        float baseScale = 1f;
        float hoverScale = 0.06f * hoverLerp;
        float pressScale = 0.13f * pressedLerp * (pressTime > 0f ? FastMath.exp(-4f * (PRESS_ANIM_DURATION - pressTime)) : 1f);
        float scale = baseScale + hoverScale + pressScale;

        renderer.getNode().setLocalScale(scale);

        // Свечение и фон
        ColorRGBA baseBg = renderer.getLabelText().getColor();
        ColorRGBA hoverGlow = new ColorRGBA(1f, 1f, 1f, 0.13f + 0.13f * glowPulse * hoverLerp); // белый "ореол"
        ColorRGBA pressGlow = new ColorRGBA(1f, 0.9f, 0.3f, 0.18f * pressedLerp); // желтый ореол как в GTA
        ColorRGBA finalGlow = baseBg.clone();

        // Миксуем цвета: базовый -> hover -> press
        finalGlow.interpolateLocal(hoverGlow, hoverLerp);
        finalGlow.interpolateLocal(pressGlow, pressedLerp);

        renderer.setBackgroundColor(finalGlow);

        // Текст: делаем чуть светлее при наведении и нажатии
        ColorRGBA baseText = renderer.getLabelText().getColor();
        ColorRGBA hoverText = baseText.clone().interpolateLocal(ColorRGBA.White, hoverLerp * 0.5f);
        ColorRGBA pressText = hoverText.clone().interpolateLocal(new ColorRGBA(1f, 0.94f, 0.7f, 1f), pressedLerp * 0.85f);
        renderer.setTextColor(pressText);
    }
}