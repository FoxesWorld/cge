package org.foxesworld.cge.core.io;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Универсальный не абстрактный парсер для массива байт.
 * Позволяет передавать функцию для преобразования байтов в нужный тип.
 */
public class GenericByteParser<T> extends ByteParser<T> {

    private final Function<byte[], T> parser;

    public GenericByteParser(Function<byte[], T> parser) {
        this.parser = Objects.requireNonNull(parser, "Parser function must not be null");
    }

    @Override
    protected T parseBytes(byte[] data) throws IOException {
        try {
            return parser.apply(data);
        } catch (RuntimeException e) {
            throw new IOException("Failed to parse data", e);
        }
    }
}
