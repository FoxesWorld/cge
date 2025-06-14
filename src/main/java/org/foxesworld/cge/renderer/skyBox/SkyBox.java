package org.foxesworld.cge.renderer.skyBox;

import com.jme3.app.Application;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.scene.Spatial;
import com.jme3.util.SkyFactory;
import jme3utilities.sky.SkyControl;
import jme3utilities.sky.StarsOption;
import jme3utilities.sky.Updater;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.renderer.RendererModule;

import java.time.LocalTime;

/**
 * The {@code SkyBox} class is a visual sky environment module designed for the CalistaGameEngine.
 * It integrates advanced atmospheric rendering, including dynamic sky textures, sun and moon lights,
 * smooth diurnal time simulation, and directional light shadow support.
 * <p>
 * This implementation uses {@link SkyControl} for animated celestial motion and cloud simulation,
 * and initializes lighting based on real-time clock synchronized to the current system time.
 * </p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *     <li>Real-time sun and moon simulation with dynamic light directions</li>
 *     <li>Smooth interpolation toward the current time of day</li>
 *     <li>Sun shadows via {@link DirectionalLightShadowRenderer}</li>
 *     <li>Customizable cloud cover and ambient light</li>
 * </ul>
 *
 * @author Calista
 */
public class SkyBox extends EngineModule<SkyBoxConfig> {

    private final CalistaGameEngine calistaGameEngine;
    private final Vector3f sunDir = new Vector3f();

    private SkyControl skyControl;
    private DirectionalLight sunLight;
    private DirectionalLight moonLight;
    private DirectionalLightShadowRenderer dlsr;

    /** Simulated time in hours (e.g., 13.5 = 1:30 PM). */
    private float simulatedHour = 12.0f;

    /** Speed at which simulated time approaches the real-world time (0.1 = slow, 1.0 = instant). */
    private float smoothingSpeed = 0.1f;

    /**
     * Constructs the SkyBox module and registers it to the engine.
     *
     * @param rendererModule the renderer module that owns this sky module
     */
    public SkyBox(RendererModule rendererModule) {
        super("skybox", SkyBoxConfig.class, rendererModule.getGameEngine());
        this.calistaGameEngine = rendererModule.getGameEngine();
    }

    /**
     * Called when the configuration is reloaded.
     * Can be used to dynamically update parameters such as cloud settings or lighting intensities.
     */
    @Override
    protected void onConfigReloaded() {
        // Optional: apply updated config to SkyControl or ShadowRenderer
    }

    /**
     * Initializes the SkyBox system, sky dome, lighting, and shadow rendering.
     *
     * @param app the main game engine application
     */
    @Override
    protected void initModule(CalistaGameEngine app) {
        calistaGameEngine.enqueue(() -> {
            // 1) Create static sky dome
            Spatial sky = SkyFactory.createSky(
                    calistaGameEngine.getAssetManager(),
                    calistaGameEngine.getAssetRepo().getTexture(getConfig().getSkyBoxTexture()),
                    SkyFactory.EnvMapType.valueOf(getConfig().getEnvMap())
            );
            sky.setShadowMode(RenderQueue.ShadowMode.Off);

            // 2) Setup SkyControl
            skyControl = new SkyControl(
                    calistaGameEngine.getAssetManager(),
                    calistaGameEngine.getCamera(),
                    getConfig().getCloudFlattering(),
                    StarsOption.valueOf(getConfig().getStarsOption()),
                    getConfig().isBottomDome()
            );
            calistaGameEngine.getRootNode().addControl(skyControl);

            // Configure SkyControl parameters
            skyControl.setCloudiness(0.8f);
            skyControl.setCloudsYOffset(0.4f);
            skyControl.setTopVerticalAngle(1.78f);

            Updater updater = skyControl.getUpdater();
            ColorRGBA ambientColor = ColorRGBA.DarkGray.mult(0.7f).add(ColorRGBA.LightGray.mult(0.3f));
            updater.setAmbientLight(new AmbientLight(ambientColor));

            // 3) Setup directional light for the Sun
            sunLight = new DirectionalLight();
            sunLight.setColor(ColorRGBA.White.mult(getConfig().getSunLightIntensity()));
            calistaGameEngine.getRootNode().addLight(sunLight);
            updater.setMainLight(sunLight);

            // 4) Setup directional light for the Moon
            moonLight = new DirectionalLight();
            moonLight.setColor(new ColorRGBA(0.6f, 0.6f, 0.8f, 1f).mult(getConfig().getMoonLightIntensity()));
            calistaGameEngine.getRootNode().addLight(moonLight);

            // 5) Setup shadow renderer for the Sun
            int shadowMapSize = getConfig().getShadowMapSize();
            int shadowFrustumCount = getConfig().getShadowFrustumCount();
            dlsr = new DirectionalLightShadowRenderer(
                    calistaGameEngine.getAssetManager(),
                    shadowMapSize,
                    shadowFrustumCount
            );
            dlsr.setLight(sunLight);
            dlsr.setShadowZExtend(getConfig().getShadowZExtend());
            ViewPort viewPort = calistaGameEngine.getRenderManager().getMainView("Default");
            viewPort.addProcessor(dlsr);

            skyControl.setEnabled(true);
        });
    }

