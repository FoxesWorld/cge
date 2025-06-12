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
 * Улучшенный SkyBox: добавлено два направленных света для солнца и луны,
 * а также тени от солнца.
 */
public class SkyBox extends EngineModule<SkyBoxConfig> {
    private final CalistaGameEngine calistaGameEngine;
    private final Vector3f sunDir = new Vector3f();
    private SkyControl skyControl;
    private DirectionalLight sunLight;
    private DirectionalLight moonLight;
    private DirectionalLightShadowRenderer dlsr;

    public SkyBox(RendererModule rendererModule) {
        super("skybox", SkyBoxConfig.class, rendererModule.getGameEngine());
        this.calistaGameEngine = rendererModule.getGameEngine();
    }

    @Override
    protected void onConfigReloaded() {
        // Можно обновить параметры skyControl или ShadowRenderer при перезагрузке конфига
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        calistaGameEngine.enqueue(() -> {
            // 1) Создаем sky-куб
            Spatial sky = SkyFactory.createSky(
                    calistaGameEngine.getAssetManager(),
                    calistaGameEngine.getAssetRepo().getTexture(getConfig().getSkyBoxTexture()),
                    SkyFactory.EnvMapType.valueOf(getConfig().getEnvMap())
            );
            sky.setShadowMode(RenderQueue.ShadowMode.Off);

            // 2) Инициализируем SkyControl
            skyControl = new SkyControl(
                    calistaGameEngine.getAssetManager(),
                    calistaGameEngine.getCamera(),
                    getConfig().getCloudFlattering(),
                    StarsOption.valueOf(getConfig().getStarsOption()),
                    getConfig().isBottomDome()
            );
            calistaGameEngine.getRootNode().addControl(skyControl);

            skyControl.setCloudiness(0.8f);
            skyControl.setCloudsYOffset(0.4f);
            skyControl.setTopVerticalAngle(1.78f);

            Updater updater = skyControl.getUpdater();

            // Заменяем недоступный interpolate:
            ColorRGBA ambientColor = ColorRGBA.DarkGray.mult(0.7f).add(ColorRGBA.LightGray.mult(0.3f));
            updater.setAmbientLight(new AmbientLight(ambientColor));

            // 3) Создаем направленный свет для Солнца
            sunLight = new DirectionalLight();
            sunLight.setColor(ColorRGBA.White.mult(getConfig().getSunLightIntensity()));
            calistaGameEngine.getRootNode().addLight(sunLight);
            updater.setMainLight(sunLight);

            // 4) Создаем направленный свет для Луны
            moonLight = new DirectionalLight();
            moonLight.setColor(new ColorRGBA(0.6f, 0.6f, 0.8f, 1f).mult(getConfig().getMoonLightIntensity()));
            calistaGameEngine.getRootNode().addLight(moonLight);

            // 5) Настраиваем ShadowRenderer
            int shadowMapSize = getConfig().getShadowMapSize();
            int shadowFrustumCount = getConfig().getShadowFrustumCount();
            dlsr = new DirectionalLightShadowRenderer(
                    calistaGameEngine.getAssetManager(),
                    shadowMapSize,
                    shadowFrustumCount
            );
            dlsr.setLight(sunLight);
            //dlsr.setEdgeFilteringMode(getConfig().getEdgeFilteringMode());
            dlsr.setShadowZExtend(getConfig().getShadowZExtend());
            ViewPort viewPort = calistaGameEngine.getRenderManager().getMainView("Default");
            viewPort.addProcessor(dlsr);

            skyControl.setEnabled(true);
        });
    }


    @Override
    public void update(float tpf) {
        LocalTime currentTime = LocalTime.now();
        float hour = currentTime.getHour() + currentTime.getMinute() / 60f;

        // Устанавливаем час в SkyControl
        skyControl.getSunAndStars().setHour(hour);

        // Получаем направление солнца (Vector3d)
        Vector3f sunDirD = skyControl.getSunAndStars().sunDirection(sunDir);

        // Преобразуем в Vector3f и инвертируем (в JME3 направление к источнику задаётся *в сторону света*)
        Vector3f sunDirF = new Vector3f((float) -sunDirD.x, (float) -sunDirD.y, (float) -sunDirD.z);
        sunLight.setDirection(sunDirF);

        // Направление луны — противоположно направлению солнца
        Vector3f moonDirF = sunDirF.negate(); // уже инвертировано, ещё раз инвертируем для противоположного направления
        moonLight.setDirection(moonDirF);

        // Обновляем тени, если используется DirectionalLightShadowRenderer
        if (dlsr != null) {
            dlsr.setLight(sunLight);
        }
    }
    @Override
    protected void updateModule(float tpf) throws Exception {
        // Дополнительная логика модуля (если потребуется)
    }

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

    @Override
    protected void onEnable() {
        if (skyControl != null) {
            skyControl.setEnabled(true);
        }
        if (dlsr != null) {
            viewPort().addProcessor(dlsr);
        }
    }

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
     * Удобный метод для получения MainViewPort из движка.
     */
    private ViewPort viewPort() {
        return calistaGameEngine.getRenderManager().getMainView("Default");
    }
}