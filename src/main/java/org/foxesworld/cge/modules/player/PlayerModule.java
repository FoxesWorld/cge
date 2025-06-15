package org.foxesworld.cge.modules.player;

import com.jme3.app.Application;
import com.jme3.app.state.AppState;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import com.jme3.math.Vector3f;

/**
 * PlayerModule wraps the Player character as a dynamically-loadable module.
 * <p>
 * On initialization, it spawns the Player at a configured position,
 * registers update callbacks, and handles cleanup on disable.
 * </p>
 */
public class PlayerModule extends EngineModule<PlayerConfig> {
    private Player player;

    /**
     * Constructs the PlayerModule.
     *
     * @param app the main game engine instance
     */
    public PlayerModule(CalistaGameEngine app) {
        super("player", PlayerConfig.class, app);
    }

    /**
     * Called when configuration is reloaded.
     */
    @Override
    protected void onConfigReloaded() {
        // no dynamic reload logic for player
    }

    /**
     * Called when the module is enabled (after initModule completes).
     */
    @Override
    protected void onEnable() {
        // nothing special on enable
    }

    @Override
    protected void onDisable() {

    }

    /**
     * Initializes the Player module: reads spawn position from config,
     * creates the Player, attaches to root node, and schedules the update AppState.
     *
     * @param app the engine instance
     * @throws Exception if config or creation fails
     */
    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        app.getAssetLoader().loadAllAssets(() -> {
            PlayerConfig cfg = getConfig();
            if (cfg == null) {
                throw new IllegalStateException("PlayerConfig not loaded");
            }
            Vector3f spawn = cfg.getSpawnPosition();
            this.player = new Player(app, spawn);
            app.getRootNode().attachChild(player);

        });
    }

    /**
     * Called each frame; player updates handled by attached AppState.
     *
     * @param tpf time per frame
     */
    @Override
    protected void updateModule(float tpf) {
        // no-op: handled by AppState
    }

    /**
     * Cleans up the Player module: removes player and restores input/camera.
     *
     * @param app the engine instance
     */
    @Override
    protected void cleanupModule(Application app) {
        if (player != null) {
            player.cleanup();
            getGameEngine().getRootNode().detachChild(player);
            player = null;
        }
    }
}
