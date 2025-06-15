package org.foxesworld.cge.core.loader.texture;

/**
 * POJO representing a CGTEX texture entry from JSON configuration.
 */
public class TextureEntry {
    private String path;
    private boolean flipY;
    private boolean generateMipmaps;

    /**
     * Default constructor needed for JSON deserialization.
     */
    public TextureEntry() {
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isFlipY() {
        return flipY;
    }

    public void setFlipY(boolean flipY) {
        this.flipY = flipY;
    }

    public boolean isGenerateMipmaps() {
        return generateMipmaps;
    }

    public void setGenerateMipmaps(boolean generateMipmaps) {
        this.generateMipmaps = generateMipmaps;
    }

    @Override
    public String toString() {
        return "TextureEntry{" +
                "path='" + path + '\'' +
                ", flipY=" + flipY +
                ", generateMipmaps=" + generateMipmaps +
                '}';
    }
}
