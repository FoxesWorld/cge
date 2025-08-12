package org.foxesworld.cge.modules.player;

import com.jme3.anim.*;
import com.jme3.anim.tween.action.BaseAction;
import com.jme3.anim.tween.*;
import com.jme3.anim.tween.action.LinearBlendSpace;
import com.jme3.math.FastMath;
import org.foxesworld.cge.modules.player.config.AnimationMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * AAA-quality advanced animation controller.
 * <ul>
 *   <li>1D BlendSpace for locomotion</li>
 *   <li>Cross-fade transitions with reflection-based blend support</li>
 *   <li>Queued Event-based sequencing</li>
 *   <li>Layered animations (base, upper, custom layers)</li>
 *   <li>Extensible via listeners and configurable states</li>
 * </ul>
 */
public class PlayerAnimationController {
    private static final Logger logger = LoggerFactory.getLogger(PlayerAnimationController.class);
    private static final String DEFAULT_LAYER = AnimComposer.DEFAULT_LAYER;
    private static final int SPEED_SMOOTH_SAMPLES = 5;

    private final AnimComposer composer;
    private final Map<String, AnimationState> stateByLayer = new HashMap<>();
    private final Map<String, LinearBlendSpace> blendSpaces = new HashMap<>();
    private final Deque<Float> speedHistory = new ArrayDeque<>();
    private final Queue<AnimationTask> taskQueue = new ConcurrentLinkedQueue<>();
    private Method blendedActionMethod;
    private AnimationMapping animationMapping;

    public PlayerAnimationController(AnimComposer composer, AnimationMapping animationMapping) {
        this.animationMapping = animationMapping;
        this.composer = Objects.requireNonNull(composer, "AnimComposer cannot be null");
        initReflection();
        ensureLayer(DEFAULT_LAYER);
    }

    private void initReflection() {
        try {
            blendedActionMethod = composer.getClass()
                    .getMethod("setCurrentAction", String.class, String.class, boolean.class, float.class);
        } catch (NoSuchMethodException e) {
            blendedActionMethod = null;
        }
    }

    /**
     * Call each update to process queued tasks and blend-spaces.
     */
    public void update(float tpf) {
        // process queued tasks
        AnimationTask task;
        while ((task = taskQueue.poll()) != null) {
            task.execute(this);
        }
    }

    /**
     * Smooths raw speed input.
     */
    private float smoothSpeed(float raw) {
        if (speedHistory.size() >= SPEED_SMOOTH_SAMPLES) speedHistory.poll();
        speedHistory.offer(raw);
        return (float) speedHistory.stream().mapToDouble(Float::doubleValue).average().orElse(raw);
    }

    /**
     * Blend locomotion state: 0=idling, [0-1]=mix walk/run, 1=full run.
     */
    public void setLocomotion(float rawSpeedNorm) {
        float norm = FastMath.clamp(rawSpeedNorm, 0f, 1f);
        norm = smoothSpeed(norm);
        String layer = DEFAULT_LAYER;

        LinearBlendSpace bs = blendSpaces.computeIfAbsent(layer, l -> {
            ensureLayer(l);
            LinearBlendSpace space = new LinearBlendSpace(0f, 1f);
            composer.actionBlended(l + ":locomotion", space, "walk", "run");
            composer.setCurrentAction(l + ":locomotion", l);
            return space;
        });

        if (Math.abs(bs.getWeight() - norm) > 1e-3f) {
            bs.setValue(norm);
            stateByLayer.put(layer, new AnimationState("locomotion", 0f, true));
            logger.debug("Locomotion blend α={}", norm);
        }
    }

