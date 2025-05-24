package org.foxesworld.cge.core.cgs.parser;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.cgs.SceneChunk;

public interface ChunkParser {
    /**
     * @param calistaGameEngine — для загрузки материалов/моделей
     * @param chunk        — данные чанка
     * @return Spatial-узел, построенный на основании содержимого
     */
    Spatial parse(CalistaGameEngine calistaGameEngine, SceneChunk chunk);
}
