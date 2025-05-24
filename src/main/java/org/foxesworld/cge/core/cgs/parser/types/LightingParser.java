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
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class LightingParser implements ChunkParser {

    private static final Logger logger = LoggerFactory.getLogger(LightingParser.class);
    private static final HexFormat HEX = HexFormat.of();

    private enum LightType {
        POINT, DIRECTIONAL, SPOT, SKY;

        // Метод для получения LightType по строковому значению
        public static LightType fromString(String type) {
            try {
                return LightType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown light type: " + type);
            }
        }
    }

    @Override
    public Spatial parse(CalistaGameEngine engine, SceneChunk chunk) {
        ByteBuffer buf = chunk.getData();
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
            if (buf.remaining() < 1) {
                logger.warn("[{}] Unexpected end before type byte at light {}", chunk.getId(), i);
                break;
            }

            int posBefore = buf.position();
            String typeString = readLightType(buf);  // Читаем строку типа света

            try {
                LightType type = LightType.fromString(typeString);  // Преобразуем строку в LightType
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

    // Метод для извлечения строки типа света из буфера
    private String readLightType(ByteBuffer buf) {
        // Допустим, строка будет длиной не более 4 символов для типа
        int maxLength = 4;  // Максимальная длина строки типа света (например, "Sky")

        if (buf.remaining() < maxLength) {
            logger.warn("Not enough data to read light type, remaining={}", buf.remaining());
            return "";  // Возвращаем пустую строку, если данных недостаточно
        }

        byte[] stringBytes = new byte[maxLength];
        buf.get(stringBytes);  // Читаем байты

        // Преобразуем байты в строку и обрезаем лишние символы
        String typeString = new String(stringBytes, StandardCharsets.UTF_8).trim();

        logger.debug("Read light type string: {}", typeString);
        return typeString;
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
        int required = 4 * Float.BYTES;
        if (buf.remaining() < required) {
            logger.warn("[{}] Not enough data for SKY (color), remaining={}, hex={}",
                    cid, buf.remaining(), dumpBufferHex(buf));
            buf.position(buf.limit());
            return;
        }

        ColorRGBA col = readColor(buf);
        AmbientLight l = new AmbientLight(col.mult(col.a));
        parent.addLight(l);

        logger.debug("[{}] SKY color={}", cid, col);
    }

    private ColorRGBA readColor(ByteBuffer buf) {
        return new ColorRGBA(buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat());
    }

    private String dumpBufferHex(ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.slice().get(bytes); // не меняет позицию оригинального буфера
        return HEX.formatHex(bytes);
    }
}
