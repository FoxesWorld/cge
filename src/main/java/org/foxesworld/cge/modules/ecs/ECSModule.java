package org.foxesworld.cge.modules.ecs;

import com.jme3.app.Application;
import com.simsilica.es.*;
import com.simsilica.es.base.DefaultEntityData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.core.module.ModuleManager;

import java.util.Arrays;

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

        this.entityData = new DefaultEntityData();

        logger.info("Zay-ES 1.6.0 initialized with ECS core setup");
    }

    @Override
    protected void updateModule(float tpf) {
        // Future: update systems here
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
        if (isInitialized()) {
            EntityId id = entityData.createEntity();
            for (EntityComponent comp : components) {
                if (comp != null) {
                    entityData.setComponent(id, comp);
                } else {
                    logger.warn("Attempted to add null component to entity {}", id);
                }
            }
            logger.debug("Entity created with ID: {}", id);
            return id;
        }
        logger.warn("Cannot add Entity to system, ECS is not initialized!");
        return null;
    }

    /**
     * Adds or updates a component for an existing entity.
     *
     * @param entityId Entity ID.
     * @param component Component to set.
     */
    public void setComponent(EntityId entityId, EntityComponent component) {
        if (!isInitialized()) {
            logger.warn("Cannot set component for entity {}, ECS is not initialized!", entityId);
            return;
        }
        if (entityId == null || component == null) {
            logger.warn("EntityId or Component is null (entityId={}, component={}), skipping setComponent", entityId, component);
            return;
        }
        entityData.setComponent(entityId, component);
        logger.debug("Component {} set for entity {}", component.getClass().getSimpleName(), entityId);
    }

    /**
     * Removes a component from an entity.
     *
     * @param entityId Entity ID.
     * @param componentType Type of component to remove.
     */
    public void removeComponent(EntityId entityId, Class<? extends EntityComponent> componentType) {
        if (!isInitialized()) {
            logger.warn("Cannot remove component from entity {}, ECS is not initialized!", entityId);
            return;
        }
        if (entityId == null || componentType == null) {
            logger.warn("EntityId or componentType is null (entityId={}, componentType={}), skipping removeComponent", entityId, componentType);
            return;
        }
        entityData.removeComponent(entityId, componentType);
        logger.debug("Component {} removed from entity {}", componentType.getSimpleName(), entityId);
    }

    /**
     * Returns an EntitySet for the given component types.
     *
     * @param types Component classes to watch.
     * @return EntitySet, or null if ECS not initialized.
     */
    @SafeVarargs
    public final EntitySet addEntitySet(Class<? extends EntityComponent>... types) {
        if (isInitialized()) {
            EntitySet set = entityData.getEntities(types);
            logger.debug("EntitySet created for components: {}", Arrays.toString(types));
            return set;
        }
        logger.warn("Cannot create EntitySet, ECS is not initialized!");
        return null;
    }
}
