package org.foxesworld.cge.modules.ui.novaUi.elements.progress;

import com.jme3.math.FastMath;

/**
 * A simple animator that smoothly interpolates a float value towards a target.
 */
public class ProgressAnimator {
    private float currentValue;
    private float targetValue;
    private float speed;

    public ProgressAnimator(float initialValue, float speed) {
        this.currentValue = initialValue;
        this.targetValue = initialValue;
        this.speed = speed;
    }

    /**
     * Updates the current value, moving it towards the target.
     * @param tpf Time per frame.
     * @return true if the value changed, false otherwise.
     */
    public boolean update(float tpf) {
        if (FastMath.abs(currentValue - targetValue) < 0.001f) {
            if (currentValue != targetValue) {
                currentValue = targetValue; // Snap to final value
                return true;
            }
            return false;
        }
        currentValue = FastMath.interpolateLinear(tpf * speed, currentValue, targetValue);
        return true;
    }

    public void setTarget(float newTarget) {
        this.targetValue = newTarget;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public float getCurrentValue() {
        return currentValue;
    }
}