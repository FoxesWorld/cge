package org.foxesworld.cge.core.streaming;

import java.io.InputStream;
import java.io.IOException;

/**
 * Интерфейс для потоковой генерации содержимого, если файл/ресурс не найден.
 */
@FunctionalInterface
public interface ByteStreamer {
    /**
     * Создаёт поток для fallback-загрузки.
     *
     * @param path путь, который не удалось открыть обычным способом
     * @return {@link InputStream} с fallback-данными
     * @throws IOException при ошибке генерации потока
     */
    InputStream stream(String path) throws IOException;
}
