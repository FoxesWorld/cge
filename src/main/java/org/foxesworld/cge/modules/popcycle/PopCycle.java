package org.foxesworld.cge.modules.popcycle;

import org.foxesworld.cge.CalistaGameEngine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * PopCycle — универсальный менеджер событий.
 * <p>
 * Поддерживает регистрацию/снятие подписчиков (EventListener) на конкретные типы событий (GameEvent),
 * а также безопасную публикацию (post) событий из разных потоков.
 */
public class PopCycle {
    /**
     * Для каждого класса события храним потокобезопасный список слушателей.
     */
    private final Map<Class<? extends GameEvent>, CopyOnWriteArrayList<EventListener>> listeners =
            new ConcurrentHashMap<>();

    private final  CalistaGameEngine calistaGameEngine;
    public PopCycle(CalistaGameEngine calistaGameEngine){
        this.calistaGameEngine = calistaGameEngine;
    }
    /**
     * Регистрирует нового слушателя на конкретный тип события.
     * Слушатель будет вставлен с учётом getPriority(), таким образом весь список остаётся отсортированным.
     *
     * @param eventType класс события, на которое подписывается listener
     * @param listener  объект, реализующий EventListener
     */
    public void register(Class<? extends GameEvent> eventType, EventListener listener) {
        CopyOnWriteArrayList<EventListener> subs =
                listeners.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>());

        // Вставляем listener в уже отсортированный список по приоритету (больший приоритет — ближе к началу)
        int insertIndex = binarySearchInsertIndex(subs, listener.getPriority());
        subs.add(insertIndex, listener);
    }

    /**
     * Удаляет ранее зарегистрированного слушателя для данного типа события.
     *
     * @param eventType класс события, с которого отписываем listener
     * @param listener  ранее зарегистрированный объект EventListener
     * @return true, если listener был найден и удалён, false — если не найден
     */
    public boolean unregister(Class<? extends GameEvent> eventType, EventListener listener) {
        CopyOnWriteArrayList<EventListener> subs = listeners.get(eventType);
        if (subs != null) {
            return subs.remove(listener);
        }
        return false;
    }

    /**
     * Публикует (рассылает) событие всем подписчикам данного типа.
     * <p>
     * Если нужно учитывать подписчиков по иерархии (родительские классы), можно дополнить эту логику,
     * обходя класс-цепочку event.getClass().getSuperclass(), и проверяя listeners.get(superType).
     *
     * @param event экземпляр события
     */
    public void post(GameEvent event) {
        Class<? extends GameEvent> type = event.getClass().asSubclass(GameEvent.class);
        // Берём список подписчиков (CopyOnWriteArrayList позволяет безопасно перебирать, даже если где-то другой поток регистрирует/удаляет).
        List<EventListener> subs = listeners.getOrDefault(type, new CopyOnWriteArrayList<>());
        for (EventListener listener : subs) {
            listener.onEvent(event);
        }

        /*
        // Если нужна поддержка подписки по иерархии (например, подписчики на GameEvent.class получат все события ниже):
        Class<?> parent = type.getSuperclass();
        while (parent != null && GameEvent.class.isAssignableFrom(parent)) {
            List<EventListener> parentSubs = listeners.get(parent);
            if (parentSubs != null) {
                for (EventListener listener : parentSubs) {
                    listener.onEvent(event);
                }
            }
            parent = parent.getSuperclass();
        }
        */
    }

    /**
     * Выполняет бинарный поиск места для вставки нового слушателя с указанным приоритетом.
     * Список subs при этом считается уже отсортированным по убыванию getPriority().
     *
     * @param subs     текущее отсортированное (по убыванию приоритета) CopyOnWriteArrayList
     * @param priority приоритет нового слушателя
     * @return индекс, куда можно добавить нового слушателя, чтобы сохранить порядок
     */
    private int binarySearchInsertIndex(List<EventListener> subs, int priority) {
        int low = 0;
        int high = subs.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midPrio = subs.get(mid).getPriority();
            if (midPrio == priority) {
                // Вставляем перед первым таким же приоритетом, чтобы сохранить порядок регистрации между ними
                return mid;
            } else if (midPrio < priority) {
                // Если приоритет в середине меньше, значит новый слушатель выше по приоритету → идём в левую часть
                high = mid - 1;
            } else {
                // Новый слушатель ниже по приоритету → идём вправо
                low = mid + 1;
            }
        }
        // Если не нашли точного совпадения, low указывает на позицию вставки
        return low;
    }
}
