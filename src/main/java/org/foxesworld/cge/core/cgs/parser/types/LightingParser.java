package org.foxesworld.cge.core.cgs.parser.types;

import com.jme3.light.*;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.foxesworld.cge.core.cgs.parser.ChunkParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.Map;

public class LightingParser extends ChunkParser {

    private static final Logger logger = LoggerFactory.getLogger(LightingParser.class);
    private static final HexFormat HEX = HexFormat.of();

    private enum LightType {
        POINT, DIRECTIONAL, SPOT, SKY
    }

    @Override
    public Spatial parse(CalistaGameEngine engine, SceneChunk chunk, Map<String, String> args) {
        logger.debug("Parsing LightingChunk {} with args: {}", chunk.getId(), args);
        setFieldDefinitions(args);
        ByteBuffer buf = chunk.getData().order(ByteOrder.BIG_ENDIAN);
        buf.rewind();

        logger.debug("Chunk {} raw size = {} bytes", chunk.getId(), buf.remaining());

        if (buf.remaining() < Integer.BYTES) {
            logger.warn("[{}] Not enough data for light count", chunk.getId());
            return new Node("LightingChunk-" + chunk.getId());
        }

        int count = buf.getInt();
        logger.debug("Parsing LightingChunk {}: {} lights", chunk.getId(), count);

        Node lightNode = new Node("LightingChunk-" + chunk.getId());

        for (int i = 0; i < count; i++) {
            if (buf.remaining() < 3) {
                logger.warn("[{}] Unexpected end before light type at light {}", chunk.getId(), i);
                break;
            }

            int posBefore = buf.position();
            String typeString = readLightType(buf).toUpperCase();

            try {
                LightType type = LightType.valueOf(typeString);
                switch (type) {
                    case POINT -> parsePoint(chunk.getId(), buf, lightNode);
                    case DIRECTIONAL -> parseDirectional(chunk.getId(), buf, lightNode);
                    case SPOT -> parseSpot(chunk.getId(), buf, lightNode);
                    case SKY -> parseSky(chunk.getId(), buf, lightNode);
                }
            } catch (IllegalArgumentException ex) {
                logger.warn("[{}] Unknown light type string={} at index {}, pos={}, remaining={}, hex={} ",
                        chunk.getId(), typeString, i, posBefore, buf.remaining(), dumpBufferHex(buf));
                break;
            }
        }

        logger.info("Finished LightingChunk {}", chunk.getId());
        return lightNode;
    }

    private String readLightType(ByteBuffer buf) {
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

    private void parsePoint(int cid, ByteBuffer buf, Node parent) {
        int required = (3 + 4 + 1) * Float.BYTES;
        if (buf.remaining() < required) {
            logger.warn("[{}] Not enough data for POINT (pos+color+radius), remaining={}, hex={}",
                    cid, buf.remaining(), dumpBufferHex(buf));
            buf.position(buf.limit());
            return;
        }
        Vector3f pos = new Vector3f(buf.getFloat(), buf.getFloat(), buf.getFloat());
        ColorRGBA col = readColor(buf);
        float radius = buf.getFloat();
        PointLight l = new PointLight(pos, col.mult(col.a), radius);
        parent.addLight(l);
        logger.debug("[{}] POINT pos={} radius={} color={}", cid, pos, radius, col);
    }

    private void parseDirectional(int cid, ByteBuffer buf, Node parent) {
        int required = (3 + 4) * Float.BYTES;
        if (buf.remaining() < required) {
            logger.warn("[{}] Not enough data for DIRECTIONAL (dir+color), remaining={}, hex={}",
                    cid, buf.remaining(), dumpBufferHex(buf));
            buf.position(buf.limit());
            return;
        }
        Vector3f dir = new Vector3f(buf.getFloat(), buf.getFloat(), buf.getFloat()).normalizeLocal();
        ColorRGBA col = readColor(buf);
        DirectionalLight l = new DirectionalLight(dir, col.mult(col.a));
        parent.addLight(l);
        logger.debug("[{}] DIRECTIONAL dir={} color={}", cid, dir, col);
    }

    private void parseSpot(int cid, ByteBuffer buf, Node parent) {
        int required = (3 + 4 + 1) * Float.BYTES;
        if (buf.remaining() < required) {
            logger.warn("[{}] Not enough data for SPOT (pos+color+radius), remaining={}, hex={}",
                    cid, buf.remaining(), dumpBufferHex(buf));
            buf.position(buf.limit());
            return;
        }
        Vector3f pos = new Vector3f(buf.getFloat(), buf.getFloat(), buf.getFloat());
        ColorRGBA col = readColor(buf);
        float radius = buf.getFloat();
        SpotLight l = new SpotLight();
        l.setPosition(pos);
        l.setDirection(new Vector3f(0, -1, 0));
        l.setSpotRange(radius);
        l.setColor(col.mult(col.a));
        parent.addLight(l);
        logger.debug("[{}] SPOT pos={} range={} color={}", cid, pos, radius, col);
    }

    private void parseSky(int cid, ByteBuffer buf, Node parent) {
        int required = Float.BYTES * 4 + Integer.BYTES + Byte.BYTES;
        if (buf.remaining() < required) {
            logger.warn("[{}] Not enough data for SKY (color + intensity + castShadows), remaining={}, hex={}",
                    cid, buf.remaining(), dumpBufferHex(buf));
            buf.position(buf.limit());
            return;
        }
        readFields(buf);
        System.out.println(getFieldValues());
        ColorRGBA col = (ColorRGBA) getFieldValues().get("color");
        float intensity =  Float.parseFloat(String.valueOf(getFieldValues().get("intensity")));
        boolean castShadows = (Boolean)    getFieldValues().get("castShadows");
        AmbientLight l = new AmbientLight(col.mult(col.a).mult(intensity));
        l.setEnabled(castShadows);
        parent.addLight(l);
        logger.debug("[{}] SKY color={} intensity={} castShadows={}", cid, col, intensity, castShadows);
    }

    private ColorRGBA readColor(ByteBuffer buf) {
        return new ColorRGBA(buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat());
    }

    private String dumpBufferHex(ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.slice().get(bytes);
        return HEX.formatHex(bytes);
    }
}
