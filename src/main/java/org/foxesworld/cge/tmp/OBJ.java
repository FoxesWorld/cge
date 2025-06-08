package org.foxesworld.cge.tmp;

import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.physics.PhysicsModule;

public class OBJ {

    private final CalistaGameEngine engine;
    private Spatial model;
    private  RigidBodyControl objectControl;
    private CollisionShape objectShape;

    public OBJ(CalistaGameEngine engine){
        this.engine = engine;
    }

    public OBJ getModel(String path, float scale){
        model = this.engine.getAssetManager().loadModel(path);
        model.scale(scale);

        objectShape = CollisionShapeFactory.createMeshShape(model);
        objectControl = new RigidBodyControl(objectShape, model.getTriangleCount());
        model.addControl(objectControl);

        this.engine.getModuleManager().getModule(PhysicsModule.class).getBulletAppState().getPhysicsSpace().add(objectControl);
        return this;
    }

    public Spatial getModel() {
        return model;
    }

    public RigidBodyControl getObjectControl() {
        return objectControl;
    }

    public CollisionShape getObjectShape() {
        return objectShape;
    }
}