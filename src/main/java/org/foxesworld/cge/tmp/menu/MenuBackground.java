package org.foxesworld.cge.tmp.menu;

import com.jme3.app.Application;
import com.jme3.bounding.BoundingBox;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
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

/**
 * Manages the content and animation of a 3D scene for a menu background.
 * <p>
 * This class encapsulates the setup of an advanced 3D scene, including:
 * <ul>
 *   <li>Loading and configuring a central 3D model.</li>
 *   <li>Implementing a 3-point lighting setup (Key, Fill, Rim lights).</li>
 *   <li>High-quality shadow rendering.</li>
 *   <li>A dynamic skybox.</li>
 *   <li>Post-processing effects like Bloom and FXAA.</li>
 *   <li>A smooth, circular camera animation with a "breathing" effect.</li>
 * </ul>
 * All setup operations are safely enqueued to run on the OpenGL thread.
 */
public class MenuBackground {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuBackground.class);

    // --- SCENE CONFIGURATION (Adjust these values) ---
    private static final String MODEL_PATH = "assets/meshes/house/objHouse.obj";
    private static final String SKYBOX_TEXTURE_PATH = "assets/Textures/sky.dds";

    // Animation
    private float angle = 0f;
    private final float rotationSpeed = 0.15f;
    private final float cameraDistance = 3.5f;
    private final float cameraBaseHeight = 1.6f;
    private final float cameraBobbingAmount = 0.03f;
    private final float cameraBobbingSpeed = 0.5f;
    private final Vector3f lookAtPoint = new Vector3f(0, 0.9f, 0);

    // Graphics Quality
    private static final int SHADOW_MAP_SIZE = 2048; // Higher resolution for sharper shadows
    private final BloomFilter.GlowMode bloomGlowMode = BloomFilter.GlowMode.Objects;
    private final float bloomIntensity = 1.5f;

    // --- Class Fields ---
    private final Application app;
    private final Node sceneNode;
    private final Camera sceneCam;
    private final ViewPort viewPort;

    private DirectionalLightShadowRenderer shadowRenderer;
    private FilterPostProcessor postProcessor;

    /**
     * Constructs and initializes the 3D menu background.
     *
     * @param app The main application instance (can be SimpleApplication or any custom Application).
     */
    public MenuBackground(Application app) {
        this.app = app;
        this.sceneNode = new Node("MenuBackgroundScene");
        this.sceneCam = app.getCamera();
        this.viewPort = app.getViewPort();

        // Enqueue all initialization to ensure it runs on the OpenGL thread.
        app.enqueue(() -> {
            setupLighting();
            //setupModel();
            setupSky();
            setupPostProcessing();
            return null; // Enqueue returns a future, so we return something.
        });
    }

    /**
     * @return The root node of the menu scene, to be attached to the main rootNode.
     */
    public Node getSceneNode() {
        return sceneNode;
    }

    /**
     * Updates the camera animation. Should be called from the main application's update loop.
     *
     * @param tpf Time per frame.
     */
    public void update(float tpf) {
        // Circular camera rotation
        angle += tpf * rotationSpeed;
        float x = FastMath.cos(angle) * cameraDistance;
        float z = FastMath.sin(angle) * cameraDistance;

        // Vertical camera "breathing" bob
        float y = cameraBaseHeight + (FastMath.sin(angle * cameraBobbingSpeed) * cameraBobbingAmount);

        sceneCam.setLocation(new Vector3f(x, y, z));
        sceneCam.lookAt(lookAtPoint, Vector3f.UNIT_Y);
    }

    /**
     * Cleans up all resources created by this class, such as lights, processors, and scene nodes.
     * Should be called when the menu state is detached.
     */
    public void cleanup() {
        app.enqueue(() -> {
            if (shadowRenderer != null) {
                viewPort.removeProcessor(shadowRenderer);
                shadowRenderer = null;
            }
            if (postProcessor != null) {
                viewPort.removeProcessor(postProcessor);
                postProcessor = null;
            }

            if (sceneNode != null) {
                sceneNode.detachAllChildren();
                sceneNode.getLocalLightList().clear();
                sceneNode.removeFromParent();
            }
            LOGGER.info("MenuBackground scene cleaned up successfully.");
            return null;
        });
    }

    private void setupModel() {
        Spatial sceneModel = loadAndConfigureModel(MODEL_PATH, 0.25f, new Vector3f(0f, -0.8f, 0f));

        if (sceneModel != null) {
            sceneNode.attachChild(sceneModel);
        } else {
            LOGGER.error("Model loading failed. Creating a fallback box as a placeholder.");
            createFallbackBox();
        }
    }

    private void setupLighting() {
        // --- 3-POINT LIGHTING SETUP ---

        // 1. Key Light (main light source, casts shadows)
        DirectionalLight keyLight = new DirectionalLight(new Vector3f(-0.8f, -0.6f, -0.5f).normalizeLocal());
        keyLight.setColor(ColorRGBA.White.mult(1.0f));
        sceneNode.addLight(keyLight);

        // 2. Fill Light (soft ambient light to soften shadows)
        AmbientLight fillLight = new AmbientLight(new ColorRGBA(0.4f, 0.4f, 0.4f, 1.0f));
        sceneNode.addLight(fillLight);

        // 3. Rim Light (highlights the model's silhouette from behind)
        DirectionalLight rimLight = new DirectionalLight(new Vector3f(0.8f, 0.4f, 0.9f).normalizeLocal());
        rimLight.setColor(ColorRGBA.White.mult(0.3f));
        sceneNode.addLight(rimLight);

        // --- SHADOWS ---
        shadowRenderer = new DirectionalLightShadowRenderer(app.getAssetManager(), SHADOW_MAP_SIZE, 3);
        shadowRenderer.setLight(keyLight); // Only the key light casts shadows
        shadowRenderer.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON); // Soft shadow edges
        viewPort.addProcessor(shadowRenderer);
    }

    private void setupSky() {
        try {
            Spatial sky = SkyFactory.createSky(app.getAssetManager(), SKYBOX_TEXTURE_PATH, SkyFactory.EnvMapType.CubeMap);
            sky.setShadowMode(RenderQueue.ShadowMode.Off); // Sky should not cast shadows
            sceneNode.attachChild(sky);
        } catch (Exception e) {
            LOGGER.error("Could not load skybox from path: {}", SKYBOX_TEXTURE_PATH, e);
        }
    }

    private void setupPostProcessing() {
        postProcessor = new FilterPostProcessor(app.getAssetManager());

        // Bloom effect for glowing parts
        BloomFilter bloomFilter = new BloomFilter(bloomGlowMode);
        //bloomFilter.setIntensity(bloomIntensity);
        postProcessor.addFilter(bloomFilter);

        // FXAA for anti-aliasing
        FXAAFilter fxaaFilter = new FXAAFilter();
        postProcessor.addFilter(fxaaFilter);

        viewPort.addProcessor(postProcessor);
    }

    private void createFallbackBox() {
        com.jme3.scene.shape.Box fallbackBox = new com.jme3.scene.shape.Box(1, 1, 1);
        Geometry fallbackGeo = new Geometry("FallbackBox", fallbackBox);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", ColorRGBA.Magenta); // Magenta is a common "missing texture" color
        mat.setBoolean("UseMaterialColors", true);
        fallbackGeo.setMaterial(mat);
        fallbackGeo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        sceneNode.attachChild(fallbackGeo);
    }

    // --- UTILITY METHODS ---

    /**
     * A flexible and reusable method to load, configure, and center a model.
     *
     * @param modelPath    The path to the model file in the asset directory.
     * @param scale        The uniform scale to apply to the model.
     * @param centerOffset An additional manual offset to apply after automatic centering.
     * @return The fully configured Spatial, or null if an error occurs.
     */
    public Spatial loadAndConfigureModel(String modelPath, float scale, Vector3f centerOffset) {
        try {
            Spatial model = app.getAssetManager().loadModel(modelPath).clone(); // Cloning is a good practice

            model.setLocalScale(scale);
            model.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

            applyTangentGeneration(model);
            centerModel(model, centerOffset);

            return model;
        } catch (Exception e) {
            LOGGER.error("Could not load and configure model: {}", modelPath, e);
            return null;
        }
    }

    /**
     * Applies tangent and binormal generation to all geometries within a Spatial.
     * This is the modern replacement for the deprecated TangentBinormalGenerator.
     *
     * @param spatial The model (Node or Geometry) to process.
     */
    private static void applyTangentGeneration(Spatial spatial) {
        spatial.breadthFirstTraversal(new SceneGraphVisitorAdapter() {
            @Override
            public void visit(Geometry geom) {
                try {
                    MeshUtils.computeTangentBinormal(geom.getMesh());
                } catch (Exception e) {
                    LOGGER.warn("Failed to generate tangents for geometry: {}", geom.getName(), e);
                }
            }
        });
    }

    /**
     * Centers a model by moving it so that the center of its BoundingVolume is at the origin,
     * plus an optional offset. This replaces the deprecated spatial.center().
     *
     * @param spatial The model to center.
     * @param offset  An additional offset to apply after centering.
     */
    private static void centerModel(Spatial spatial, Vector3f offset) {
        spatial.updateModelBound();
        BoundingBox bv = (BoundingBox) spatial.getWorldBound();
        Vector3f center = bv.getCenter();

        Vector3f totalOffset = center.negate().add(offset);
        spatial.move(totalOffset);
    }
}