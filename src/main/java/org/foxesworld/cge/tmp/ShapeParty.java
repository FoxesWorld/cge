package org.foxesworld.cge.tmp;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.physics.PhysicsModule;

import java.util.Random;

/**
 * Класс ShapeParty генерирует случайный набор различных физических тел:
 * кубов, прямоугольных параллелепипедов и сфер.
 * Каждое тело добавляется в сцену с рандомной позицией, размером и физическими свойствами.
 */
public class ShapeParty {

    private final CalistaGameEngine calistaGameEngine;

    /**
     * Конструктор.
     *
     * @param calistaGameEngine экземпляр движка, необходимый для доступа к сцене и модулям физики.
     */
    public ShapeParty(CalistaGameEngine calistaGameEngine) {
        this.calistaGameEngine = calistaGameEngine;
    }

    /**
     * Запускает процесс генерации набора фигур.
     * В общей сложности создаётся 50 объектов с равным шансом:
     * куб, прямоугольник (параллелепипед) или сфера.
     */
    public void startParty() {
        Random random = new Random();
        int count = 50;
        float areaRadius = 8f;
        float baseHeight = 5f;

        calistaGameEngine.enqueue(() -> {
            PhysicsModule physicsModule = calistaGameEngine.getModuleManager().getModule(PhysicsModule.class);

            for (int i = 0; i < count; i++) {
                Geometry geometry;
                float mass;
                String shapeType;

                // Выбор случайной формы: 0 - куб, 1 - параллелепипед, 2 - сфера
                int shapeSelector = random.nextInt(3);

                switch (shapeSelector) {
                    case 0 -> {
                        // Куб
                        float size = 0.5f + random.nextFloat() * 1.5f;
                        Box box = new Box(size / 2, size / 2, size / 2);
                        geometry = new Geometry("Cube" + i, box);
                        mass = size * size * size;
                        shapeType = "Cube";
                    }
                    case 1 -> {
                        // Прямоугольный параллелепипед
                        float width = 0.5f + random.nextFloat() * 1.5f;
                        float height = 0.5f + random.nextFloat() * 1.5f;
                        float depth = 0.5f + random.nextFloat() * 1.5f;
                        Box box = new Box(width / 2, height / 2, depth / 2);
                        geometry = new Geometry("Box" + i, box);
                        mass = width * height * depth;
                        shapeType = "Box";
                    }
                    default -> {
                        // Сфера
                        float radius = 0.5f + random.nextFloat() * 1.5f;
                        Sphere sphere = new Sphere(16, 16, radius);
                        geometry = new Geometry("Sphere" + i, sphere);
                        mass = (4f / 3f) * FastMath.PI * FastMath.pow(radius, 3);
                        shapeType = "Sphere";
                    }
                }

                // Материал с текстурой (предположительно для всех форм одна текстура)
                Material material = new Material(
                        calistaGameEngine.getAssetManager(),
                        "Common/MatDefs/Light/Lighting.j3md"
                );
                material.setTexture("DiffuseMap", calistaGameEngine.getTexture("box"));
                material.setTexture("NormalMap", calistaGameEngine.getTexture("box_normal"));
                material.setBoolean("UseMaterialColors", false);
                material.setFloat("Shininess", 4f);
                material.getTextureParam("DiffuseMap").getTextureValue().setWrap(Texture.WrapMode.Repeat);
                geometry.setMaterial(material);
                geometry.getMesh().scaleTextureCoordinates(new Vector2f(2,2));

                // Случайная позиция в области
                float x = (random.nextFloat() * 2 - 1) * areaRadius;
                float z = (random.nextFloat() * 2 - 1) * areaRadius;
                float y = baseHeight + random.nextFloat() * 5f;
                geometry.setLocalTranslation(x, y, z);

                // Добавляем в сцену
                calistaGameEngine.getRootNode().attachChild(geometry);

                // Добавляем физику, если модуль физики активен
                if (physicsModule != null) {
                    physicsModule.addRigidBody(geometry, mass);
                }

                System.out.printf("Created %s[%d] at (%.2f, %.2f, %.2f) with mass=%.2f%n",
                        shapeType, i, x, y, z, mass);
            }
        });
    }
}
