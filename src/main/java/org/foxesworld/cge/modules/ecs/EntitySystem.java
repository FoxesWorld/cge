// Файл: src/main/java/org/foxesworld/cge/modules/ecs/EntitySystem.java
package org.foxesworld.cge.modules.ecs;

import com.simsilica.es.EntityData;

/**
 * Определяет контракт для системы в ECS.
 * Система инкапсулирует логику, которая оперирует сущностями с определенными компонентами.
 * Имя EntitySystem используется, чтобы избежать конфликта с java.lang.System.
 */
public interface EntitySystem {
    /**
     * Вызывается один раз при добавлении системы в ECSModule и его последующей инициализации.
     * Здесь система должна получить доступ к EntityData и создать необходимые
     * EntitySet для отслеживания сущностей.
     *
     * @param entityData Хранилище данных ECS.
     */
    void initialize(EntityData entityData);

    /**
     * Вызывается после initialize(), когда модуль полностью готов к работе.
     * Здесь можно запустить начальные процессы.
     */
    void start();

    /**
     * Вызывается каждый кадр. Основная логика системы.
     *
     * В этом методе крайне важно использовать паттерн `entitySet.applyChanges()`
     * для обработки жизненного цикла сущностей:
     *
     * - `entitySet.getAddedEntities()`: для логики, которая выполняется при появлении новой сущности.
     * - `entitySet.getChangedEntities()`: для реакции на изменение компонентов.
     * - `entitySet.getRemovedEntities()`: для логики очистки, когда сущность удаляется или перестает
     *   соответствовать фильтру системы.
     *
     * @param tpf Время, прошедшее с последнего кадра (time per frame).
     */
    void update(float tpf);

    /**
     * Вызывается перед удалением системы или при очистке всего ECSModule.
     * Здесь нужно освободить все ресурсы, созданные в initialize(),
     * в первую очередь вызвав `entitySet.release()`.
     */
    void stop();
}