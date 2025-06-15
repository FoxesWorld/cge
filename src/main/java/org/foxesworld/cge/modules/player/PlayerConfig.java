package org.foxesworld.cge.modules.player;

import com.jme3.math.Vector3f;
import org.foxesworld.cge.core.module.ModuleConfig;

/**
 * Configuration for PlayerModule. Holds the spawn position.
 */
public class PlayerConfig extends ModuleConfig {
    private Vector3f spawnPosition = new Vector3f(0, 5, 0);

    public Vector3f getSpawnPosition() {
        return spawnPosition;
    }

    public void setSpawnPosition(Vector3f spawnPosition) {
        this.spawnPosition = spawnPosition;
    }
}