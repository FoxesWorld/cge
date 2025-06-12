package org.foxesworld.cge.tmp;

/**
 * POJO representing a CGTEX texture entry from JSON configuration.
 */
public class CgtexEntry {
    private String path;
    private boolean flipY;
    private boolean generateMipmaps;

    /**
     * Default constructor needed for JSON deserialization.
     */
    public CgtexEntry() {
    }

    /**
     * Construct with all fields.
     *
     * @param path             filesystem path to the .cgtex file
     * @param flipY            whether to vertically flip the texture
     * @param generateMipmaps  whether to generate mipmaps for the texture
     */
    public CgtexEntry(String path, boolean flipY, boolean generateMipmaps) {
        this.path = path;
        this.flipY = flipY;
        this.generateMipmaps = generateMipmaps;
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
        return "CgtexEntry{" +
                "path='" + path + '\'' +
                ", flipY=" + flipY +
                ", generateMipmaps=" + generateMipmaps +
                '}';
    }
}
