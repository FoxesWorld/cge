package org.foxesworld.cge.tmp;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.physics.PhysicsModule;

import java.util.Random;

public class CubeDerp {

    CalistaGameEngine calistaGameEngine;
    public CubeDerp(CalistaGameEngine calistaGameEngine){
        this.calistaGameEngine = calistaGameEngine;
    }

    public void startParty(){
        Random rnd = new Random();
        int count = 50;          // сколько кубов
        float areaRadius = 8f;   // радиус разброса по XZ
        float minSize = 0.5f;    // минимальный размер ребра
        float maxSize = 2.0f;    // максимальный размер
        float baseHeight = 5f;   // минимальная высота над полом
        calistaGameEngine.enqueue(() -> {
            PhysicsModule phys = calistaGameEngine.getModuleManager().getModule(PhysicsModule.class);
            for (int i = 0; i < count; i++) {
                // 1) Размер
                float size = minSize + rnd.nextFloat() * (maxSize - minSize);

                Box boxMesh = new Box(size/2, size/2, size/2);
                Geometry cube = new Geometry("Cube"+i, boxMesh);

                Material mat = new Material(calistaGameEngine.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");

                mat.setTexture("DiffuseMap", calistaGameEngine.getTextureMap().get("box"));
                mat.setTexture("NormalMap", calistaGameEngine.getTextureMap().get("box_normal"));
                mat.setBoolean("UseMaterialColors", false);
                mat.setFloat   ("Shininess", 4f);
                cube.setMaterial(mat);
                // 3) Позиция
                float x = (rnd.nextFloat()*2 - 1) * areaRadius;
                float z = (rnd.nextFloat()*2 - 1) * areaRadius;
                float y = baseHeight + rnd.nextFloat()*5f; // чуть выше, чтобы падали
                cube.setLocalTranslation(x, y, z);


                // 4) Прикрепляем к сцене
                calistaGameEngine.getRootNode().attachChild(cube);

                // 5) Физика: масса пропорциональна объёму
                if (phys != null) {
                    float volume = size*size*size;
                    float mass = volume; // коэффициент можно скорректировать
                    phys.addRigidBody(cube, mass);
                }
            }
        });
    }
}
