package org.foxesworld.cge.tmp;

import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;
import org.foxesworld.cge.renderer.utils.DDSDecoder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TextureLoader {
    private static final Logger logger = LogManager.getLogger(TextureLoader.class);
    private final CalistaGameEngine engine;

    public TextureLoader(CalistaGameEngine engine) {
        this.engine = engine;
    }

    public void loadCgtex(String path) throws IOException {
        logger.debug("Loading CGTEX: {}", path);
        try (CGTEXFile cgtex = new CGTEXFile(new File(path), "r")) {
            for (TextureEntry entry : cgtex.readFile().getTextures()) {
                String name = entry.getName();
                int fmt = entry.getFormat();
                logger.debug("Processing '{}' format {}", name, fmt);
                BufferedImage img;
                try {
                    img = DDSDecoder.decode(entry.getWidth(), entry.getHeight(), (byte) fmt, entry.getCompressedData());
                } catch (RuntimeException ex) {
                    logger.warn("Skip {}: {}", name, ex.getMessage());
                    continue;
                }
                ByteBuffer buf = toFlippedByteBuffer(img);
                Image jmeImage = new Image(Image.Format.RGBA8, entry.getWidth(), entry.getHeight(), buf, ColorSpace.sRGB);
                Texture2D tex = new Texture2D(jmeImage);
                tex.setName(name);
                engine.addTexture(name, tex);
                logger.debug("Loaded '{}' {}x{}", name, entry.getWidth(), entry.getHeight());
            }
        }
    }

    private ByteBuffer toFlippedByteBuffer(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = w - 1; x >= 0; x--) {
                int argb = pixels[row + x];
                buf.put((byte) ((argb >> 16) & 0xFF))  // R
                        .put((byte) ((argb >> 8) & 0xFF))   // G
                        .put((byte) (argb & 0xFF))          // B
                        .put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        buf.flip();
        return buf;
    }
}