package org.foxesworld.cge.core.file.extensions.cgs.parser.types;

import com.jme3.light.*;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.extensions.cgs.SceneChunk;
import org.foxesworld.cge.core.file.extensions.cgs.parser.ChunkParser;
import org.foxesworld.cge.core.file.extensions.cgs.ChunkFieldTypeConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LightingParser extends ChunkParser {

    private static final Logger logger = LoggerFactory.getLogger(LightingParser.class);

    private enum LightType {
        POINT, DIRECTIONAL, SPOT, SKY
    }

    @Override
    public Spatial parse(CalistaGameEngine engine, SceneChunk chunk, ChunkFieldTypeConfigLoader typeConfigLoader) {
        fieldTypes = typeConfigLoader.getSubTypesForChunkType("LIGHTING");
        logger.debug("Parsing LightingChunk {}", chunk.getId());
        ByteBuffer buf = chunk.getData().order(ByteOrder.BIG_ENDIAN);
        buf.rewind();

        logger.debug("Chunk {} raw size = {} bytes", chunk.getId(), buf.remaining());

        Spatial lightNode = new Node("LightingChunk-" + chunk.getId());
        int index = 0;

        while (buf.remaining() > 0) {
            int posBefore = buf.position();

            if (buf.remaining() < 2) {
                logger.warn("[{}] Not enough data to read type string length at light {}", chunk.getId(), index);
                break;
            }

            String typeString = readUTF(buf).toUpperCase();
            if (typeString.isEmpty()) {
                logger.warn("[{}] Empty or invalid light type at index {}, pos={}, remaining={}, hex={}",
                        chunk.getId(), index, posBefore, buf.remaining(), dumpBufferHex(buf));
                break;
            }

            try {
                LightType type = LightType.valueOf(typeString);
                Light light = switch (type) {
                    case POINT -> parsePoint(chunk.getId(), buf);
                    case DIRECTIONAL -> parseDirectional(chunk.getId(), buf);
                    case SPOT -> parseSpot(chunk.getId(), buf);
                    case SKY -> parseSky(chunk.getId(), buf);
                };
                /* Свет освещает только объекты которые находятся в го пространстве,
                * Иначе мы его не увидим
                * */
                // Добавляем свет в rootNode в главном потоке
                //TODO
                engine.enqueue(() ->{
                    engine.getRootNode().addLight(light);
                });
                //*********************8********************
                //logger.debug("Added light '{}' to {}", light.getName(), engine.getScene().getCgsMetadata().getSceneName());
                lightNode.addLight(light);
                lightNode.setName(light.getName()+"-node");
                index++;
            } catch (IllegalArgumentException ex) {
                logger.warn("[{}] Unknown light type string='{}' at index {}, pos={}, remaining={}, hex={}",
                        chunk.getId(), typeString, index, posBefore, buf.remaining(), dumpBufferHex(buf));
                break;
            }
        }

        logger.info("Finished LightingChunk {}, lights parsed: {}", chunk.getId(), index);
        return lightNode;
    }


    @Override
    protected String getType(ByteBuffer buf) {
        StringBuilder sb = new StringBuilder();
        int startPos = buf.position();
        logger.debug("Starting to read light type at buffer position {}", startPos);

        while (buf.remaining() > 0) {
            buf.mark();
            byte b = buf.get();
            if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z')) {
                sb.append((char) b);
            } else {
                buf.reset();
                logger.debug("Stopped reading light type at non-letter byte: 0x{} (char='{}'), pos={}",
                        Integer.toHexString(b & 0xFF), (char) b, buf.position());
                break;
            }
        }

        String s = sb.toString();
        logger.debug("Read light type string: '{}' (length: {}) at startPos={}, endPos={}, remaining={}",
                s, s.length(), startPos, buf.position(), buf.remaining());
        return s;
    }

    private PointLight parsePoint(int cid, ByteBuffer buf) {
        setFieldDefinitions(fieldTypes.get("POINT"));
        final int REQUIRED_BYTES = 9 * Float.BYTES; // = 24
        if (buf.remaining() < REQUIRED_BYTES) {
            logger.warn("[{}] Not enough data for POINT (pos+intensity+color+radius), remaining={}, hex={}", cid, buf.remaining(), dumpBufferHex(buf));
            buf.position(buf.limit());
            return new PointLight();
        }

        // 1) Позиция
        readFields(buf);
        Vector3f pos = (Vector3f) getFieldValues().get("pos");
        ColorRGBA col = (ColorRGBA) getFieldValues().get("color");
        float radius = Float.parseFloat(String.valueOf(getFieldValues().get("radius")));

        PointLight light = new PointLight(pos, col.mult(col.a), radius);
        light.setName("pointLight-"+cid);
        //light.setIntensity(intensity);
        logger.debug("[{}] POINT pos={} radius={} color={}", cid, pos, radius, col);
        return light;
    }


    private DirectionalLight parseDirectional(int cid, ByteBuffer buf) {
        setFieldDefinitions(fieldTypes.get("DIRECTIONAL"));
        int required = (3 + 4) * Float.BYTES;
        if (buf.remaining() < required) {
            logger.warn("[{}] Not enough data for DIRECTIONAL (dir+color), remaining={}, hex={}",
                    cid, buf.remaining(), dumpBufferHex(buf));
            buf.position(buf.limit());
            return new DirectionalLight();
        }
        readFields(buf);
        Vector3f dir = ((Vector3f) getFieldValues().get("dir")).normalizeLocal();
        float intensity =  Float.parseFloat(String.valueOf(getFieldValues().get("intensity")));
        ColorRGBA col = ((ColorRGBA) getFieldValues().get("color")).clone().multLocal(intensity);
        DirectionalLight l = new DirectionalLight(dir, col.mult(col.a));
        l.setName("directionalLight-"+cid);
        logger.debug("[{}] DIRECTIONAL dir={} color={} intensity={}", cid, dir, col, intensity);
        return l;
    }

    private SpotLight parseSpot(int cid, ByteBuffer buf) {
        setFieldDefinitions(fieldTypes.get("SPOT"));
        readFields(buf);

        Vector3f pos = (Vector3f) getFieldValues().get("pos");
        Vector3f dir = (Vector3f) getFieldValues().get("dir");
        ColorRGBA col = (ColorRGBA) getFieldValues().get("color");
        float radius = Float.parseFloat(String.valueOf(getFieldValues().get("radius")));

        SpotLight l = new SpotLight();
        l.setPosition(pos);
        l.setName("spotLight-"+cid);
        l.setDirection(dir.normalize());
        l.setSpotRange(radius);
        l.setColor(col.mult(col.a));
        logger.debug("[{}] SPOT pos={} dir={} range={} color={}", cid, pos, dir, radius, col);
        return l;
    }


    private AmbientLight parseSky(int cid, ByteBuffer buf) {
        setFieldDefinitions(fieldTypes.get("SKY"));
        int required = Float.BYTES * 5 + Byte.BYTES; // 4 цвета + 1 интенсивность
        if (buf.remaining() < required) {
            logger.warn("[{}] Not enough data for SKY (color + intensity + castShadows), remaining={}, hex={}",
                    cid, buf.remaining(), dumpBufferHex(buf));
            buf.position(buf.limit());
            return new AmbientLight();
        }
        readFields(buf);
        ColorRGBA col = (ColorRGBA) getFieldValues().get("color");
        float intensity =  Float.parseFloat(String.valueOf(getFieldValues().get("intensity")));
        boolean castShadows = (Boolean)    getFieldValues().get("castShadows");
        AmbientLight l = new AmbientLight(col.mult(col.a).mult(intensity));
        l.setName("ambientLight-"+cid);
        l.setEnabled(castShadows);
        logger.debug("[{}] SKY color={} intensity={} castShadows={}", cid, col, intensity, castShadows);
        return l;
    }

    private ColorRGBA readColor(ByteBuffer buf) {
        return new ColorRGBA(buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat());
    }
}
