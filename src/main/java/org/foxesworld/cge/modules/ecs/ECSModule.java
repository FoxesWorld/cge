// Файл: src/main/java/org/foxesworld/cge/modules/ecs/ECSModule.java
package org.foxesworld.cge.modules.ecs;

import com.jme3.app.Application;
import com.simsilica.es.*;
import com.simsilica.es.base.DefaultEntityData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * ECSModule (Entity-Component-System Module) - Финальная версия
 *
 * Интегрирует Zay-ES и предоставляет полноценный каркас для работы с ECS.
 * Управляет жизненным циклом EntityData и всех зарегистрированных систем,
 * реализующих интерфейс EntitySystem.
 */
public class ECSModule extends EngineModule<ECSConfig> {

    private static final Logger logger = LogManager.getLogger(ECSModule.class);

    private DefaultEntityData entityData;
    // Используем CopyOnWriteArrayList для потокобезопасной итерации в update и модификации в других потоках.
    private final List<EntitySystem> systems = new CopyOnWriteArrayList<>();

    public ECSModule(CalistaGameEngine app) {
        super(ECSModule.class, ECSConfig.class, app, false);
    }

    // --- Методы жизненного цикла модуля ---

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        if (getConfig() == null) {
            throw new IllegalStateException("ECSConfig not loaded. Cannot initialize ECSModule.");
        }

        this.entityData = new DefaultEntityData();
        logger.info("Zay-ES EntityData initialized.");

        // Инициализируем и запускаем все системы, добавленные до этого момента.
        logger.info("Initializing {} registered systems...", systems.size());
        for (EntitySystem system : systems) {
            try {
                logger.debug("Initializing system '{}'...", system.getClass().getSimpleName());
                system.initialize(entityData);
                system.start();
                logger.info("System '{}' started successfully.", system.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("Failed to initialize and start system: {}", system.getClass().getSimpleName(), e);
                // Если система не смогла запуститься, удаляем ее, чтобы она не вызывала проблем в update.
                systems.remove(system);
            }
        }
    }

    @Override
    protected void updateModule(float tpf) {
        // Обновляем все активные системы.
        for (EntitySystem system : systems) {
            system.update(tpf);
        }
    }

    @Override
    protected void cleanupModule(Application app) {
        logger.info("Cleaning up ECSModule...");

        logger.debug("Stopping {} systems...", systems.size());
        for (EntitySystem system : systems) {
            try {
                system.stop();
            } catch (Exception e) {
                logger.error("Error stopping system: {}", system.getClass().getSimpleName(), e);
            }
        }
        systems.clear();

        if (entityData != null) {
            entityData.close();
            entityData = null;
            logger.info("EntityData closed.");
        }
        logger.info("ECSModule cleaned up.");
    }

    // --- API для управления Системами ---

