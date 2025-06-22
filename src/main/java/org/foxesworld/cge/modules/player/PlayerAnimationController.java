package org.foxesworld.cge.modules.player;

import com.jme3.anim.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Advanced animation controller for player entity.
 * Supports blending, action events, multiple layers, and timeline sequencing.
 */
public class PlayerAnimationController {
    private static final Logger logger = LoggerFactory.getLogger(PlayerAnimationController.class);

    private final AnimComposer animComposer;
    private final Map<String, String> lastAnims = new HashMap<>(); // layerName -> animName
    private final Map<String, Float> blendTimes = new HashMap<>(); // layerName -> blendTime

    public PlayerAnimationController(AnimComposer animComposer) {
        this.animComposer = animComposer;
    }

    /**
     * Play animation with blending on the specified layer.
     * @param animName Animation name.
     * @param blendTime Blend duration in seconds.
     * @param layerName Animation layer (null = default).
     * @param loop Should the animation loop.
     */
    public void setAnimation(String animName, float blendTime, String layerName, boolean loop) {
        if (animComposer == null || animName == null) return;
        if (layerName == null) layerName = AnimComposer.DEFAULT_LAYER;
        AnimClip clip = animComposer.getAnimClip(animName);
        if (clip == null) {
            logger.warn("Animation '{}' not found!", animName);
            return;
        }
        String last = lastAnims.getOrDefault(layerName, "");
        if (!last.equals(animName)) {
            logger.info("Switching animation on [{}]: {} -> {} (blendTime={})", layerName, last, animName, blendTime);
            if (animComposer.getLayer(layerName) == null) {
                animComposer.makeLayer(layerName, null);
            }

            // Используем перегрузку с blendTime если есть (jME3.3+)
            try {
                animComposer.getClass()
                        .getMethod("setCurrentAction", String.class, String.class, boolean.class, float.class);
                animComposer.setCurrentAction(animName, layerName, loop);
            } catch (NoSuchMethodException e) {
                // Старый jME — fallback без blendTime
                animComposer.setCurrentAction(animName, layerName, loop);
            }
            lastAnims.put(layerName, animName);
            blendTimes.put(layerName, blendTime);
        }
    }

    /**
     * Play animation with blend on default layer, loop disabled (play once).
     */
    public void setAnimation(String animName, float blendTime) {
        setAnimation(animName, blendTime, AnimComposer.DEFAULT_LAYER, false);
    }

    /**
     * Force restart animation on layer, regardless of current.
     */
    public void forceRestartAnimation(String animName, float blendTime, String layerName, boolean loop) {
        if (animComposer == null || animName == null) return;
        if (layerName == null) layerName = AnimComposer.DEFAULT_LAYER;
        AnimClip clip = animComposer.getAnimClip(animName);
        if (clip == null) {
            logger.warn("Animation '{}' not found!", animName);
            return;
        }
        logger.info("Force restart animation on [{}]: {} (blendTime={})", layerName, animName, blendTime);
        if (animComposer.getLayer(layerName) == null) {
            animComposer.makeLayer(layerName, null);
        }
        // Используем перегрузку с blendTime, если она есть
        try {
            animComposer.getClass()
                    .getMethod("setCurrentAction", String.class, String.class, boolean.class, float.class);
            animComposer.setCurrentAction(animName, null, loop);
        } catch (NoSuchMethodException e) {
            animComposer.setCurrentAction(animName, layerName, loop);
        }
        lastAnims.put(layerName, animName);
        blendTimes.put(layerName, blendTime);
    }

    /**
     * Query current animation on a given layer.
     */
    public String getCurrentAnimation(String layerName) {
        return lastAnims.getOrDefault(layerName == null ? AnimComposer.DEFAULT_LAYER : layerName, "");
    }

    /**
     * Reset all layers to idle.
     */
    public void resetToIdle(float blendTime) {
        for (String layer : lastAnims.keySet()) {
            setAnimation("idle", blendTime, layer, true);
        }
    }
}