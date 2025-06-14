package org.foxesworld.cge.core.loader;

import com.google.gson.reflect.TypeToken;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.file.extensions.cgtex.CGTEXFile;
import org.foxesworld.cge.core.file.extensions.cgtex.TextureEntry;
import org.foxesworld.cge.tmp.CgtexEntry;
import org.foxesworld.cge.core.utils.DDSDecoder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Concrete loader for CGTEX textures defined by JSON list.
 */
public class TextureLoader extends AbstractAssetLoader<CgtexEntry> {
    private final CalistaGameEngine engine;

    public TextureLoader(CalistaGameEngine engine) {
        this.engine = engine;
    }

    @Override
    protected String getJsonResourcePath() {
        return "textures.json";
    }

    @Override
    protected Type getListType() {
        return new TypeToken<List<CgtexEntry>>() {}.getType();
    }

    @Override
    protected CompletableFuture<Integer> loadEntryAsync(CgtexEntry entry) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        return CompletableFuture.supplyAsync(() -> {
            try {
                String path = entry.getPath();
                boolean flipY = entry.isFlipY();
                boolean genMipMaps = entry.isGenerateMipmaps();
                File file = new File(path);
                if (!file.exists()) {
                    throw new IllegalArgumentException("CGTEX file not found: " + path);
                }
                CGTEXFile cgtex = new CGTEXFile(file, "r");
                cgtex.readFileNew();
                int count = 0;
                for (TextureEntry te : cgtex.getEntries()) {
                    BufferedImage img = DDSDecoder.decode(
                            te.getWidth(), te.getHeight(), te.getFormat(), te.getCompressedData()
                    );
                    ByteBuffer buf = toByteBuffer(img, flipY);
                    Image jmeImage = new Image(Image.Format.RGBA8, te.getWidth(), te.getHeight(), buf, ColorSpace.sRGB);
                    Texture2D tex = new Texture2D(jmeImage);
                    tex.setName(te.getName());
                    engine.getAssetRepo().addTexture(te.getName(), tex);
                    count++;
                }
                return count;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor).whenComplete((res, ex) -> {
            executor.shutdown();
        });
    }

    private static ByteBuffer toByteBuffer(BufferedImage img, boolean flipY) {
        int w = img.getWidth(), h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            int row = flipY ? h - 1 - y : y;
            int off = row * w;
            for (int x = 0; x < w; x++) {
                int argb = pixels[off + x];
                buf.put((byte) ((argb >> 16) & 0xFF))
                        .put((byte) ((argb >> 8) & 0xFF))
                        .put((byte) (argb & 0xFF))
                        .put((byte) ((argb >> 24) & 0xFF));
            }
        }
        buf.flip();
        return buf;
    }
}