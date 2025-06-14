package org.foxesworld.cge.tmp;

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
import org.foxesworld.cge.physics.PhysicsModule;

public class Terrain {

    public static void createTestTerrain(CalistaGameEngine calistaGameEngine, float width, float height) {
        Material mat = new Material(calistaGameEngine.getAssetManager(), "Common/MatDefs/Light/PBRLighting.j3md");
        mat.setTexture("BaseColorMap", calistaGameEngine.getAssetRepo().getTexture("ch2_dor_bushyground"));
        //mat.setTexture("NormalMap", calistaGameEngine.getAssetRepo().getTexture("ch2_dor_bushyground_n"));
        mat.setTexture("RoughnessMap", calistaGameEngine.getAssetRepo().getTexture("ch2_dor_bushyground_roughness"));
        mat.setTexture("MetallicMap", calistaGameEngine.getAssetRepo().getTexture("ch2_dor_bushyground_metallic"));
        //mat.setTexture("LightMap", assetRepo.getTexture("box_ao"));

        mat.setBoolean("UseSpecGloss", false);
        mat.setFloat("Glossiness", 0.7f);
        mat.setBoolean("UseSpecularAA", false);
        mat.setFloat("Metallic", 0.0f);

        // Повтор текстуры
        mat.getTextureParam("BaseColorMap").getTextureValue().setWrap(Texture.WrapMode.Repeat);
         mat.getTextureParam("MetallicMap").getTextureValue().setWrap(Texture.WrapMode.Repeat);
         mat.getTextureParam("RoughnessMap").getTextureValue().setWrap(Texture.WrapMode.Repeat);

        Quad quad = new Quad(width, height);
        Geometry terrain = new Geometry("TerrainPlane", quad);
        terrain.getMesh().scaleTextureCoordinates(new Vector2f(8, 8));
        terrain.setLocalTranslation(-width / 2f, 0, height / 2f);
        terrain.rotate(-FastMath.HALF_PI, 0, 0);
        terrain.setMaterial(mat);
        terrain.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        calistaGameEngine.enqueue(() -> {
            calistaGameEngine.getRootNode().attachChild(terrain);
            MeshCollisionShape shape = new MeshCollisionShape(quad);
            RigidBodyControl rbc = new RigidBodyControl(shape, 0f);
            terrain.addControl(rbc);
            PhysicsModule phys = calistaGameEngine.getModuleManager().getModule(PhysicsModule.class);
            if (phys != null) {
                phys.getBulletAppState().getPhysicsSpace().add(rbc);
            }
            return null;
        });
    }
}
