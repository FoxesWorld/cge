package org.foxesworld.cge.core.loadingLogos;

/**
 * A POJO representing the configuration for a single logo screen.
 * GSON uses this to map data from a JSON object.
 */
public class LogoConfig {
    // --- Обязательные поля ---
    /**
     * The path to the logo image file, relative to the assets directory.
     */
    public String imagePath;
    /**
     * The desired final width of the logo in pixels.
     */
    public float width;
    /**
     * The desired final height of the logo in pixels.
     */
    public float height;

    // --- Необязательные поля (будут использоваться значения по умолчанию, если отсутствуют в JSON) ---
    /**
     * The duration of the logo's scaling animation in seconds.
     * Defaults to 3.0f.
     */
    public Float duration;
    /**
     * The starting scale of the animation.
     * Defaults to 0.1f.
     */
    public Float startScale;
    /**
     * The ending scale of the animation. Can be > 1.0 for an "overshoot" effect.
     * Defaults to 1.0f.
     */
    public Float endScale;
    /**
     * Whether this logo screen can be skipped with a mouse click.
     * Defaults to true.
     */
    public Boolean skippable;
}