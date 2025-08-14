package com.jme3.anim.tween.action;

import com.jme3.anim.util.HasLocalTransform;
import com.jme3.math.FastMath;
import com.jme3.math.Transform;

import java.util.*;
import java.util.stream.DoubleStream;

/**
 * Расширенная версия BlendAction с поддержкой мульти-блендинга
 * и динамическим управлением весами анимаций.
 */
public class MultiBlendAction extends BlendableAction {

    private final BlendSpace blendSpace;
    private final double[] timeFactors;
    private double[] speedFactors;
    private double[] blendWeights;
    private final Map<HasLocalTransform, Transform> targetMap = new HashMap<>();
    private final List<Integer> activeIndices = new ArrayList<>();
    private float transitionWeight = 1.0f;

    public MultiBlendAction(BlendSpace blendSpace, BlendableAction... actions) {
        super(actions);
        this.blendSpace = blendSpace;
        this.timeFactors = new double[actions.length];
        this.blendWeights = new double[actions.length];
        Arrays.fill(blendWeights, 0.0);

        // Инициализация целевых преобразований
        for (BlendableAction action : actions) {
            if (action.getLength() > getLength()) {
                setLength(action.getLength());
            }
            for (HasLocalTransform target : action.getTargets()) {
                targetMap.computeIfAbsent(target, k -> new Transform());
            }
        }

        // Расчет факторов времени
        for (int i = 0; i < actions.length; i++) {
            timeFactors[i] = calculateTimeFactor(actions[i]);
        }
    }

    @Override
    public Collection<HasLocalTransform> getTargets() {
        // Возвращаем все целевые объекты из карты преобразований
        return Collections.unmodifiableSet(targetMap.keySet());
    }

    @Override
    public void doInterpolate(double t) {
        updateActiveIndices();
        resetTargetTransforms();

        // Интерполяция всех активных анимаций
        for (int i = 0; i < actions.length; i++) {
            if (blendWeights[i] <= 0) continue;

            BlendableAction action = (BlendableAction) actions[i];
            action.setCollectTransformDelegate(this);
            action.setWeight((float) blendWeights[i]);
            action.interpolate(t * timeFactors[i]);
            action.setCollectTransformDelegate(null);
        }

        // Применение финальных преобразований
        applyFinalTransforms();
    }

    /**
     * Устанавливает веса для всех анимаций.
     *
     * @param weights массив весов (должен совпадать с количеством анимаций)
     */
    public void setBlendWeights(double... weights) {
        if (weights.length != actions.length) {
            throw new IllegalArgumentException("Weights array length must match actions count");
        }

        double total = DoubleStream.of(weights).sum();
        if (total > 0) {
            for (int i = 0; i < weights.length; i++) {
                blendWeights[i] = weights[i] / total;
            }
        }
    }

    /**
     * Устанавливает вес для конкретной анимации.
     *
     * @param index индекс анимации
     * @param weight значение веса (0.0-1.0)
     */
    public void setActionWeight(int index, double weight) {
        if (index < 0 || index >= actions.length) {
            throw new IndexOutOfBoundsException("Invalid action index");
        }
        blendWeights[index] = FastMath.clamp((float) weight, 0f, 1f);
        normalizeWeights();
    }

    /**
     * Возвращает текущие веса анимаций.
     */
    public double[] getBlendWeights() {
        return blendWeights.clone();
    }

    @Override
    public double getSpeed() {
        if (speedFactors == null) {
            return super.getSpeed();
        }

        double effectiveSpeed = 0.0;
        for (int i = 0; i < actions.length; i++) {
            if (blendWeights[i] > 0) {
                effectiveSpeed += super.getSpeed() * speedFactors[i] * blendWeights[i];
            }
        }
        return effectiveSpeed;
    }

    /**
     * Устанавливает факторы скорости для каждой анимации.
     */
    public void setSpeedFactors(double... factors) {
        if (factors.length != actions.length) {
            throw new IllegalArgumentException("Factors array length must match actions count");
        }
        this.speedFactors = factors;
    }

    /**
     * Устанавливает глобальный вес перехода.
     */
    public void setTransitionWeight(float weight) {
        this.transitionWeight = FastMath.clamp(weight, 0f, 1f);
    }

    @Override
    public void collectTransform(HasLocalTransform target, Transform t, float weight, BlendableAction source) {
        Transform accumulated = targetMap.get(target);
        if (accumulated == null) return;

        if (weight == 1.0f) {
            accumulated.set(t);
        } else {
            accumulated.interpolateTransforms(accumulated, t, weight);
        }
    }

    private double calculateTimeFactor(BlendableAction action) {
        if (action.getLength() <= 0 || getLength() <= 0) return 1.0;
        return action.getLength() / getLength();
    }

    private void updateActiveIndices() {
        activeIndices.clear();
        for (int i = 0; i < blendWeights.length; i++) {
            if (blendWeights[i] > 0.001) {
                activeIndices.add(i);
            }
        }
    }

    private void resetTargetTransforms() {
        for (Transform transform : targetMap.values()) {
            transform.loadIdentity();
        }
    }

    private void applyFinalTransforms() {
        for (Map.Entry<HasLocalTransform, Transform> entry : targetMap.entrySet()) {
            HasLocalTransform target = entry.getKey();
            Transform finalTransform = entry.getValue();

            if (collectTransformDelegate != null) {
                collectTransformDelegate.collectTransform(target, finalTransform, this.getWeight(), this);
            } else {
                applyTransformToTarget(target, finalTransform);
            }
        }
    }

    private void applyTransformToTarget(HasLocalTransform target, Transform finalTransform) {
        if (transitionWeight == 1.0f) {
            target.setLocalTransform(finalTransform);
        } else {
            Transform current = target.getLocalTransform();
            current.interpolateTransforms(current, finalTransform, transitionWeight);
            target.setLocalTransform(current);
        }
    }

    private void normalizeWeights() {
        double totalWeight = DoubleStream.of(blendWeights).sum();
        if (totalWeight <= 0) return;

        for (int i = 0; i < blendWeights.length; i++) {
            blendWeights[i] /= totalWeight;
        }
    }

    // Оптимизация: кешируем индексы для быстрого доступа
    public int[] getActiveActionIndices() {
        return activeIndices.stream().mapToInt(i -> i).toArray();
    }

    public double getActiveActionWeight(int index) {
        return (index >= 0 && index < blendWeights.length) ? blendWeights[index] : 0.0;
    }
}