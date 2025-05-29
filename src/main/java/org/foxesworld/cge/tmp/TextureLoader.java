package org.foxesworld.cge.tmp;

import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.cgtex.CGTEXFile;
import org.foxesworld.cge.core.file.cgtex.TextureEntry;
import org.foxesworld.cge.tools.CGTEXcreator.preview.DDSDecoder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TextureLoader {

    private static final Logger logger = LogManager.getLogger(TextureLoader.class);
    private final CalistaGameEngine calistaGameEngine;

    public TextureLoader(CalistaGameEngine calistaGameEngine) {
        this.calistaGameEngine = calistaGameEngine;
    }

    public void loadCgtex(String path) {
        CGTEXFile cgtexFile;
        logger.debug("Starting to load CGTEX file: " + path);
        cgtexFile = new CGTEXFile(new File(path), "r");

        for (TextureEntry textureEntry : cgtexFile.readFile().getTextures()) {
            String textureName = textureEntry.getName();
            logger.debug("Loading texture: " + textureName);

            int format = textureEntry.getFormat();
            logger.debug("Texture " + textureName + " format: " + getFormatDescription(format));

            switch (format) {
                case 1: {
                    logger.debug("Texture " + textureName + " is in DXT1 format.");
                    loadDXT1Texture(textureEntry);
                }

                case 5: {
                    logger.debug("Texture " + textureName + " is in DXT5 format.");
                    loadDXT5Texture(textureEntry);
                }
                default:
                logger.warn("Texture " + textureName + " is an unsupported format {} skipping.", format);
            }
        }
        logger.debug("CGTEX file loaded successfully: " + path);
        try {
            cgtexFile.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
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
            logger.debug("Decoding DXT1 texture: " + textureEntry.getName());
            BufferedImage image = flipImageHorizontally(DDSDecoder.decode(textureEntry.getWidth(), textureEntry.getHeight(), textureEntry.getFormat(), textureEntry.getCompressedData()));// decodeDXT1(textureStream, textureEntry);

            ByteBuffer byteBuffer = convertBufferedImageToByteBuffer(image);
            Image textureImage = new Image(Image.Format.RGBA8, textureEntry.getWidth(), textureEntry.getHeight(), byteBuffer, ColorSpace.sRGB);
            Texture2D texture2D = new Texture2D(textureImage);
            texture2D.setName(textureEntry.getName());
            this.calistaGameEngine.addTexture(textureEntry.getName(), texture2D);
            logger.debug("Successfully loaded DXT1 texture: " + textureEntry.getName());

    }
    private void loadDXT5Texture(TextureEntry textureEntry) {
            logger.debug("Decoding DXT5 texture: " + textureEntry.getName());
            BufferedImage image = flipImageHorizontally(DDSDecoder.decode(textureEntry.getWidth(), textureEntry.getHeight(), textureEntry.getFormat(), textureEntry.getCompressedData()));// decodeDXT1(textureStream, textureEntry);


            ByteBuffer byteBuffer = convertBufferedImageToByteBuffer(image);
            Image textureImage = new Image(Image.Format.RGBA8, textureEntry.getWidth(), textureEntry.getHeight(), byteBuffer, ColorSpace.sRGB);
            Texture2D texture2D = new Texture2D(textureImage);
            texture2D.setName(textureEntry.getName());
            this.calistaGameEngine.addTexture(textureEntry.getName(), texture2D);
            logger.debug("Successfully loaded DXT1 texture: " + textureEntry.getName());

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
