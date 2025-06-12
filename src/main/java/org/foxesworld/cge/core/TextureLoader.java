package org.foxesworld.cge.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.extensions.cgtex.CGTEXFile;
import org.foxesworld.cge.core.file.extensions.cgtex.TextureEntry;
import org.foxesworld.cge.tmp.CgtexEntry;
import org.foxesworld.cge.core.utils.DDSDecoder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance loader for CGTEX textures.
 * Provides both synchronous and asynchronous loading,
 * optimized ByteBuffer creation, and safe logging.
 */
public class TextureLoader {
    private static final Logger logger = LogManager.getLogger(TextureLoader.class);
    private final CalistaGameEngine engine;

    public TextureLoader(CalistaGameEngine engine) {
        this.engine = engine;
    }

    /**
     * Loads all textures from a CGTEX file synchronously.
     * Each texture is added to the engine via {@link CalistaGameEngine#getAssetRepo()} addTexture(String, Texture)}.
     *
     * @param path path to the .cgtex file
     * @throws IOException if reading or decoding fails
     */
    public void loadCgtex(String path, boolean flipY) throws IOException {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new IOException("CGTEX file not found: " + path);
        }

        logger.debug("Loading CGTEX file: {}", path);
        CGTEXFile cgtex = new CGTEXFile(file, "r");
        cgtex.readFileNew();
        for (TextureEntry entry : cgtex.getEntries()) {
            String name = entry.getName();
            int width  = entry.getWidth();
            int height = entry.getHeight();
            int fmt    = entry.getFormat();

            logger.debug("Decoding texture '{}' ({}x{}, format {})", name, width, height, fmt);

            BufferedImage img;
            try {
                img = DDSDecoder.decode(width, height, (byte) fmt, entry.getCompressedData());
            } catch (Exception ex) {
                logger.warn("Skipping texture '{}' due to decode error: {}", name, ex.getMessage());
                continue;
            }

            ByteBuffer buf = toByteBuffer(img, flipY);
            Image jmeImage = new Image(Image.Format.RGBA8, width, height, buf, ColorSpace.sRGB);
            Texture2D tex = new Texture2D(jmeImage);
            tex.setName(name);
            engine.getAssetRepo().addTexture(name, tex);
            logger.debug("Loaded texture '{}' ({}x{}) into engine", name, width, height);
        }
    }

    public CompletableFuture<Void> loadCgtexAsync(InputStream jsonInputStream) {
        return CompletableFuture.runAsync(() -> {
            try (InputStreamReader reader = new InputStreamReader(jsonInputStream)) {
                Type listType = new TypeToken<List<CgtexEntry>>() {}.getType();
                List<CgtexEntry> entries = new Gson().fromJson(reader, listType);
                for (CgtexEntry entry : entries) {
                    loadCgtex(entry.getPath(), entry.isFlipY());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load texture JSON", e);
            }
        });
    }


    /**
     * Converts a {@link BufferedImage} to a {@link ByteBuffer} in RGBA format.
     *
     * @param img   the input image
     * @param flipY whether to vertically flip the image
     * @return the image data as a direct RGBA ByteBuffer
     */
    public static ByteBuffer toByteBuffer(BufferedImage img, boolean flipY) {
        int width = img.getWidth();
        int height = img.getHeight();

        int[] pixels = new int[width * height];
        img.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);

        for (int y = 0; y < height; y++) {
            int row = flipY ? (height - 1 - y) : y;
            int rowStart = row * width;

            for (int x = 0; x < width; x++) {
                int argb = pixels[rowStart + x];

                byte r = (byte) ((argb >> 16) & 0xFF);
                byte g = (byte) ((argb >> 8) & 0xFF);
                byte b = (byte) (argb & 0xFF);
                byte a = (byte) ((argb >> 24) & 0xFF);

                buffer.put(r).put(g).put(b).put(a);
            }
        }

        buffer.flip(); // prepare for reading
        return buffer;
    }
}


