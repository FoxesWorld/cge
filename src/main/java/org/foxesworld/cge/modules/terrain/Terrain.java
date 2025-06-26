package org.foxesworld.cge.modules.terrain;

import com.jme3.app.Application;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.loader.JmeProgressBar;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.physics.PhysicsModule;

/**
 * Terrain module: creates a flat test terrain with proper shadow support and physics.
 */
public class Terrain extends EngineModule<TerrainConfig> {

    public Terrain(CalistaGameEngine calistaGameEngine) {
        super(Terrain.class, TerrainConfig.class, calistaGameEngine, false);
    }

    /**
     * Creates a test flat terrain with PBR material, proper shadow mode and physics.
     * @param width width of the terrain
     * @param height height of the terrain
     */
    public void createTestTerrain(float width, float height) {
        Material mat = gameEngine.getMaterialManager().getMaterial("assets/MatDefs/grass.j3m");
        Quad quad = new Quad(width, height);
        Geometry terrain = new Geometry("TerrainPlane", quad);
        terrain.getMesh().scaleTextureCoordinates(new Vector2f(16, 16));
        terrain.setLocalTranslation(-width / 2f, 0, height / 2f);
        terrain.rotate(-FastMath.HALF_PI, 0, 0);
        terrain.setMaterial(mat);

        // Enable both casting and receiving shadows
        terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        getGameEngine().enqueue(() -> {
            getGameEngine().getRootNode().attachChild(terrain);

            // Proper physics: use Geometry, not Quad, for collision shape!
            MeshCollisionShape shape = new MeshCollisionShape(terrain.getMesh());
            RigidBodyControl rbc = new RigidBodyControl(shape, 0f);
            terrain.addControl(rbc);

            PhysicsModule phys = getGameEngine().getModuleManager().getModule(PhysicsModule.class);
            if (phys != null) {
                phys.getBulletAppState().getPhysicsSpace().add(rbc);
            }
            return null;
        });
    }

    @Override
    public void onConfigReloaded() {
        // Add dynamic reconfiguration if needed
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        getGameEngine().getAssetLoader().onAssetsLoaded(() -> {
            createTestTerrain(getConfig().getWidth(), getConfig().getHeight());
        });
    }

    @Override
    protected void updateModule(float tpf) {
        // Reserved for runtime terrain updates
    }

    @Override
    protected void cleanupModule(Application app) {
        // Implement terrain cleanup if needed
    }

    @Override
    protected void onEnable() {
        // Implement if needed
    }

    @Override
    protected void onDisable() {
        // Implement if needed
    }
}