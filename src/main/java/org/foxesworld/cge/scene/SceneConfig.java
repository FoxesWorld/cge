package org.foxesworld.cge.scene;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * Configuration for SceneModule loaded from scene.json.
 */
public class SceneConfig {
    private String scenePath = "test.cgs";
    private Vector3f translation = new Vector3f(0, 0, 0);
    private Quaternion rotation = new Quaternion();
    private Vector3f scale = new Vector3f(1, 1, 1);

    public SceneConfig() {}

    public String getScenePath() {
        return scenePath;
    }

    public void setScenePath(String scenePath) {
        this.scenePath = scenePath;
    }

    public Vector3f getTranslation() {
        return translation;
    }

    public void setTranslation(Vector3f translation) {
        this.translation = translation;
    }

    public Quaternion getRotation() {
        return rotation;
    }

    public void setRotation(Quaternion rotation) {
        this.rotation = rotation;
    }

    public Vector3f getScale() {
        return scale;
    }

    public void setScale(Vector3f scale) {
        this.scale = scale;
    }
}
