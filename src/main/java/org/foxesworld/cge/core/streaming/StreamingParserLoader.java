package org.foxesworld.cge.core.streaming;

import org.foxesworld.cge.core.io.ByteParser;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Универсальный загрузчик, который открывает файл по пути и парсит его через ByteParser.
 * Используется в StreamingManager.
 *
 * @param <T> тип объекта, получаемого после парсинга
 */
public class StreamingParserLoader<T> {

    private final ByteParser<T> parser;

    public StreamingParserLoader(ByteParser<T> parser) {
        this.parser = Objects.requireNonNull(parser);
    }

    public T load(String path) throws IOException {
        try (InputStream in = openInputStream(path)) {
            return parser.parse(in);
        }
    }

    protected InputStream openInputStream(String path) throws IOException {
        // По умолчанию — просто открываем файл с диска.
        return new FileInputStream(path);
    }
}
