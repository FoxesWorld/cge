package org.foxesworld.cge.core.streaming;

public interface IStreamableAsset {
    void streamIn();   // Загружается в память
    void streamOut();  // Выгружается из памяти
    boolean isStreamed();
    boolean isLoaded();
}
