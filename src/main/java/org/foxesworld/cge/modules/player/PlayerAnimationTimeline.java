package org.foxesworld.cge.modules.player;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Timeline for sequencing animations with blend and event support.
 */
public class PlayerAnimationTimeline {
    private static final Logger logger = LoggerFactory.getLogger(PlayerAnimationTimeline.class);

    private final PlayerAnimationController controller;
    private final Queue<TimelineEntry> timeline = new LinkedList<>();
    private TimelineEntry currentEntry = null;
    private float elapsed = 0f;
    private boolean playing = false;

    public static class TimelineEntry {
        public final String animName;
        public final float blendTime;
        public final float duration;
        public final String layer;
        public final boolean loop;
        public final Runnable onStart, onEnd;

        public TimelineEntry(String animName, float blendTime, float duration, String layer, boolean loop, Runnable onStart, Runnable onEnd) {
            this.animName = animName;
            this.blendTime = blendTime;
            this.duration = duration;
            this.layer = layer;
            this.loop = loop;
            this.onStart = onStart;
            this.onEnd = onEnd;
        }
    }

    public PlayerAnimationTimeline(PlayerAnimationController controller) {
        this.controller = controller;
    }

    public void add(TimelineEntry entry) {
        timeline.add(entry);
    }

    public void play() {
        if (!playing) {
            logger.info("Starting animation timeline.");
            playing = true;
            elapsed = 0f;
            next();
        }
    }

    public void update(float tpf) {
        if (!playing || currentEntry == null) return;
        elapsed += tpf;
        if (elapsed >= currentEntry.duration) {
            if (currentEntry.onEnd != null) currentEntry.onEnd.run();
            next();
        }
    }

    private void next() {
        elapsed = 0f;
        currentEntry = timeline.poll();
        if (currentEntry == null) {
            playing = false;
            logger.info("Timeline finished.");
            return;
        }
        controller.setAnimation(currentEntry.animName, currentEntry.blendTime, currentEntry.layer, currentEntry.loop);
        if (currentEntry.onStart != null) currentEntry.onStart.run();
    }
}