package org.foxesworld.cge.modules.terrain;

import com.jme3.app.Application;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.physics.PhysicsModule;

/**
 * Terrain module: creates a procedural terrain with hills, proper shadow support, and physics.
 */
public class Terrain extends EngineModule<TerrainConfig> {

    private Geometry terrainGeo; // Keep a reference to the terrain geometry

    public Terrain(CalistaGameEngine calistaGameEngine) {
        super(Terrain.class, TerrainConfig.class, calistaGameEngine, false);
    }

    /**
     * Creates a procedural terrain based on the module's configuration.
     * @param config The terrain configuration.
     */
    public void createProceduralTerrain(TerrainConfig config) {
        // 1. Generate the mesh using our new generator
        Mesh terrainMesh = TerrainGenerator.generateTerrainMesh(
                config.getSize(),
                config.getPatchSize(),
                config.getHeightScale(),
                config.getNoiseScale(),
                12345L
        );

        // 2. Create the Geometry
        this.terrainGeo = new Geometry("ProceduralTerrain", terrainMesh);

        // Scale texture coordinates to tile the texture
        terrainMesh.scaleTextureCoordinates(new Vector2f(config.getTextureScale(), config.getTextureScale()));

        // 3. Apply material and shadows
        Material mat = gameEngine.getMaterialManager().getMaterial("assets/MatDefs/grass.j3m");
        terrainGeo.setMaterial(mat);
        terrainGeo.setShadowMode(RenderQueue.ShadowMode.Receive); // Terrain usually just receives shadows

        // 4. Attach to the scene graph and add physics
        getGameEngine().enqueue(() -> {
            getGameEngine().getRootNode().attachChild(this.terrainGeo);

            MeshCollisionShape shape = new MeshCollisionShape(terrainMesh);
            RigidBodyControl rbc = new RigidBodyControl(shape, 0f); // 0f mass = static object
            this.terrainGeo.addControl(rbc);

            PhysicsModule phys = getGameEngine().getModuleManager().getModule(PhysicsModule.class);
            if (phys != null) {
                phys.getBulletAppState().getPhysicsSpace().add(rbc);
            }
            return null;
        });
    }

    @Override
    public void onConfigReloaded() {
        // Optional: Implement logic to destroy old terrain and create a new one
    }

    @Override
    protected void initModule(CalistaGameEngine app) {
        getGameEngine().getAssetLoader().onAssetsLoaded(() -> {
            createProceduralTerrain(getConfig());
        });
    }

    // ... остальная часть вашего класса без изменений ...
    @Override
    protected void updateModule(float tpf) {}

    @Override
    protected void cleanupModule(Application app) {
        if (terrainGeo != null) {
            // Optional: Implement proper cleanup
            terrainGeo.removeFromParent();
        }
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}
}