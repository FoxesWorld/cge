package org.foxesworld.cge.importers.fbx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FBXBinaryParser {
    public FBXNode parse(InputStream in) throws IOException {
        byte[] data = in.readAllBytes();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(27); // skip header
        int version = buf.getInt();
        FBXNode root = new FBXNode("Root");
        parseNodes(buf, root, version);
        return root;
    }

    private void parseNodes(ByteBuffer buf, FBXNode parent, int version) {
        while (hasMoreNodes(buf, version)) {
            FBXNode node = parseNode(buf, version);
            if (node == null) break;
            parent.addChild(node);
        }
    }

    private boolean hasMoreNodes(ByteBuffer buf, int version) {
        int endOffset = (version >= 7500) ? 25 : 13;
        if (buf.remaining() < endOffset) return false;
        for (int i = 0; i < endOffset; i++) {
            if (buf.get(buf.position() + i) != 0) return true;
        }
        return false;
    }

    private FBXNode parseNode(ByteBuffer buf, int version) {
        int endOffsetLen = (version >= 7500) ? 8 : 4;
        int propCountLen = (version >= 7500) ? 8 : 4;
        int propListLenLen = (version >= 7500) ? 8 : 4;

        long endOffset = readUnsignedInt(buf, endOffsetLen);
        long numProperties = readUnsignedInt(buf, propCountLen);
        long propertyListLen = readUnsignedInt(buf, propListLenLen);

        int nameLen = Byte.toUnsignedInt(buf.get());
        byte[] nameBytes = new byte[nameLen];
        buf.get(nameBytes);
        String name = new String(nameBytes);

        FBXNode node = new FBXNode(name);

        // Пропустить свойства (этот MVP не парсит их)
        buf.position((int)(buf.position() + propertyListLen));

        // Рекурсивно парсим дочерние узлы
        while (buf.position() < endOffset && hasMoreNodes(buf, version)) {
            FBXNode child = parseNode(buf, version);
            if (child == null) break;
            node.addChild(child);
        }
        if (buf.position() < endOffset) {
            buf.position((int)endOffset);
        }
        return node;
    }

    private long readUnsignedInt(ByteBuffer buf, int bytes) {
        if (bytes == 4) {
            return Integer.toUnsignedLong(buf.getInt());
        } else if (bytes == 8) {
            return buf.getLong();
        }
        throw new IllegalArgumentException("Unsupported int size: " + bytes);
    }
}