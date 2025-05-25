package org.foxesworld.cge.core.cgs.parser.types;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.foxesworld.cge.core.cgs.parser.ChunkParser;
import org.foxesworld.cge.physics.PhysicsModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Парсер физических чанков сцены.
 */
public class PhysicsParser implements ChunkParser {

    private static final Logger logger = LoggerFactory.getLogger(PhysicsParser.class);

    @Override
    public Spatial parse(CalistaGameEngine calistaGameEngine, SceneChunk chunk) {

        Node spatial = new Node("PhysicsChunk-" + chunk.getId());
        RigidBodyControl body = new RigidBodyControl(1.0f); // Масса 1.0

        spatial.addControl(body);

        PhysicsModule physicsModule = calistaGameEngine.getModuleManager().getModule(PhysicsModule.class);
        if (physicsModule != null && physicsModule.getBulletAppState() != null) {
            physicsModule.getBulletAppState().getPhysicsSpace().add(body);
            logger.info("Physics body from chunk {} added to BulletAppState", chunk.getId());
        } else {
            logger.warn("PhysicsModule or BulletAppState not available for chunk {}", chunk.getId());
        }

        return spatial;
    }
}
