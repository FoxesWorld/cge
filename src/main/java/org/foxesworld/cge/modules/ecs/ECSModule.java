package org.foxesworld.cge.modules.ecs;

import com.jme3.app.Application;
import com.simsilica.es.*;
import com.simsilica.es.base.DefaultEntityData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;

/**
 * ECSModule integrates the Zay-ES 1.6.0 entity system with the Calista engine,
 * providing core ECS setup.
 */
public class ECSModule extends EngineModule<ECSConfig> {

    private static final Logger logger = LogManager.getLogger(ECSModule.class);
    private final ModuleManager subManager;
    private DefaultEntityData entityData;

    public ECSModule(CalistaGameEngine app) {
        super("ecs", ECSConfig.class, app, false);
        this.subManager = new ModuleManager(app);
    }

    @Override
    protected void onEnable() {
        logger.info("ECSModule enabled");
    }

    @Override
    protected void onDisable() {
        logger.info("ECSModule disabled");
    }

    @Override
    protected void onConfigReloaded() {
        logger.info("ECS config reloaded");
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        ECSConfig cfg = getConfig();
        if (cfg == null) {
            throw new IllegalStateException("ECSConfig not loaded");
        }

        // Zay-ES 1.6.0: DefaultEntityData now implements AutoCloseable
        this.entityData = new DefaultEntityData();

        logger.info("Zay-ES 1.6.0 initialized with ECS core setup");
    }

    @Override
    protected void updateModule(float tpf) {
        // Systems can be updated here in future
    }

    @Override
    protected void cleanupModule(Application app) {
        logger.info("Cleaning up ECSModule...");
        if (entityData != null) {
            entityData.close();
            entityData = null;
        }
        logger.info("ECSModule cleaned up");
    }

    public EntityData getEntityData() {
        return entityData;
    }

    /**
     * Registers a new entity with the provided component(s).
     *
     * @param components Components to associate with the new entity.
     * @return The created EntityId, or null if ECS is not initialized.
     */
    public EntityId addEntityComponent(EntityComponent... components) {
        if(isInitialized()) {
        EntityId id = entityData.createEntity();
        for (EntityComponent comp : components) {
            entityData.setComponent(id, comp);
        }
        logger.debug("Entity created with ID: {}", id);
        return id;
        }
        logger.warn("Cannot add Entity to system, ECS is not initialised!");
        return null;
    }

    /**
     * Adds a component to an existing entity.
     *
     * @param entityId Entity ID.
     * @param component Component to set.
     */
    public void setComponent(EntityId entityId, EntityComponent component) {
        if(isInitialized()) {
            entityData.setComponent(entityId, component);
            logger.debug("Component {} set for entity {}", component.getClass().getSimpleName(), entityId);
        } else {
            logger.warn("Cannot set emtityId for - {}, ECS is not initialised!", entityId);
        }
    }

    /**
     * Removes a component from an entity.
     *
     * @param entityId Entity ID.
     * @param componentType Type of component to remove.
     */
    public void removeComponent(EntityId entityId, Class<? extends EntityComponent> componentType) {
        if(isInitialized()) {
            entityData.removeComponent(entityId, componentType);
            logger.debug("Component {} removed from entity {}", componentType.getSimpleName(), entityId);
        } else {
            logger.warn("Can't remove entity {} ECS is not initialised!", entityId);
        }
    }

    /**
     * Returns an EntitySet for the given component types.
     *
     * @param types Component classes to watch.
     * @return EntitySet
     */
    @SafeVarargs
    public final EntitySet addEntitySet(Class<? extends EntityComponent>... types) {
        if(isInitialized()) {
            EntitySet set = entityData.getEntities(types);
            logger.debug("EntitySet created for components: {}", (Object) types);

        return set;
        }
        return null;
    }


}
