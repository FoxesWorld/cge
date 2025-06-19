package org.foxesworld.cge.modules.scene;

import com.jme3.scene.Node;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import org.foxesworld.cge.core.file.extensions.cgs.CGSMetadata;
import org.foxesworld.cge.core.file.extensions.cgs.ChunkEntry;

import java.util.List;

public class SceneEntityContext {

    private final Node sceneRoot;
    private final CGSMetadata metadata;
    private final List<ChunkEntry> chunkEntries;
    private final EntityData entityData;

    public SceneEntityContext(Node sceneRoot, CGSMetadata metadata, List<ChunkEntry> chunkEntries, EntityData entityData) {
        this.sceneRoot = sceneRoot;
        this.metadata = metadata;
        this.chunkEntries = chunkEntries;
        this.entityData = entityData;
    }

    public Node getSceneRoot() {
        return sceneRoot;
    }

    public CGSMetadata getMetadata() {
        return metadata;
    }

    public List<ChunkEntry> getChunkEntries() {
        return chunkEntries;
    }

    public EntityData getEntityData() {
        return entityData;
    }

    /** Создаёт новую сущность в ECS. */
    public EntityId createEntity() {
        return entityData.createEntity();
    }
}
