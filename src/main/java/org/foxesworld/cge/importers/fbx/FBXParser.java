package org.foxesworld.cge.importers.fbx;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Определяет тип FBX (ASCII/Binary) и вызывает нужный парсер.
 */
public class FBXParser {
    public FBXNode parse(InputStream in) throws IOException {
        PushbackInputStream pushbackIn = new PushbackInputStream(in, 27);
        byte[] header = new byte[27];
        int read = pushbackIn.read(header);
        if (read < 23) {
            throw new IOException("FBX file too short or corrupted");
        }
        String signature = new String(header, 0, 21, StandardCharsets.US_ASCII);
        boolean isBinary = signature.equals("Kaydara FBX Binary  ");
        boolean isAscii = signature.startsWith("Kaydara FBX ASCII");
        pushbackIn.unread(header, 0, read);

        if (isBinary) {
            FBXBinaryParser binaryParser = new FBXBinaryParser();
            return binaryParser.parse(pushbackIn);
        } else if (isAscii) {
            FBXAsciiParser asciiParser = new FBXAsciiParser();
            return asciiParser.parse(pushbackIn);
        } else {
            FBXAsciiParser asciiParser = new FBXAsciiParser();
            return asciiParser.parse(pushbackIn);
        }
    }
}