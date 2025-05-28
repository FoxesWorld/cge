package org.foxesworld.cge.tools.CGTEXcreator.preview;

import org.foxesworld.cge.tools.CGTEXcreator.TextureInfo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DDSParser {

    /**
     * Читает из .dds файл размеры, FourCC и возвращает TextureInfo (без декодирования).
     */
    public static TextureInfo parse(File f) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] hdr = new byte[128];
            raf.readFully(hdr);
            if (hdr[0]!='D'||hdr[1]!='D'||hdr[2]!='S'||hdr[3]!=' ')
                throw new IOException("Not a DDS file: " + f.getName());

            ByteBuffer buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
            buf.position(12);
            int height = buf.getInt();
            int width  = buf.getInt();
            buf.position(84);
            byte[] four = new byte[4];
            buf.get(four);
            String code = new String(four, "UTF-8");

            byte fmt;
            switch (code) {
                case "DXT1": fmt = 1; break;
                case "DXT3": fmt = 3; break;
                case "DXT5": fmt = 5; break;
                default: throw new IOException("Unsupported DDS format: " + code);
            }

            long remaining = raf.length() - 128;
            byte[] data = new byte[(int)remaining];
            raf.readFully(data);

            return new TextureInfo(f, width, height, fmt, data);
        }
    }
}