    /**
     * Регистрирует систему для управления модулем.
     * Система будет инициализирована и запущена при старте модуля.
     * @param system Экземпляр системы для добавления. Не может быть null.
     */
    public void addSystem(EntitySystem system) {
        Objects.requireNonNull(system, "System cannot be null.");

        // Добавляем систему в список в любом случае
        systems.add(system);
        logger.info("System registered: {}", system.getClass().getSimpleName());

        // >>> ГЛАВНОЕ ИЗМЕНЕНИЕ <<<
        // Если модуль УЖЕ работает, то новую систему нужно инициализировать и запустить сразу же.
        if (isInitialized() && entityData != null) {
            try {
                logger.debug("Initializing and starting system on-the-fly: {}", system.getClass().getSimpleName());
                system.initialize(entityData);
                system.start();
                logger.info("System '{}' started successfully on-the-fly.", system.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("Failed to initialize and start on-the-fly system: {}", system.getClass().getSimpleName(), e);
                // Если система не смогла запуститься, немедленно удаляем ее,
                // чтобы она не вызвала NPE при выключении.
                systems.remove(system);
            }
        }
    }

    /**
     * Удаляет систему из модуля. Если модуль активен, у системы будет вызван метод stop().
     * @param system Экземпляр системы для удаления. Не может быть null.
     */
    public void removeSystem(EntitySystem system) {
        Objects.requireNonNull(system, "System cannot be null.");
        if (systems.remove(system)) {
            if (isInitialized()) {
                system.stop();
            }
            logger.info("System removed: {}", system.getClass().getSimpleName());
        } else {
            logger.warn("Attempted to remove a system that was not registered: {}", system.getClass().getSimpleName());
        }
    }

    // --- API для управления Сущностями и Компонентами ---

    /**
     * Возвращает основной объект для работы с данными сущностей.
     * @return EntityData instance, или null, если модуль не инициализирован.
     */
    public EntityData getEntityData() {
        if (!isInitialized()) {
            logger.warn("Attempted to get EntityData before ECSModule is initialized.");
        }
        return entityData;
    }

    /**
     * Создает новую сущность с указанными компонентами.
     * @param components Компоненты для новой сущности. Null-компоненты будут проигнорированы.
     * @return ID созданной сущности (EntityId) или null, если модуль не инициализирован.
     */
    public EntityId createEntity(EntityComponent... components) {
        if (!isInitialized()) {
            logger.warn("Cannot create entity: ECSModule is not initialized.");
            return null;
        }

        EntityId id = entityData.createEntity();
        for (EntityComponent component : components) {
            if (component != null) {
                entityData.setComponent(id, component);
            }
        }

        if (logger.isDebugEnabled()) {
            String componentNames = Arrays.stream(components)
                    .filter(Objects::nonNull)
                    .map(c -> c.getClass().getSimpleName())
                    .collect(Collectors.joining(", "));
            logger.debug("Entity created [id={}, components=[{}]]", id, componentNames);
        }
        return id;
    }

    /**
     * Удаляет сущность и все ее компоненты.
     * @param entityId ID сущности для удаления. Не может быть null.
     */
    public void removeEntity(EntityId entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        if (!isInitialized()) {
            logger.warn("Cannot remove entity {}: ECSModule is not initialized.", entityId);
            return;
        }
        entityData.removeEntity(entityId);
        logger.debug("Entity removed [id={}]", entityId);
    }

    /**
     * Добавляет или обновляет компонент для существующей сущности.
     * @param entityId  ID сущности. Не может быть null.
     * @param component Компонент для установки. Не может быть null.
     */
    public void setComponent(EntityId entityId, EntityComponent component) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(component, "component cannot be null");

        if (!isInitialized()) {
            logger.warn("Cannot set component for entity {}: ECSModule is not initialized.", entityId);
            return;
        }
        entityData.setComponent(entityId, component);
        logger.debug("Component {} set for entity {}", component.getClass().getSimpleName(), entityId);
    }

    /**
     * Удаляет компонент указанного типа у сущности.
     * @param entityId      ID сущности. Не может быть null.
     * @param componentType Тип компонента для удаления. Не может быть null.
     */
    public void removeComponent(EntityId entityId, Class<? extends EntityComponent> componentType) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(componentType, "componentType cannot be null");

        if (!isInitialized()) {
            logger.warn("Cannot remove component from entity {}: ECSModule is not initialized!", entityId);
            return;
        }
        if (entityData.removeComponent(entityId, componentType)) {
            logger.debug("Component {} removed from entity {}", componentType.getSimpleName(), entityId);
        }
    }

    /**
     * Возвращает {@link EntitySet} для отслеживания сущностей с определенным набором компонентов.
     * @param types Типы компонентов для фильтрации.
     * @return EntitySet, или null, если модуль не инициализирован.
     */
    @SafeVarargs
    public final EntitySet getEntities(Class<? extends EntityComponent>... types) {
        if (!isInitialized()) {
            logger.warn("Cannot get EntitySet: ECSModule is not initialized.");
            return null;
        }
        logger.debug("Providing EntitySet for components: {}", Arrays.toString(types));
        return entityData.getEntities(types);
    }

    // --- Служебные методы жизненного цикла модуля ---

    @Override
    protected void onEnable() {
        logger.info("ECSModule enabled.");
    }

    @Override
    protected void onDisable() {
        logger.info("ECSModule disabled.");
    }

    @Override
    public void onConfigReloaded() {
        logger.info("ECS config reloaded. Note: No runtime changes are applied from config yet.");
    }
}