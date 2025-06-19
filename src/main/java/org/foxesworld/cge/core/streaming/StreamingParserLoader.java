package org.foxesworld.cge.core.streaming;

import org.foxesworld.cge.core.io.ByteParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Objects;

/**
 * Универсальный загрузчик, который открывает файл, ресурс или использует ByteStreamer как fallback.
 * <p>
 * В данном варианте добавлены дополнительные проверки и логирование для улучшения стабильности
 * и оптимальной загрузки ресурсов. Если путь указывает на директорию или файл недоступен для чтения,
 * загрузка переключается на fallback-поток.
 *
 * @param <T> Тип объекта, получаемого после парсинга
 */
public class StreamingParserLoader<T> {

    private static final Logger logger = LogManager.getLogger(StreamingParserLoader.class);

    protected final ByteParser<T> parser;
    protected final ByteStreamer fallbackStreamer;

    /**
     * Конструктор загрузчика без fallback-стримера.
     * Если файл/ресурс отсутствует или не читается, используется пустой поток.
     *
     * @param parser парсер, не может быть {@code null}
     */
    public StreamingParserLoader(ByteParser<T> parser) {
        this(parser, path -> {
            logger.warn("No file or resource found for '{}'. Returning empty stream.", path);
            return new ByteArrayInputStream(new byte[0]);
        });
    }

    /**
     * Конструктор загрузчика с пользовательским fallback-стримером.
     *
     * @param parser           парсер, не может быть {@code null}
     * @param fallbackStreamer стример, вызываемый если файл не найден или недоступен
     */
    public StreamingParserLoader(ByteParser<T> parser, ByteStreamer fallbackStreamer) {
        this.parser = Objects.requireNonNull(parser, "Parser cannot be null");
        this.fallbackStreamer = Objects.requireNonNull(fallbackStreamer, "Fallback streamer cannot be null");
    }

    /**
     * Загружает и парсит объект по пути.
     *
     * @param path путь к файлу или ресурсу
     * @return результат парсинга
     * @throws IOException при ошибке загрузки или парсинга
     */
    public T load(String path) throws IOException {
        try (InputStream in = openInputStream(path)) {
            logger.debug("Loading stream from path: {}", path);
            return parser.parse(in);
        } catch (IOException e) {
            logger.error("Failed to load or parse stream from path: {}", path, e);
            throw e;
        }
    }

    /**
     * Открывает поток по пути:
     * <ol>
     *   <li>Файловый путь (проверка, что файл существует, не является директорией и доступен для чтения)</li>
     *   <li>Classpath ресурс</li>
     *   <li>{@link ByteStreamer} fallback</li>
     * </ol>
     *
     * @param path путь к ресурсу
     * @return поток
     * @throws IOException при ошибке открытия
     */
    protected InputStream openInputStream(String path) throws IOException {
        File file = new File(path);
        if (file.exists() && file.isFile() && file.canRead()) {
            logger.debug("Opening file input stream: {}", file.getAbsolutePath());
            return new FileInputStream(file);
        }

        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(path);
        if (resourceStream != null) {
            logger.debug("Opening classpath resource: {}", path);
            return resourceStream;
        }

        logger.info("Falling back to ByteStreamer for: {}", path);
        return fallbackStreamer.stream(path);
    }

    /**
     * Позволяет вручную передать {@link InputStream} и спарсить его.
     *
     * @param inputStream поток
     * @return результат парсинга
     * @throws IOException при ошибке чтения
     */
    public T parse(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "Input stream cannot be null");
        return parser.parse(inputStream);
    }
}