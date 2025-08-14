package org.foxesworld.cge.modules.player;

import java.util.*;

/**
 * Многоточечный BlendSpace для линейного смешивания анимаций по одной оси (например, скорость).
 * Поддерживает смешивание из 2, 3 и более анимаций.
 */
public class MultiLinearBlendSpace {

    /**
     * Одна точка в BlendSpace.
     */
    private static class Point {
        final float value;     // Позиция на оси (например, нормализованная скорость)
        final String animName; // Имя анимации

        Point(float value, String animName) {
            this.value = value;
            this.animName = animName;
        }
    }

    private final List<Point> points = new ArrayList<>();

    /**
     * Добавляет точку в BlendSpace.
     * @param value    позиция на оси [0..1]
     * @param animName имя анимации
     */
    public void addPoint(float value, String animName) {
        points.add(new Point(value, animName));
        points.sort(Comparator.comparingDouble(p -> p.value));
    }

    /**
     * Возвращает имена анимаций для заданного значения.
     * Здесь мы подбираем ближайшие точки и отдаём их в порядке близости.
     */
    public String[] getAnimationsForValue(float value) {
        if (points.isEmpty()) {
            throw new IllegalStateException("Нет точек в BlendSpace");
        }
        if (points.size() == 1) {
            return new String[]{points.get(0).animName};
        }

        // Находим ближайшие точки
        List<Point> sortedByDist = new ArrayList<>(points);
        sortedByDist.sort(Comparator.comparingDouble(p -> Math.abs(p.value - value)));

        // Можно вернуть 2, 3 или больше ближайших
        return sortedByDist.stream()
                .limit(3) // например, берём 3 ближайших для blend-а
                .map(p -> p.animName)
                .toArray(String[]::new);
    }

    /**
     * Возвращает имя анимации, которая ближе всего к значению.
     */
    public String getAnimationForValue(float value) {
        if (points.isEmpty()) {
            throw new IllegalStateException("Нет точек в BlendSpace");
        }
        return points.stream()
                .min(Comparator.comparingDouble(p -> Math.abs(p.value - value)))
                .get().animName;
    }

    /**
     * Возвращает веса для каждой анимации (сумма весов = 1).
     * Это пригодится для интерполяции.
     */
    public Map<String, Float> getWeightsForValue(float value) {
        String[] anims = getAnimationsForValue(value);
        Map<String, Float> weights = new LinkedHashMap<>();

        if (anims.length == 1) {
            weights.put(anims[0], 1f);
            return weights;
        }

        // Находим точки для этих анимаций
        List<Point> nearestPoints = new ArrayList<>();
        for (String anim : anims) {
            nearestPoints.add(points.stream()
                    .filter(p -> p.animName.equals(anim))
                    .findFirst()
                    .get());
        }

        // Линейное распределение весов
        float totalDist = 0f;
        for (Point p : nearestPoints) {
            totalDist += 1f / (Math.abs(p.value - value) + 0.0001f);
        }

        for (Point p : nearestPoints) {
            float w = (1f / (Math.abs(p.value - value) + 0.0001f)) / totalDist;
            weights.put(p.animName, w);
        }

        return weights;
    }

    /**
     * Для отладки — печать содержимого blend space.
     */
    public void debugPrint() {
        System.out.println("BlendSpace:");
        for (Point p : points) {
            System.out.printf("  %.2f -> %s%n", p.value, p.animName);
        }
    }
}