    /**
     * Updates the simulated time and sun/moon light directions.
     *
     * @param tpf time per frame (seconds)
     */
    @Override
    public void update(float tpf) {
        simulatedHour = getCurrentHour();

        // Обновление SkyControl
        skyControl.getSunAndStars().setHour(simulatedHour);

        // Направление солнца и луны
        Vector3f sunDirD = skyControl.getSunAndStars().sunDirection(sunDir);
        Vector3f sunDirF = new Vector3f(-sunDirD.x, -sunDirD.y, -sunDirD.z);
        sunLight.setDirection(sunDirF);
        moonLight.setDirection(sunDirF.negate());

        if (dlsr != null) {
            dlsr.setLight(sunLight);
        }
    }

    private float getCurrentHour() {
        LocalTime now = LocalTime.now();
        return now.getHour() + now.getMinute() / 60f + now.getSecond() / 3600f;
    }


    /**
     * Optional module-specific update logic.
     *
     * @param tpf time per frame
     * @throws Exception if an update error occurs
     */
    @Override
    protected void updateModule(float tpf) throws Exception {
        // Reserved for future extension
    }

    /**
     * Cleans up resources and detaches lights, sky controls, and shadow processors.
     *
     * @param app the application context
     */
    @Override
    protected void cleanupModule(Application app) {
        if (skyControl != null) {
            skyControl.setEnabled(false);
        }
        if (sunLight != null) {
            calistaGameEngine.getRootNode().removeLight(sunLight);
        }
        if (moonLight != null) {
            calistaGameEngine.getRootNode().removeLight(moonLight);
        }
        if (dlsr != null) {
            viewPort().removeProcessor(dlsr);
        }
    }

    /**
     * Enables the SkyBox module and its rendering processors.
     */
    @Override
    protected void onEnable() {
        if (skyControl != null) {
            skyControl.setEnabled(true);
        }
        if (dlsr != null) {
            viewPort().addProcessor(dlsr);
        }
    }

    /**
     * Disables the SkyBox module and removes rendering processors.
     */
    @Override
    protected void onDisable() {
        if (skyControl != null) {
            skyControl.setEnabled(false);
        }
        if (dlsr != null) {
            viewPort().removeProcessor(dlsr);
        }
    }

    /**
     * Utility method to retrieve the primary {@link ViewPort} used by the engine.
     *
     * @return the main ViewPort instance
     */
    private ViewPort viewPort() {
        return calistaGameEngine.getRenderManager().getMainView("Default");
    }
}
