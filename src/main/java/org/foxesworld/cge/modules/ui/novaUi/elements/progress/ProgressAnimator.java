package org.foxesworld.cge.modules.ui.novaUi.elements.progress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ProgressAnimator – отвечает за плавную анимацию перехода
 * от displayedProgress к targetProgress со скоростью animationSpeed.
 *
 * Поведение:
 *  • display – текущее отображаемое значение [0..1]
 *  • target  – желаемое значение [0..1]
 *  • speed   – скорость изменения (в единицах [0..1] в секунду)
 *
 * Во внешнем цикле каждый кадр вызывается update(tpf),
 * чтобы постепенно приближаться к target.
 */
public class ProgressAnimator {
    private static final Logger logger = LoggerFactory.getLogger(ProgressAnimator.class);

    private float displayed;    // отображаемое значение [0..1]
    private float target;       // целевое значение [0..1]
    private float speed = 1f;   // скорость [0..1] за секунду

    public ProgressAnimator(float initial, float speed) {
        this.displayed = clamp(initial, 0f, 1f);
        this.target    = this.displayed;
        this.speed     = Math.max(0f, speed);
    }

    /** Устанавливает новый targetProgress (в диапазоне 0..1). */
    public void setTarget(float t) {
        this.target = clamp(t, 0f, 1f);
    }

    /** Возвращает текущее отображаемое значение. */
    public float getDisplayed() {
        return displayed;
    }

    /** Возвращает текущий target (может отличаться, пока анимация не закончена). */
    public float getTarget() {
        return target;
    }

    /** Устанавливает скорость анимации. */
    public void setSpeed(float speed) {
        this.speed = Math.max(0f, speed);
    }

    /** Возвращает скорость анимации. */
    public float getSpeed() {
        return speed;
    }

    /**
     * Вызывается каждый кадр: постепенно приближает displayed к target
     * со скоростью speed (учитывая переданное tpf).
     *
     * @param tpf Время кадра (в секундах)
     * @return true, если после обновления displayed изменился; false, если уже равен target
     */
    public boolean update(float tpf) {
        if (Math.abs(displayed - target) < 1e-6f) {
            displayed = target;
            return false;
        }
        float delta = target - displayed;
        float maxStep = speed * tpf;
        if (Math.abs(delta) <= maxStep) {
            displayed = target;
        } else {
            displayed += Math.copySign(maxStep, delta);
        }
        return true;
    }

    private float clamp(float v, float min, float max) {
        return (v < min) ? min : Math.min(v, max);
    }
}
