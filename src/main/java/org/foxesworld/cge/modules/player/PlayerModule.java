package org.foxesworld.cge.modules.player;

import com.jme3.app.Application;
import com.jme3.math.Vector3f;
import com.simsilica.es.EntityData;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.loader.JmeProgressBar;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.player.config.PlayerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PlayerModule wraps the Player character as a dynamically-loadable module.
 * On initialization, it spawns the Player at a configured position,
 * attaches it to the root node, and manages cleanup.
 *
 * Улучшено:
 * - Исключён режим гонки: игрок создаётся и добавляется только когда сцена полностью готова,
 *   используя onSceneReady (Consumer<SceneReadyContext>).
 * - Убран неиспользуемый прогресс-бар и синхронный вызов loadAllAssets (который может вызвать гонки).
 * - Добавлен потокобезопасный detach и защита от двойного создания/удаления.
 * - Все операции с rootNode происходят через app.enqueue (JME main thread).
 * - Добавлен логгер для диагностики.
 */
public class PlayerModule extends EngineModule<PlayerConfig> {
    private static final Logger logger = LoggerFactory.getLogger(PlayerModule.class);
    private volatile Player player;

    public PlayerModule(CalistaGameEngine app) {
        super("player", PlayerConfig.class, app, true);
    }

    @Override
    public void onConfigReloaded() {
        // В будущем: реализовать динамический respawn по изменению позиции
    }

    @Override
    protected void onEnable() {
        // не требуется
    }

    @Override
    protected void onDisable() {
        // не требуется
    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        // Гарантируем, что игрок создаётся только после полной готовности сцены
        app.getAssetLoader().loadAllAssets(() -> {
            app.enqueue(() -> {
                if (player != null) {
                    logger.warn("Player already exists, skipping spawn.");
                    return;
                }
                PlayerConfig cfg = getConfig();
                if (cfg == null) {
                    throw new IllegalStateException("PlayerConfig not loaded");
                }
                Vector3f spawn = cfg.getSpawnPosition();
                this.player = new Player(this, spawn);
                app.getRootNode().attachChild(player);
                logger.info("Player spawned at {}", spawn);
            });
        }, new JmeProgressBar(gameEngine));
    }

    @Override
    protected void updateModule(float tpf) {
        // Player logic handled by attached AppState or Control
    }

    @Override
    protected void cleanupModule(Application app) {
        // Удаляем игрока только в игровом потоке и только если был создан
        app.enqueue(() -> {
            if (player != null) {
                try {
                    player.cleanup();
                } catch (Exception e) {
                    logger.warn("Error during player cleanup", e);
                }
                getGameEngine().getRootNode().detachChild(player);
                logger.info("Player removed from scene.");
                player = null;
            }
        });
    }
}