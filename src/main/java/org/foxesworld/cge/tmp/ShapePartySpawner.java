// Файл: src/main/java/org/foxesworld/cge/tmp/ShapePartySpawner.java
package org.foxesworld.cge.tmp;

import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.modules.ecs.ECSModule;
import org.foxesworld.cge.modules.ecs.components.PhysicsBody;
import org.foxesworld.cge.modules.ecs.components.PlaySound;
import org.foxesworld.cge.modules.ecs.components.Position;
import org.foxesworld.cge.modules.ecs.components.View;

import java.util.Objects;
import java.util.Random;

/**
 * Создает ("спаунит") сущности для "Shape Party".
 * Этот класс отвечает только за создание сущностей с правильным набором компонентов.
 * Вся дальнейшая логика (физика, рендер, звук) обрабатывается системами в ECSModule.
 */
public class ShapePartySpawner {

    private final CalistaGameEngine app;
    private final ECSModule ecsModule;
    private ParticleEmitter particleTemplate;
    private Spatial modelTemplate;

    public ShapePartySpawner(CalistaGameEngine app) {
        this.app = app;
        // Получаем модуль ECS один раз при создании спаунера
        this.ecsModule = app.getModuleManager().getModule(ECSModule.class);
        Objects.requireNonNull(ecsModule, "ECSModule must be registered before creating a spawner.");
    }

    /**
     * Загружает и подготавливает ресурсы-шаблоны (модели, эффекты).
     * Вызывать один раз перед началом спауна.
     */
    public void preloadAssets() {
        // Загрузка модели-шаблона
        modelTemplate = app.getAssetRepo().getModel("ParkBench01");
        if (modelTemplate == null) {
            throw new RuntimeException("Не удалось загрузить модель-шаблон 'ParkBench01'");
        }

        // Создаем шаблон эффекта частиц
        particleTemplate = new ParticleEmitter("particle-template", ParticleMesh.Type.Triangle, 60);
        Material pmMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Particle.j3md");
        pmMat.setTexture("Texture", app.getAssetRepo().getTexture("explosion"));
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
    }

    public void startParty(int count) {
        Random random = new Random();
        float areaRadius = 4f;
        float baseHeight = 2f;

        // enqueue для выполнения в основном потоке JME
        app.enqueue(() -> {
            for (int i = 0; i < count; i++) {
                // --- Шаг 1: Создание визуального представления (Spatial) ---
                Spatial instance = modelTemplate.clone();
                instance.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                instance.setName("PartyBench_" + i);

                ParticleEmitter emitter = particleTemplate.clone();
                emitter.setLocalTranslation(0, -4.5f, 0); // Позиция относительно модели
                ((Node) instance).attachChild(emitter);
                emitter.emitAllParticles();

                // --- Шаг 2: Определение позиции в мире ---
                float x = (random.nextFloat() * 2 - 1) * areaRadius;
                float z = (random.nextFloat() * 2 - 1) * areaRadius;
                float y = baseHeight + random.nextFloat() * 5f;
                Vector3f worldPosition = new Vector3f(x, y, z);

                // --- Шаг 3: Создание сущности с набором компонентов ---
                // Это декларативный подход: мы "описываем" сущность, а не "делаем" что-то с ней.
                /*
                ecsModule.createEntity(
                        new Position(worldPosition),
                        new View(instance),
                        new PhysicsBody(1.0f),
                        new PlaySound("assets/Sounds/bang.ogg", 1.0f)
                ); */
            }
        });
    }
}