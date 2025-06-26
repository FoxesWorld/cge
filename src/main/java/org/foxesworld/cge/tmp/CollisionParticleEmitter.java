package org.foxesworld.cge.tmp;

import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.foxesworld.cge.CalistaGameEngine;

public class CollisionParticleEmitter implements PhysicsCollisionListener {

    private final CalistaGameEngine engine;

    public CollisionParticleEmitter(CalistaGameEngine engine) {
        this.engine = engine;
    }

    @Override
    public void collision(PhysicsCollisionEvent event) {
        // Проверяем силу столкновения
        if (event.getAppliedImpulse() > 5f) {
            Vector3f location = event.getPositionWorldOnB().clone();

            engine.enqueue(() -> {
                ParticleEmitter particles = createImpactParticles(location);
                engine.getRootNode().attachChild(particles);
                particles.emitAllParticles();

                // Удаляем после проигрыша
                engine.getTaskScheduler().getExecutor().execute(particles::removeFromParent);
            });
        }
    }

    private ParticleEmitter createImpactParticles(Vector3f location) {
        ParticleEmitter emitter = new ParticleEmitter("Impact", ParticleMesh.Type.Triangle, 20);
        Material mat = new Material(engine.getAssetManager(), "Common/MatDefs/Misc/Particle.j3md");
        mat.setTexture("Texture", engine.getAssetRepo().getTexture("explosion"));
        emitter.setMaterial(mat);

        emitter.setImagesX(2);
        emitter.setImagesY(2);
        emitter.setStartColor(ColorRGBA.White);
        emitter.setEndColor(new ColorRGBA(1f, 0f, 0f, 0f));
        emitter.setStartSize(0.3f);
        emitter.setEndSize(0.05f);
        emitter.setGravity(0, 90f, 0);
        emitter.setLowLife(3.3f);
        emitter.setHighLife(5.8f);
        emitter.setFacingVelocity(true);
        emitter.setLocalTranslation(location);
        emitter.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 3f, 0));
        emitter.getParticleInfluencer().setVelocityVariation(1f);

        return emitter;
    }
}
