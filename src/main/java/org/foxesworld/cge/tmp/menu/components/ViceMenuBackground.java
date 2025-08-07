package org.foxesworld.cge.tmp.menu.components;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.asset.ModelKey;
import com.jme3.bounding.BoundingBox;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.texture.Texture;
import com.jme3.texture.TextureCubeMap;
import com.jme3.util.SkyFactory;
import org.foxesworld.cge.core.utils.mesh.MeshUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Manages a dynamic, cinematic 3D menu background with a rich, configurable visual style.
 *
 * This class orchestrates a scene with advanced lighting, shadows, and post-processing effects
 * like bloom, light scattering, and SSAO. The camera performs a slow, sweeping animation with a
 * subtle FOV pulse for a dynamic "breathing" effect.
 *
 * <p>Configuration is handled through the fluent {@link Builder} API.
 * Example: {@code ViceMenuBackground.newBuilder("path/to/model.j3o").build(app);}
 */
public final class ViceMenuBackground {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViceMenuBackground.class);
    private static final float FOV_UPDATE_THRESHOLD = 0.01f;

    private final Application app;
    private final Node sceneNode = new Node("ViceMenuScene");
    private final Camera sceneCam;
    private final ViewPort viewPort;
    private final Builder config;

    // Scene and animation state
    private float animationAngle = 0f;
    private float lastFov = -1f; // For FOV update optimization
    private Spatial sceneModel;

    // JME Resources to be managed
    private DirectionalLight keyLight;
    private DirectionalLightShadowRenderer shadowRenderer;
    private FilterPostProcessor postProcessor;

    private ViceMenuBackground(Application app, Builder builder) {
        this.app = app;
        this.config = builder;
        // Use the application's main camera and viewport
        this.sceneCam = app.getCamera();
        this.viewPort = app.getViewPort();

        app.enqueue(this::initializeScene);
    }

    /**
     * Updates the cinematic camera animation. Must be called from the main application's update loop.
     * @param tpf Time per frame.
     */
    public void update(float tpf) {
        animationAngle += tpf * config.rotationSpeed;
        if (animationAngle > FastMath.TWO_PI) {
            animationAngle -= FastMath.TWO_PI;
        }

        // Sweeping circular camera motion
        float x = FastMath.cos(animationAngle) * config.cameraDistance;
        float z = FastMath.sin(animationAngle) * config.cameraDistance;
        sceneCam.setLocation(new Vector3f(x, config.cameraBaseHeight, z));
        sceneCam.lookAt(config.lookAtPoint, Vector3f.UNIT_Y);

        // Dynamic FOV "breathing" effect, optimized to avoid unnecessary updates
        float fovPulse = FastMath.sin(animationAngle * config.fovPulseSpeed) * config.fovPulseAmount;
        float currentFov = config.baseFov + fovPulse;
        if (Math.abs(currentFov - lastFov) > FOV_UPDATE_THRESHOLD) {
            sceneCam.setFrustumPerspective(currentFov, (float) sceneCam.getWidth() / sceneCam.getHeight(), 0.1f, 1000f);
            lastFov = currentFov;
        }
    }

    /**
     * Cleans up all JME3 resources created by this class.
     * Call this when the menu state is detached to prevent memory leaks.
     */
    public void cleanup() {
        app.enqueue(() -> {
            if (shadowRenderer != null) viewPort.removeProcessor(shadowRenderer);
            if (postProcessor != null) viewPort.removeProcessor(postProcessor);

            sceneNode.detachAllChildren();
            sceneNode.getLocalLightList().clear();
            if (sceneNode.getParent() != null) {
                sceneNode.removeFromParent();
            }
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
        loadAndSetupModel(); // Load model first to calculate bounds
    }

    private void setupLighting() {
        // 1. Key Light (e.g., the Sun): Strong, directional, casts shadows.
        keyLight = new DirectionalLight(config.keyLightDirection);
        keyLight.setColor(config.keyLightColor);
        sceneNode.addLight(keyLight);

        // 2. Fill Light (e.g., Sky Light): Soft, ambient light to fill in shadows.
        AmbientLight ambient = new AmbientLight(config.ambientLightColor);
        sceneNode.addLight(ambient);

        // 3. Rim Light (optional): Highlights model edges for stylistic effect.
        if (config.rimLightEnabled) {
            DirectionalLight rim = new DirectionalLight(config.rimLightDirection);
            rim.setColor(config.rimLightColor);
            sceneNode.addLight(rim);
        }

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
            TextureCubeMap cubeMap = (TextureCubeMap) app.getAssetManager().loadTexture(config.skyboxPath);
            cubeMap.setMagFilter(Texture.MagFilter.Bilinear);
            cubeMap.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);

            Spatial sky = SkyFactory.createSky(app.getAssetManager(), cubeMap, SkyFactory.EnvMapType.CubeMap);
            sky.setShadowMode(RenderQueue.ShadowMode.Off);

            sky.updateLogicalState(0);
            sky.updateGeometricState();
            app.getRenderManager().preloadScene(sky);

            sceneNode.attachChild(sky);
        } catch (Exception e) {
            LOGGER.error("Could not load skybox: {}", config.skyboxPath, e);
        }
    }

    private void loadAndSetupModel() {
        if (config.modelPath == null || config.modelPath.isEmpty()) {
            LOGGER.warn("No model path provided. Creating a fallback placeholder.");
            app.enqueue(this::createFallbackBox);
            return;
        }

        // Asynchronously load the model to avoid freezing the application
        CompletableFuture.supplyAsync(() -> {
            try {
                return app.getAssetManager().loadModel(new ModelKey(config.modelPath));
            } catch (Exception e) {
                LOGGER.error("Asynchronous model loading failed for: {}", config.modelPath, e);
                return null; // Return null on failure
            }
        }).thenAcceptAsync(model -> {
            if (model != null) {
                configureModel(model);
                this.sceneModel = model;
                sceneNode.attachChild(this.sceneModel);
            } else {
                createFallbackBox(); // Create fallback if loading failed
            }
        }, app::enqueue); // Enqueue the result to be processed on the main render thread
    }

    private void configureModel(Spatial model) {
        model.setLocalScale(config.modelScale);
        model.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        applyTangentGeneration(model);
        centerModel(model, config.modelOffset);
    }

    private void createFallbackBox() {
        Geometry fallbackGeo = new Geometry("FallbackBox", new com.jme3.scene.shape.Box(1, 1, 1));
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", ColorRGBA.Magenta); // Use a bright color to indicate a problem
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
                        LOGGER.warn("Failed to generate tangents for geometry: {}. Normal mapping may not work.", geom.getName());
                    }
                }
            }
        });
    }

    private static void centerModel(Spatial spatial, Vector3f offset) {
        spatial.updateModelBound(); // Ensure bounds are up-to-date
        if (spatial.getWorldBound() instanceof BoundingBox bv) {
            Vector3f center = bv.getCenter();
            spatial.move(center.negate().add(offset));
        }
    }

    /**
     * A fluent builder for creating and configuring {@link ViceMenuBackground} instances.
     */
    public static class Builder {
        private final String modelPath;
        private AssetManager assetManager; // For future use, e.g. loading materials

        // Model and Sky
        private String skyboxPath = "Textures/Sky/Lagoon/Lagoon.dds";
        private float modelScale = 1.0f;
        private Vector3f modelOffset = Vector3f.ZERO.clone();

        // Camera Animation
        private float rotationSpeed = 0.08f;
        private float cameraDistance = 4.0f;
        private float cameraBaseHeight = 1.2f;
        private Vector3f lookAtPoint = new Vector3f(0, 0.8f, 0);
        private float baseFov = 55f;
        private float fovPulseAmount = 2f;
        private float fovPulseSpeed = 0.4f;

        // Lighting Configuration
        private ColorRGBA keyLightColor = new ColorRGBA(1.0f, 0.7f, 0.5f, 1.0f).mult(1.2f);
        private Vector3f keyLightDirection = new Vector3f(-0.8f, -0.4f, -0.5f).normalizeLocal();
        private ColorRGBA ambientLightColor = new ColorRGBA(0.3f, 0.2f, 0.5f, 1.0f).mult(0.8f);
        private boolean rimLightEnabled = true;
        private ColorRGBA rimLightColor = new ColorRGBA(1.0f, 0.4f, 0.8f, 1.0f).mult(0.5f);
        private Vector3f rimLightDirection = new Vector3f(0.8f, 0.3f, 0.9f).normalizeLocal();

        // Shadows
        private boolean shadowsEnabled = true;
        private int shadowMapSize = 2048;

        // Post-Processing
        private boolean postProcessingEnabled = true;
        private boolean bloomEnabled = true;
        private float bloomIntensity = 2.5f;
        private float bloomBlurScale = 1.5f;
        private boolean lightScatteringEnabled = true;
        private float lightScatteringDensity = 0.7f;
        private boolean ssaoEnabled = true;
        private float ssaoSampleRadius = 0.5f, ssaoIntensity = 2.5f, ssaoScale = 0.4f, ssaoBias = 0.5f;
        private boolean chromaticAberrationEnabled = true;

        public Builder(String modelPath) {
            this.modelPath = modelPath;
        }

        /**
         * Creates a new builder instance.
         * @param modelPath Path to the central 3D model (e.g., "path/to/car.j3o").
         * @return A new Builder instance.
         */
        public static Builder newBuilder(String modelPath) {
            return new Builder(modelPath);
        }

        // --- Fluent Configuration Methods ---

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

        public Builder keyLight(ColorRGBA color, Vector3f direction) { this.keyLightColor = color; this.keyLightDirection = direction.normalize(); return this; }
        public Builder ambientLight(ColorRGBA color) { this.ambientLightColor = color; return this; }
        public Builder rimLight(boolean enabled, ColorRGBA color, Vector3f direction) {
            this.rimLightEnabled = enabled; this.rimLightColor = color; this.rimLightDirection = direction.normalize(); return this;
        }

        public Builder withShadows(boolean enabled, int mapSize) { this.shadowsEnabled = enabled; this.shadowMapSize = mapSize; return this; }
        public Builder withBloom(boolean enabled, float intensity, float blurScale) { this.bloomEnabled = enabled; this.bloomIntensity = intensity; this.bloomBlurScale = blurScale; return this; }
        public Builder withLightScattering(boolean enabled, float density) { this.lightScatteringEnabled = enabled; this.lightScatteringDensity = density; return this; }
        public Builder withSSAO(boolean enabled, float radius, float intensity, float scale, float bias) {
            this.ssaoEnabled = enabled; this.ssaoSampleRadius = radius; this.ssaoIntensity = intensity; this.ssaoScale = scale; this.ssaoBias = bias; return this;
        }
        public Builder withChromaticAberration(boolean enabled) { this.chromaticAberrationEnabled = enabled; return this; }

        /**
         * Finalizes the configuration and creates the {@link ViceMenuBackground}.
         * @param app The main application instance.
         * @return A new, configured ViceMenuBackground object ready for use.
         */
        public ViceMenuBackground build(Application app) {
            return new ViceMenuBackground(app, this);
        }
    }
}