package org.foxesworld.cge.core.io.progressBar;

/**
 * Простая callback-интерфейс для прогресса загрузки ассетов.
 * Может быть реализован для UI, логирования, HUD и т.п.
 */
public interface ProgressListener {
    /**
     * Вызывается при обновлении прогресса.
     * @param assetType   тип ассета (например "Texture", "Model", "Sound")
     * @param loaded      сколько загружено
     * @param total       всего в очереди
     */
    void onProgress(String assetType, int loaded, int total);
}