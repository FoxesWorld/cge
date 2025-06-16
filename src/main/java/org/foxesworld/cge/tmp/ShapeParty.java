package org.foxesworld.cge.tmp;

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.physics.PhysicsModule;

import java.util.Random;

public class ShapeParty {

    private final CalistaGameEngine calistaGameEngine;

    public ShapeParty(CalistaGameEngine calistaGameEngine) {
        this.calistaGameEngine = calistaGameEngine;
    }

    public void startParty() {
        Random random = new Random();
        int count = 1;
        float areaRadius = 4f;
        float baseHeight = 2f;

        // Заранее создаем шаблон эффекта частиц
        ParticleEmitter particleTemplate = new ParticleEmitter("my particle effect", ParticleMesh.Type.Triangle, 60);
        Material pmMat = new Material(calistaGameEngine.getAssetManager(), "Common/MatDefs/Misc/Particle.j3md");
        pmMat.setTexture("Texture", calistaGameEngine.getAssetManager().loadTexture("Textures/explosion.png"));
        particleTemplate.setMaterial(pmMat);
        particleTemplate.setImagesX(1);
        particleTemplate.setImagesY(1);
        particleTemplate.setStartColor(ColorRGBA.Orange);
        particleTemplate.setEndColor(ColorRGBA.Red);
        particleTemplate.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 2, 0));
        particleTemplate.setStartSize(0.5f);
        particleTemplate.setEndSize(0.1f);
        particleTemplate.setGravity(0, 0, 0);
        particleTemplate.setLowLife(1f);
        particleTemplate.setHighLife(2f);

        calistaGameEngine.enqueue(() -> {
            PhysicsModule physicsModule = calistaGameEngine.getModuleManager().getModule(PhysicsModule.class);
            AssetManager assetManager = calistaGameEngine.getAssetManager();

            // Загрузка модели
            Spatial model = calistaGameEngine.getAssetRepo().getModel("ParkBench01");
            if (model == null) {
                throw new RuntimeException("Не удалось загрузить модель");
            }

            for (int i = 0; i < count; i++) {
                Spatial instance = model.clone();
                instance.setName("ModelInstance_" + i);

                float x = (random.nextFloat() * 2 - 1) * areaRadius;
                float z = (random.nextFloat() * 2 - 1) * areaRadius;
                float y = baseHeight + random.nextFloat() * 5f;
                instance.setLocalTranslation(x, y, z);

                // Добавляем эффект частиц как дочерний элемент к объекту
                ParticleEmitter emitter = particleTemplate.clone();
                emitter.setLocalTranslation(0, 1.5f, 0); // немного над объектом
                ((Node) instance).attachChild(emitter);
                emitter.emitAllParticles(); // запускаем эффект

                calistaGameEngine.getRootNode().attachChild(instance);

                if (physicsModule != null && instance instanceof Geometry) {
                    physicsModule.addRigidBody(instance, 1.0f);
                } else if (physicsModule != null && instance instanceof Node) {
                    processNodePhysics((Node) instance, physicsModule);
                }
            }
        });
    }


    // Рекурсивное масштабирование текстурных координат
    private void scaleTextureCoordinates(Spatial spatial, Vector2f scale) {
        if (spatial instanceof Geometry) {
            ((Geometry) spatial).getMesh().scaleTextureCoordinates(scale);
        } else if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                scaleTextureCoordinates(child, scale);
            }
        }
    }

    // Обработка физики для нод
    private void processNodePhysics(Node node, PhysicsModule physicsModule) {
        for (Spatial child : node.getChildren()) {
            if (child instanceof Geometry) {
                physicsModule.addRigidBody(child, 1.0f);
            } else if (child instanceof Node) {
                processNodePhysics((Node) child, physicsModule);
            }
        }
    }
}