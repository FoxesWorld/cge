package org.foxesworld.cge.core.file.extensions.cgs.parser.types;
/*
import com.jme3.asset.AssetRepo;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.foxesworld.cge.core.cgs.parser.ChunkParser;

import java.nio.ByteBuffer;


public class GeometryParser implements ChunkParser {
    @Override
    public Spatial parse(CalistaGameEngine assetManager, SceneChunk chunk) {
        ByteBuffer buf = chunk.getData();
        buf.rewind();

        int count = buf.getInt();
        Node parent = new Node("GeometryChunk-" + chunk.getId());

        for (int i = 0; i < count; i++) {
            // считываем размеры box-примитива
            float x = buf.getFloat();
            float y = buf.getFloat();
            float z = buf.getFloat();

            // считываем имя материала
            int nameLen = buf.getInt();
            byte[] nameBytes = new byte[nameLen];
            buf.get(nameBytes);
            String matName = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);

            // создаём простейший Box, на который позже можно навесить материал:
            Mesh mesh = new Box(x/2, y/2, z/2);
            Geometry geom = new Geometry("Geom-" + i, mesh);
            // TODO: geom.setMaterial(assetManager.loadMaterial(matName));

            parent.attachChild(geom);
        }

        return parent;
    }
}
 */
