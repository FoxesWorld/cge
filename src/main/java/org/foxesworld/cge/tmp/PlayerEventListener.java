package org.foxesworld.cge.tmp;

public interface PlayerEventListener {
    void onJump(float velocity);
    void onLand(float airTime);
    void onSprintStart();
    void onSprintEnd();
}