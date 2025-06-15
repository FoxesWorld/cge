package org.foxesworld.cge.modules.popcycle;

public interface EventListener {
    /** Метод обработки события */
    void onEvent(GameEvent event);
    /** Приоритет обработки (чем выше, тем раньше вызывается) */
    int getPriority();
}