package org.foxesworld.cge.core.file.cgtex;

/**
 * Simple POJO для заголовка CGTEX файла.
 */
public class CGTEXHeader {
    private final String magic;       // Обычно "CGTX"
    private final int version;        // Версия формата
    private final int textureCount;   // Количество текстур в файле

    public CGTEXHeader(String magic, int version, int textureCount) {
        this.magic = magic;
        this.version = version;
        this.textureCount = textureCount;
    }

    public String getMagic() {
        return magic;
    }

    public int getVersion() {
        return version;
    }

    public int getTextureCount() {
        return textureCount;
    }

    @Override
    public String toString() {
        return "CGTEXHeader{" +
                "magic='" + magic + '\'' +
                ", version=" + version +
                ", textureCount=" + textureCount +
                '}';
    }
}
