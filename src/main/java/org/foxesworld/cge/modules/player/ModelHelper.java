package org.foxesworld.cge.modules.player;

import com.google.gson.Gson;
import com.jme3.anim.AnimComposer;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import org.foxesworld.cge.modules.player.config.AnimationMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

class ModelHelper {
    private static final Logger logger = LoggerFactory.getLogger(ModelHelper.class);
    private final Player owner;
    private final PlayerModule module;
    private Spatial playerModel;
    private AnimComposer animComposer;
    private PlayerAnimationController animationController;

    private final Vector3f reuseVec1 = new Vector3f();
    private final Vector3f reuseVec2 = new Vector3f();

    ModelHelper(Player owner) {
        this.owner = owner;
        this.module = owner.getPlayerModule();
    }

    AnimationMapping loadAnimationMapping(com.jme3.asset.AssetManager assets, String path) {
        if (path == null || path.isEmpty()) {
            logger.warn("Animation mapping path is not defined in player config. Using an empty mapping.");
            return new AnimationMapping();
        }

        try {
            AssetInfo assetInfo = assets.locateAsset(new AssetKey<>(path));
            if (assetInfo == null) throw new AssetNotFoundException("Asset not found via locateAsset: " + path);

            try (InputStream stream = assetInfo.openStream();
                 Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                AnimationMapping animationMapping = new Gson().fromJson(reader, AnimationMapping.class);
                if (animationMapping == null) {
                    logger.warn("Animation mapping file '{}' is empty or invalid. Using an empty mapping.", path);
                    return new AnimationMapping();
                } else {
                    logger.info("Successfully loaded animation mapping from '{}'", path);
                    return animationMapping;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load animation mapping from '{}'. Using an empty mapping.", path, e);
            return new AnimationMapping();
        }
    }

    void loadPlayerModel(String modelPath) {
        try {
            this.playerModel = owner.getEngine().getAssetManager().loadModel(modelPath);
            this.playerModel.setLocalScale(module.getConfig().getModel().getScale());
            this.playerModel.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            this.playerModel.setCullHint(Spatial.CullHint.Never);
            this.playerModel.setLocalTranslation(0, module.getConfig().getModel().getDownOffset(), module.getConfig().getModel().getBackOffset());
            owner.attachChild(playerModel);

            this.animComposer = fetchControl(playerModel, AnimComposer.class);
            if (this.animComposer != null) {
                AnimationMapping mapping = loadAnimationMapping(owner.getEngine().getAssetManager(), module.getConfig().getAnimMappingPath());
                this.animationController = new PlayerAnimationController(this.animComposer, mapping);
                this.animationController.play("idle", 0.15f, null, true);
            } else {
                logger.warn("AnimComposer not found on player model!");
            }
        } catch (Exception e) {
            logger.warn("Failed to load player model '{}': {}", modelPath, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    <T extends Control> T fetchControl(Spatial spatial, Class<T> type) {
        if (spatial == null) return null;
        T control = spatial.getControl(type);
        if (control != null) return control;
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                T childControl = fetchControl(child, type);
                if (childControl != null) return childControl;
            }
        }
        return null;
    }

    void update(float tpf) {
        if (animationController != null) animationController.update(tpf);
    }

    void updateModelPosition() {
        if (playerModel == null) return;
        Vector3f modelPos = reuseVec1.set(owner.character.getPhysicsLocation()).addLocal(0, -module.getConfig().getPhysics().getHeight() / 2f, 0);
        playerModel.setLocalTranslation(modelPos);

        Vector3f lookDir = owner.getCam().getDirection(reuseVec2).normalizeLocal();
        Vector3f lookTarget = modelPos.add(lookDir);
        playerModel.lookAt(lookTarget, Vector3f.UNIT_Y);
    }

    void play(String name, float blend, Object args, boolean loop) {
        if (animationController != null) animationController.play(name, blend, (String) args, loop);
    }

    Spatial getPlayerModel() {
        return playerModel;
    }

    AnimComposer getAnimComposer() {
        return animComposer;
    }

    PlayerAnimationController getAnimationController() {
        return animationController;
    }

    void cleanup() {
        if (playerModel != null && playerModel.getParent() != null) playerModel.removeFromParent();
    }
}