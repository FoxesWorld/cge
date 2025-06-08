package org.foxesworld.cge.tmp;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.physics.PhysicsModule;

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

        calistaGameEngine.enqueue(() -> {
            PhysicsModule physicsModule = calistaGameEngine.getModuleManager().getModule(PhysicsModule.class);
            AssetManager assetManager = calistaGameEngine.getAssetManager();

            // Загрузка модели вместо создания случайных фигур
            Spatial model = assetManager.loadModel("meshes/furniture/bench/ParkBench01.obj");

            // Убедимся, что модель является Geometry или Node
            if (model == null) {
                throw new RuntimeException("Не удалось загрузить модель");
            }

            for (int i = 0; i < count; i++) {
                // Создаем клон загруженной модели
                Spatial instance = model.clone();
                instance.setName("ModelInstance_" + i);

                scaleTextureCoordinates(instance, new Vector2f(2, 2));

                // Случайная позиция
                float x = (random.nextFloat() * 2 - 1) * areaRadius;
                float z = (random.nextFloat() * 2 - 1) * areaRadius;
                float y = baseHeight + random.nextFloat() * 5f;
                instance.setLocalTranslation(x, y, z);

                // Прикрепляем к сцене
                calistaGameEngine.getRootNode().attachChild(instance);

                // Добавляем физику для всех Geometry в иерархии
                if (physicsModule != null && instance instanceof Geometry) {
                    physicsModule.addRigidBody((Geometry) instance, 1.0f);
                } else if (physicsModule != null && instance instanceof Node) {
                    processNodePhysics((Node) instance, physicsModule);
                }

                //System.out.printf("Created ModelInstance_%d at (%.2f, %.2f, %.2f)%n", i, x, y, z);
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