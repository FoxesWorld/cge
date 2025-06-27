package org.foxesworld.cge.core.loader.texture;

import com.google.gson.reflect.TypeToken;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.extensions.cgtex.CGTEXFile;
import org.foxesworld.cge.core.loader.AbstractAssetLoader;
import org.foxesworld.cge.core.loader.ILoader;
import org.foxesworld.cge.core.io.progressBar.ProgressListener;
import org.foxesworld.cge.core.utils.CallbackLatch;
import org.foxesworld.cge.core.utils.DDSDecoder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Optimized loader for CGTEX textures defined by a JSON list,
 * leveraging asynchronous loading from the parent abstract loader.
 * Implements AssetLoader.ILoader for dynamic loader registration.
 */
public class TextureLoader extends AbstractAssetLoader<TextureEntry> implements ILoader {

    private final CalistaGameEngine engine;

    public TextureLoader(CalistaGameEngine engine) {
        this.engine = engine;
    }

    @Override
    protected String getJsonResourcePath() {
        return "config/data/textures.json";
    }

    @Override
    protected Type getListType() {
        return new TypeToken<List<TextureEntry>>() {}.getType();
    }

    @Override
    protected CompletableFuture<Integer> loadEntryAsync(TextureEntry entry) {
        return CompletableFuture.supplyAsync(() -> processEntry(entry));
    }

    private int processEntry(TextureEntry entry) {
        String path = entry.getPath();
        boolean flipY = entry.isFlipY();
        boolean genMipMaps = entry.isGenerateMipmaps();

        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException("CGTEX file not found: " + path);
        }

        try (CGTEXFile cgtex = new CGTEXFile(file, "r")) {
            cgtex.readFileNew();
            int count = 0;
            for (org.foxesworld.cge.core.file.extensions.cgtex.TextureEntry te : cgtex.getEntries()) {
                BufferedImage img = DDSDecoder.decode(
                        te.getWidth(), te.getHeight(), te.getFormat(), te.getCompressedData()
                );
                ByteBuffer buf = toByteBuffer(img, flipY);
                Image jmeImage = new Image(Image.Format.RGBA8, te.getWidth(), te.getHeight(), buf, ColorSpace.sRGB);
                jmeImage.setMipmapsGenerated(genMipMaps);

                Texture2D tex = new Texture2D(jmeImage);
                tex.setName(te.getName());
                engine.getAssetRepo().addTexture(te.getName(), tex);
                count++;
            }
            return count;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process CGTEX entry: " + path, e);
        }
    }

    /**
     * Converts a BufferedImage (ARGB) to a ByteBuffer (RGBA), optionally flipping vertically.
     *
     * @param img   input image
     * @param flipY whether to flip vertically
     * @return a new, flipped/unflipped RGBA ByteBuffer
     */
    private static ByteBuffer toByteBuffer(BufferedImage img, boolean flipY) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);

        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            int row = flipY ? (h - 1 - y) : y;
            int offset = row * w;
            for (int x = 0; x < w; x++) {
                int argb = pixels[offset + x];
                buf.put((byte) ((argb >> 16) & 0xFF)); // R
                buf.put((byte) ((argb >> 8) & 0xFF));  // G
                buf.put((byte) (argb & 0xFF));         // B
                buf.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        buf.flip();
        return buf;
    }

    // --- ILoader interface implementation for dynamic registration ---

    @Override
    public void setProgressListener(ProgressListener listener) {
        super.setProgressListener(listener);
    }

    @Override
    public void loadWithLatch(CallbackLatch latch) {
        super.loadWithLatch(latch);
    }
}