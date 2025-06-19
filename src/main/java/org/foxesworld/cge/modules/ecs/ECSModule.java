package org.foxesworld.cge.modules.ecs;

import com.jme3.app.Application;
import com.simsilica.es.EntityData;;
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
    private static final String CONFIG_FILE = "ecs_config";

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
}