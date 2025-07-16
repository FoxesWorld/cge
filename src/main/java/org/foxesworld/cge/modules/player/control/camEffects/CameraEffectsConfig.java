package org.foxesworld.cge.modules.player.control.camEffects;

import com.google.gson.Gson;
import com.jme3.asset.AssetManager;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Configuration data for the CameraEffectsControl, loaded from a JSON file.
 * This class provides a structured way for designers to tweak every aspect
 * of the camera's "feel" without modifying Java code.
 */
public class CameraEffectsConfig {

    public SpringSettings positionalSpring = new SpringSettings(180f, 20f);
    public SpringSettings rotationalSpring = new SpringSettings(170f, 18f);
    public SpringSettings fovSpring = new SpringSettings(80f, 16f);

    public MotionProfile walkProfile = new MotionProfile(0.45f, 0.018f, 0.022f, 0.10f, 1.8f, 0.0008f, 40f, 0f);
    public MotionProfile sprintProfile = new MotionProfile(0.70f, 0.040f, 0.030f, 0.12f, 2.5f, 0.0015f, 55f, 3.0f);

    public IdleSettings idleSettings = new IdleSettings(0.22f, 0.0035f);
    public LandingSettings landingSettings = new LandingSettings(0.5f, 0.1f, 0.6f, 8.0f);
    public ThirdPersonSettings thirdPersonSettings = new ThirdPersonSettings(3.5f, 0.5f, 8.0f);

    // Updated structure for rotational lag
    public RotationalLagSettings rotationalLagSettings = new RotationalLagSettings(0.5f, new SpringSettings(150f, 24f));


    // --- Inner classes for structured configuration ---

    /**
     * Defines the physical properties of a damped spring.
     */
    public static class SpringSettings {
        public float stiffness;
        public float damping;

        // Default constructor for Gson
        public SpringSettings() {}

        public SpringSettings(float stiffness, float damping) {
            this.stiffness = stiffness;
            this.damping = damping;
        }
    }

    /**
     * Defines all biomechanical parameters for a specific movement type (walk/sprint).
     */
    public static class MotionProfile {
        public float strideFactor, bobAmp, swayAmp, rollAmp, curvePower, jitterIntensity, jitterSpeed, fovAdd;

        // Default constructor for Gson
        public MotionProfile() {}

        public MotionProfile(float s, float b, float sw, float r, float c, float j, float js, float fov) {
            strideFactor = s; bobAmp = b; swayAmp = sw; rollAmp = r; curvePower = c;
            jitterIntensity = j; jitterSpeed = js; fovAdd = fov;
        }
    }

    /**
     * Defines settings for the idle breathing effect.
     */
    public static class IdleSettings {
        public float breathFreq, breathAmp;

        public IdleSettings() {}
        public IdleSettings(float f, float a) { breathFreq = f; breathAmp = a; }
    }

    /**
     * Defines settings for the landing impact effect.
     */
    public static class LandingSettings {
        public float impactFactor, minImpact, maxImpact, fovPunch;

        public LandingSettings() {}
        public LandingSettings(float f, float min, float max, float punch) {
            impactFactor = f; minImpact = min; maxImpact = max; fovPunch = punch;
        }
    }

    /**
     * Defines settings for the third-person camera view.
     */
    public static class ThirdPersonSettings {
        public float distance, heightOffset, smoothSpeed;

        public ThirdPersonSettings() {}
        public ThirdPersonSettings(float d, float h, float s) { distance = d; heightOffset = h; smoothSpeed = s; }
    }

    /**
     * Defines settings for the rotational lag effect (camera sway on mouse look).
     */
    public static class RotationalLagSettings {
        public float lagIntensity;
        public SpringSettings springSettings;

        public RotationalLagSettings() {}
        public RotationalLagSettings(float intensity, SpringSettings settings) {
            this.lagIntensity = intensity;
            this.springSettings = settings;
        }
    }

    /**
     * Loads the configuration from a JSON file using the AssetManager.
     * If loading fails, it returns a default configuration object and logs an error.
     *
     * @param assetManager The application's asset manager.
     * @param path The path to the JSON file (e.g., "config/camera_effects.json").
     * @return A new CameraEffectsConfig instance, either loaded or default.
     */
    public static CameraEffectsConfig load(AssetManager assetManager, String path) {
        try (Reader reader = new InputStreamReader(assetManager.locateAsset(new com.jme3.asset.AssetKey<>(path)).openStream(), StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, CameraEffectsConfig.class);
        } catch (Exception e) {
            System.err.println("WARNING: Failed to load camera effects config from '" + path + "', using default values. Error: " + e.getMessage());
            return new CameraEffectsConfig();
        }
    }
}