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
import org.foxesworld.cge.core.loader.ConsoleProgressBar;
import org.foxesworld.cge.core.loader.JmeProgressBar;
import org.foxesworld.cge.core.module.EngineModule;
import org.foxesworld.cge.modules.physics.PhysicsModule;

public class Terrain  extends EngineModule<TerrainConfig> {

    /**
     * Constructs an EngineModule instance, registers its configuration if provided,
     * and initializes core dependencies.
     *
     * @param calistaGameEngine  the central game engine instance
     */
    public Terrain(CalistaGameEngine calistaGameEngine) {
        super("terrain", TerrainConfig.class, calistaGameEngine);
    }

    public void createTestTerrain(float width, float height) {
        Material mat = new Material(getGameEngine().getAssetManager(), "Common/MatDefs/Light/PBRLighting.j3md");
        mat.setTexture("BaseColorMap", getGameEngine().getAssetRepo().getTexture("box"));
        //mat.setTexture("NormalMap", calistaGameEngine.getAssetRepo().getTexture("ch2_dor_bushyground_n"));
        //mat.setTexture("RoughnessMap", calistaGameEngine.getAssetRepo().getTexture("ch2_dor_bushyground_roughness"));
        //at.setTexture("MetallicMap", calistaGameEngine.getAssetRepo().getTexture("ch2_dor_bushyground_metallic"));
        //mat.setTexture("LightMap", assetRepo.getTexture("box_ao"));

        mat.setBoolean("UseSpecGloss", false);
        mat.setFloat("Glossiness", 0.7f);
        mat.setBoolean("UseSpecularAA", false);
        mat.setFloat("Metallic", 0.0f);

        // Повтор текстуры
        mat.getTextureParam("BaseColorMap").getTextureValue().setWrap(Texture.WrapMode.Repeat);
         //mat.getTextureParam("MetallicMap").getTextureValue().setWrap(Texture.WrapMode.Repeat);
         //mat.getTextureParam("RoughnessMap").getTextureValue().setWrap(Texture.WrapMode.Repeat);

        Quad quad = new Quad(width, height);
        Geometry terrain = new Geometry("TerrainPlane", quad);
        terrain.getMesh().scaleTextureCoordinates(new Vector2f(32, 32));
        terrain.setLocalTranslation(-width / 2f, 0, height / 2f);
        terrain.rotate(-FastMath.HALF_PI, 0, 0);
        terrain.setMaterial(mat);
        terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        getGameEngine().enqueue(() -> {
            getGameEngine().getRootNode().attachChild(terrain);
            MeshCollisionShape shape = new MeshCollisionShape(quad);
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
    protected void onConfigReloaded() throws Exception {

    }

    @Override
    protected void initModule(CalistaGameEngine app) throws Exception {
        getGameEngine().getAssetLoader().loadAllAssets(() -> {
            createTestTerrain(getConfig().getWidth(), getConfig().getHeight());
        }, new JmeProgressBar(this.gameEngine));
    }

    @Override
    protected void updateModule(float tpf) throws Exception {

    }

    @Override
    protected void cleanupModule(Application app) throws Exception {

    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }
}
