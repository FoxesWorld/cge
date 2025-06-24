package org.foxesworld.cge.modules.player.animation.event;


import com.jme3.anim.tween.Tween;
import com.jme3.anim.tween.Tweens;

/**
 *
 * @author codex
 */
public interface AnimEventListener {

    public void animationEvent(AnimationEvent event);

    /**
     * Creates an animation Tween that calls the {@code animationEvent}
     * method on this listener.
     * @param event animation event
     * @return tween that calls the animation event method
     */
    public default Tween tween(AnimationEvent event) {
        return Tweens.callMethod(this, "animationEvent", event);
    }

}