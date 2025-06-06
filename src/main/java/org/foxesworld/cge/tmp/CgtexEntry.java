package org.foxesworld.cge.tmp;

import com.jme3.asset.TextureKey;

public class CgtexEntry {
    private String path;
    private boolean flipY;
    private boolean generateMipmaps;

    public String getPath() {
        return path;
    }

    public boolean isFlipY() {
        return flipY;
    }

    public boolean isGenerateMipmaps() {
        return generateMipmaps;
    }
}