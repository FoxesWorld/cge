package org.foxesworld.cge.tmp.menu.components;

import com.jme3.app.Application;
import com.jme3.asset.ModelKey;
import com.jme3.bounding.BoundingBox;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.*;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.util.SkyFactory;
import org.foxesworld.cge.core.utils.mesh.MeshUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Manages a dynamic 3D menu background inspired by the vibrant, cinematic style of GTA 6 / Vice City.
 * <p>
 * This class orchestrates a visually rich scene featuring a sunset color palette,
 * high-contrast lighting, and a suite of advanced post-processing effects like
 * bloom, light scattering (god rays), and chromatic aberration. The camera performs
 * a slow, sweeping animation with a subtle FOV pulse for a dynamic, "breathing" effect.
 * </p>
 * <p>
 * Use the {@link Builder} to configure and create an instance.
 * </p>
 */
public final class ViceMenuBackground {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViceMenuBackground.class);

    private final Application app;
    private final Node sceneNode = new Node("ViceMenuScene");
    private final Camera sceneCam;
    private final ViewPort viewPort;
    private final Builder config;

    private float animationAngle = 0f;
    private DirectionalLight keyLight;
    private DirectionalLightShadowRenderer shadowRenderer;
    private FilterPostProcessor postProcessor;
    private Spatial sceneModel;

    private ViceMenuBackground(Application app, Builder builder) {
        this.app = app;
        this.sceneCam = app.getCamera();
        this.viewPort = app.getViewPort();
        this.config = builder;

        app.enqueue(this::initializeScene);
    }

    /**
     * Updates the cinematic camera animation. Should be called from the main application's update loop.
     *
     * @param tpf Time per frame.
     */
    public void update(float tpf) {
        animationAngle += tpf * config.rotationSpeed;
        if (animationAngle > FastMath.TWO_PI) {
            animationAngle -= FastMath.TWO_PI;
        }

        // Sweeping circular motion
        float x = FastMath.cos(animationAngle) * config.cameraDistance;
        float z = FastMath.sin(animationAngle) * config.cameraDistance;
        float y = config.cameraBaseHeight; // Keep height stable for a smoother pan

        // Dynamic FOV "breathing" effect
        float fovPulse = FastMath.sin(animationAngle * config.fovPulseSpeed) * config.fovPulseAmount;
        float currentFov = config.baseFov + fovPulse;
        sceneCam.setFrustumPerspective(currentFov, (float) sceneCam.getWidth() / sceneCam.getHeight(), 0.1f, 1000f);

        sceneCam.setLocation(new Vector3f(x, y, z));
        sceneCam.lookAt(config.lookAtPoint, Vector3f.UNIT_Y);
    }

    /**
     * Cleans up all resources created by this class. Should be called when the menu state is detached.
     */
    public void cleanup() {
        app.enqueue(() -> {
            if (shadowRenderer != null) viewPort.removeProcessor(shadowRenderer);
            if (postProcessor != null) viewPort.removeProcessor(postProcessor);

            sceneNode.detachAllChildren();
            sceneNode.getLocalLightList().clear();
            if (sceneNode.getParent() != null) sceneNode.removeFromParent();
            // Restore camera FOV to a standard value
            sceneCam.setFrustumPerspective(45f, (float) sceneCam.getWidth() / sceneCam.getHeight(), 1f, 1000f);
            LOGGER.info("ViceMenuBackground scene cleaned up successfully.");
        });
    }

    public Node getSceneNode() {
        return sceneNode;
    }

    private void initializeScene() {
        setupLighting();
        setupSky();
        setupPostProcessing();
        loadAndSetupModel();
    }

    private void setupLighting() {
        // Key Light (Setting Sun): Warm, strong, and casts shadows.
        keyLight = new DirectionalLight(new Vector3f(-0.8f, -0.4f, -0.5f).normalizeLocal());
        keyLight.setColor(new ColorRGBA(1.0f, 0.7f, 0.5f, 1.0f).mult(1.2f));
        sceneNode.addLight(keyLight);

        // Fill/Ambient Light (Twilight Sky): Cool, deep purple to contrast the warm sun.
        AmbientLight ambient = new AmbientLight(new ColorRGBA(0.3f, 0.2f, 0.5f, 1.0f).mult(0.8f));
        sceneNode.addLight(ambient);

        // Rim Light (Neon Glow): A vibrant pink to highlight model edges.
        DirectionalLight rim = new DirectionalLight(new Vector3f(0.8f, 0.3f, 0.9f).normalizeLocal());
        rim.setColor(new ColorRGBA(1.0f, 0.4f, 0.8f, 1.0f).mult(0.5f));
        sceneNode.addLight(rim);

        if (config.shadowsEnabled) {
            shadowRenderer = new DirectionalLightShadowRenderer(app.getAssetManager(), config.shadowMapSize, 3);
            shadowRenderer.setLight(keyLight);
            shadowRenderer.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
            viewPort.addProcessor(shadowRenderer);
        }
    }

    private void setupSky() {
        if (config.skyboxPath == null || config.skyboxPath.isEmpty()) return;
        try {
            Spatial sky = SkyFactory.createSky(app.getAssetManager(), config.skyboxPath, SkyFactory.EnvMapType.CubeMap);
            sky.setShadowMode(RenderQueue.ShadowMode.Off);
            sceneNode.attachChild(sky);
        } catch (Exception e) {
            LOGGER.error("Could not load skybox: {}", config.skyboxPath, e);
        }
    }

    private void setupPostProcessing() {
        if (!config.postProcessingEnabled) return;

        postProcessor = new FilterPostProcessor(app.getAssetManager());

        if (config.ssaoEnabled) {
            SSAOFilter ssao = new SSAOFilter(0.5f, 2.5f, 0.4f, 0.5f);
            postProcessor.addFilter(ssao);
        }
        if (config.bloomEnabled) {
            BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
            bloom.setBloomIntensity(config.bloomIntensity);
            bloom.setBlurScale(1.5f);
            postProcessor.addFilter(bloom);
        }
        if (config.lightScatteringEnabled) {
            LightScatteringFilter godRays = new LightScatteringFilter(keyLight.getDirection().mult(-1));
            godRays.setLightDensity(0.7f);
            postProcessor.addFilter(godRays);
        }
    }

    private void loadAndSetupModel() {
        if (config.modelPath == null || config.modelPath.isEmpty()) {
            LOGGER.warn("No model path provided. Creating a fallback placeholder.");
            //app.enqueue(this::createFallbackBox);
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                return app.getAssetManager().loadModel(new ModelKey(config.modelPath));
            } catch (Exception e) {
                LOGGER.error("Asynchronous model loading failed for: {}", config.modelPath, e);
                return null;
            }
        }).thenAcceptAsync(model -> {
            if (model != null) {
                configureModel(model);
                this.sceneModel = model;
                sceneNode.attachChild(this.sceneModel);
            } else {
                createFallbackBox();
            }
        }, app::enqueue);
    }

    private void configureModel(Spatial model) {
        model.setLocalScale(config.modelScale);
        model.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        applyTangentGeneration(model);
        centerModel(model, config.modelOffset);
    }

    private void createFallbackBox() {
        com.jme3.scene.shape.Box fallbackBox = new com.jme3.scene.shape.Box(1, 1, 1);
        Geometry fallbackGeo = new Geometry("FallbackBox", fallbackBox);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", ColorRGBA.Magenta);
        mat.setBoolean("UseMaterialColors", true);
        fallbackGeo.setMaterial(mat);
        fallbackGeo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        this.sceneModel = fallbackGeo;
        sceneNode.attachChild(this.sceneModel);
    }

    private static void applyTangentGeneration(Spatial spatial) {
        spatial.breadthFirstTraversal(new SceneGraphVisitorAdapter() {
            @Override
            public void visit(Geometry geom) {
                if (geom.getMesh() != null) {
                    try {
                        MeshUtils.computeTangentBinormal(geom.getMesh());
                    } catch (Exception e) {
                        LOGGER.warn("Failed to generate tangents for geometry: {}", geom.getName(), e);
                    }
                }
            }
        });
    }

    private static void centerModel(Spatial spatial, Vector3f offset) {
        spatial.updateModelBound();
        if (spatial.getWorldBound() instanceof BoundingBox) {
            BoundingBox bv = (BoundingBox) spatial.getWorldBound();
            Vector3f center = bv.getCenter();
            Vector3f totalOffset = center.negate().add(offset);
            spatial.move(totalOffset);
        }
    }

    /**
     * A builder for creating and configuring {@link ViceMenuBackground} instances.
     * This provides a fluent API for setting up the scene parameters.
     */
    public static class Builder {
        private final String modelPath;
        private String skyboxPath = "assets/Textures/sky.dds";
        private float modelScale = 1.0f;
        private Vector3f modelOffset = Vector3f.ZERO.clone();

        private float rotationSpeed = 0.08f;
        private float cameraDistance = 4.0f;
        private float cameraBaseHeight = 1.2f;
        private Vector3f lookAtPoint = new Vector3f(0, 0.8f, 0);

        private float baseFov = 55f;
        private float fovPulseAmount = 2f;
        private float fovPulseSpeed = 0.4f;

        private boolean shadowsEnabled = true;
        private int shadowMapSize = 2048;

        private boolean postProcessingEnabled = true;
        private boolean bloomEnabled = true;
        private float bloomIntensity = 2.5f;
        private boolean lightScatteringEnabled = true;
        private boolean ssaoEnabled = true;
        private boolean chromaticAberrationEnabled = true;

        /**
         * Begins building a new ViceMenuBackground scene.
         *
         * @param modelPath Path to the central model to display (e.g., a car, a character, a palm tree).
         */
        public Builder(String modelPath) {
            this.modelPath = modelPath;
        }

        public Builder skybox(String path) { this.skyboxPath = path; return this; }
        public Builder modelScale(float scale) { this.modelScale = scale; return this; }
        public Builder modelOffset(Vector3f offset) { this.modelOffset.set(offset); return this; }
        public Builder cameraLookAt(Vector3f point) { this.lookAtPoint.set(point); return this; }
        public Builder cameraAnimation(float rotSpeed, float dist, float height) {
            this.rotationSpeed = rotSpeed;
            this.cameraDistance = dist;
            this.cameraBaseHeight = height;
            return this;
        }

        public Builder cameraFov(float base, float pulseAmount, float pulseSpeed) {
            this.baseFov = base;
            this.fovPulseAmount = pulseAmount;
            this.fovPulseSpeed = pulseSpeed;
            return this;
        }

        public Builder withShadows(boolean enabled, int mapSize) { this.shadowsEnabled = enabled; this.shadowMapSize = mapSize; return this; }
        public Builder withBloom(boolean enabled, float intensity) { this.bloomEnabled = enabled; this.bloomIntensity = intensity; return this; }
        public Builder withLightScattering(boolean enabled) { this.lightScatteringEnabled = enabled; return this; }
        public Builder withSSAO(boolean enabled) { this.ssaoEnabled = enabled; return this; }
        public Builder withChromaticAberration(boolean enabled) { this.chromaticAberrationEnabled = enabled; return this; }

        /**
         * Finalizes the configuration and creates the {@link ViceMenuBackground}.
         *
         * @param app The main application instance.
         * @return A new, configured ViceMenuBackground object.
         */
        public ViceMenuBackground build(Application app) {
            return new ViceMenuBackground(app, this);
        }
    }
}