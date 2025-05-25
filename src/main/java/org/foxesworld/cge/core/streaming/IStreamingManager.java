package org.foxesworld.cge.core.streaming;

import com.jme3.app.Application;

public interface IStreamingManager {

    void initialize(Application app);

    /**
     * Загружает указанный блок сцены (например, локацию, сектор, зону).
     */
    void streamIn(String sceneId, int chunkId);

    /**
     * Выгружает из памяти блок сцены.
     */
    void streamOut(String sceneId, int chunkId);

    /**
     * Обновление логики стриминга, например, по позиции камеры.
     */
    void update(float tpf);

    /**
     * Очистка всех ресурсов.
     */
    void shutdown();
}