    /**
     * Play or blend to target animation.
     */
    public void play(String name, float blendTime, String layer, boolean loop) {
        String targetLayer = layer != null ? layer : DEFAULT_LAYER;
        //AnimClip clip = composer.getAnimClip(name);
        name = animationMapping.get(name);
        float length = getClipLength(name);

        AnimationState prev = stateByLayer.get(targetLayer);
        if (prev != null && prev.name.equals(name) && blendTime > 0f) {
            return;
        }
        ensureLayer(targetLayer);
        if (blendTime > 0f && blendedActionMethod != null) {
            invokeBlended(name, targetLayer, loop, blendTime);
        } else {
            composer.setCurrentAction(name, targetLayer, loop);
        }
        stateByLayer.put(targetLayer, new AnimationState(name, blendTime, loop));
        logger.info("Play '{}' on [{}] (blend={}, loop={})", name, targetLayer, blendTime, loop);
    }

    // Утилита: получить длину клипа (в секундах)
    private float getClipLength(String animName) {
        if (composer == null) return 0f;
        try {
            // AnimComposer -> AnimClip (в JME API есть composer.getAnimClip(name))
            com.jme3.anim.AnimClip clip = composer.getAnimClip(animName);
            if (clip != null) return (float) clip.getLength();
        } catch (RuntimeException ignored) { }
        return 0f;
    }

    /**
     * Schedule an animation to play with callback event.
     */
    public void playWithEvent(String name, float blendTime, String layer, boolean loop,
                              Object target, String method, float at) {
        taskQueue.add(new AnimationTask(name, blendTime, layer, loop, target, method, at));
    }

    /**
     * Immediately force play, ignoring last state.
     */
    public void forcePlay(String name, float blendTime, String layer, boolean loop) {
        layer = ensureLayer(layer);
        name = animationMapping.get(name);
        if (blendTime > 0f && blendedActionMethod != null) {
            invokeBlended(name, layer, loop, blendTime);
        } else {
            composer.setCurrentAction(name, layer, loop);
        }
        stateByLayer.put(layer, new AnimationState(name, blendTime, loop));
        logger.info("Force play '{}' on [{}]", name, layer);
    }

    public String getCurrent(String layer) {
        AnimationState s = stateByLayer.get(layer != null ? layer : DEFAULT_LAYER);
        return s != null ? s.name : "";
    }

    private void invokeBlended(String name, String layer, boolean loop, float blend) {
        try {
            blendedActionMethod.invoke(composer, name, layer, loop, blend);
        } catch (Exception e) {
            composer.setCurrentAction(name, layer, loop);
        }
    }

    private String ensureLayer(String layer) {
        if (layer == null) layer = DEFAULT_LAYER;
        if (composer.getLayer(layer) == null) {
            composer.makeLayer(layer, null);
        }
        return layer;
    }

    private static class AnimationState {
        final String name;
        final float blend;
        final boolean loop;
        AnimationState(String name, float blend, boolean loop) {
            this.name = name;
            this.blend = blend;
            this.loop = loop;
        }
    }

    private static class AnimationTask {
        final String name;
        final float blend;
        final String layer;
        final boolean loop;
        final Object target;
        final String method;
        final float delay;
        AnimationTask(String name, float blend, String layer, boolean loop,
                      Object target, String method, float delay) {
            this.name   = name;
            this.blend  = blend;
            this.layer  = layer;
            this.loop   = loop;
            this.target = target;
            this.method = method;
            this.delay  = delay;
        }
        void execute(PlayerAnimationController ctrl) {
            // schedule base play
            ctrl.play(name, blend, layer, loop);
            // schedule callback
            Tween seq = Tweens.sequence(
                    Tweens.delay(delay),
                    Tweens.callMethod(target, method)
            );
            BaseAction ba = new BaseAction(Tweens.smoothStep(seq));
            String key = (layer != null ? layer : DEFAULT_LAYER) + ":evt:" + name;
            ctrl.composer.addAction(key, ba);
            ctrl.composer.setCurrentAction(key, layer, loop);
            ctrl.logger.info("Event '{}' scheduled at {}s on [{}]", name, delay, layer);
        }
    }
}