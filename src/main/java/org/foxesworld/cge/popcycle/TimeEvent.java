package org.foxesworld.cge.popcycle;

/**
 * Событие смены времени суток.
 * В качестве источника времени используется дробное значение часа (0.0f – полночь, 12.5f – полдень и т.д.).
 */
public class TimeEvent implements GameEvent {
    /** Текущее «часовое» значение (0–24). */
    private final float hour;

    /**
     * @param hour дробное значение часа (от 0.0 до 24.0)
     */
    public TimeEvent(float hour) {
        this.hour = hour;
    }

    /**
     * @return дробное значение часа (0–24), переданное в конструкторе
     */
    public float getHour() {
        return hour;
    }

    @Override
    public String toString() {
        return "TimeEvent{hour=" + hour + '}';
    }
}
