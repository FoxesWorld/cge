package org.foxesworld.cge.tmp;

import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;
import org.foxesworld.cge.core.file.cgtex.reader.CGTEXFileReader;
import org.foxesworld.cge.tools.CGTEXcreator.preview.DDSDecoder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class TextureLoader {

    private static final Logger logger = LogManager.getLogger(TextureLoader.class);
    private final CalistaGameEngine calistaGameEngine;

    public TextureLoader(CalistaGameEngine calistaGameEngine) {
        this.calistaGameEngine = calistaGameEngine;
    }

    public void loadCgtex(String path) {
        CGTEXFileReader cgtexFile;
        try {
            logger.info("Starting to load CGTEX file: " + path);
            cgtexFile = new CGTEXFileReader(new File(path));

            for (TextureEntry textureEntry : cgtexFile.getTextures()) {
                String textureName = textureEntry.getName();
                logger.debug("Loading texture: " + textureName);

                int format = textureEntry.getFormat();
                logger.debug("Texture " + textureName + " format: " + getFormatDescription(format));

                if (format == 1) {
                    logger.debug("Texture " + textureName + " is in DXT1 format.");
                    loadDXT1Texture(textureEntry);
                } else {
                    logger.warn("Texture " + textureName + " is not in DXT1 format, skipping.");
                }
            }

            logger.debug("CGTEX file loaded successfully: " + path);
        } catch (IOException e) {
            logger.error("Error loading CGTEX file: " + path + ": " + e.getMessage());
            throw new RuntimeException("Error loading CGTEX file: " + path, e);
        }
    }

    private String getFormatDescription(int formatCode) {
        return switch (formatCode) {
            case 1 -> "DXT1";
            case 5 -> "DXT5";
            default -> "Unknown Format (" + formatCode + ")";
        };
    }

    private void loadDXT1Texture(TextureEntry textureEntry) {
        try (InputStream textureStream = new ByteArrayInputStream(textureEntry.getCompressedData())) {
            logger.debug("Decoding DXT1 texture: " + textureEntry.getName());
            BufferedImage image = flipImageHorizontally(DDSDecoder.decode(textureEntry.getWidth(), textureEntry.getHeight(), textureEntry.getFormat(), textureEntry.getCompressedData()));// decodeDXT1(textureStream, textureEntry);

            ByteBuffer byteBuffer = convertBufferedImageToByteBuffer(image);
            Image textureImage = new Image(Image.Format.RGBA8, textureEntry.getWidth(), textureEntry.getHeight(), byteBuffer, ColorSpace.sRGB);
            Texture2D texture2D = new Texture2D(textureImage);
            texture2D.setName(textureEntry.getName());
            this.calistaGameEngine.addTexture(textureEntry.getName(), texture2D);
            logger.debug("Successfully loaded DXT1 texture: " + textureEntry.getName());
        } catch (IOException e) {
            logger.error("Error loading DXT1 texture data for " + textureEntry.getName() + ": " + e.getMessage());
        }
    }

    private BufferedImage flipImageHorizontally(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage flippedImage = new BufferedImage(width, height, image.getType());

        // Переворачиваем каждую строку по горизонтали
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flippedImage.setRGB(width - x - 1, y, image.getRGB(x, y));
            }
        }

        return flippedImage;
    }

    private ByteBuffer convertBufferedImageToByteBuffer(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        int[] pixels = new int[width * height];
        bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(width * height * 4);
        for (int pixel : pixels) {
            byteBuffer.putInt(pixel);
        }
        byteBuffer.flip();
        return byteBuffer;
    }
}
