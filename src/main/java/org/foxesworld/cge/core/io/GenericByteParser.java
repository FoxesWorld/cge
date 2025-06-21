package org.foxesworld.cge.core.io;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Universal non-abstract parser for byte arrays.
 * Allows passing a function to convert bytes to the desired type.
 * <p>
 * This implementation offers a flexible way to parse byte data using functional interfaces,
 * eliminating the need to create custom subclasses for each parsing scenario.
 *
 * @param <T> The target type to which the bytes will be parsed
 */
public class GenericByteParser<T> extends ByteParser<T> {

    private final Function<byte[], T> parser;

    /**
     * Creates a new generic byte parser with the specified parsing function.
     *
     * @param parser the function that converts byte arrays to objects of type T
     * @throws NullPointerException if the parser function is null
     */
    public GenericByteParser(Function<byte[], T> parser) {
        this.parser = Objects.requireNonNull(parser, "Parser function must not be null");
    }

    /**
     * Parses the byte array using the provided parser function.
     *
     * @param data the byte array to parse
     * @return the parsed object of type T
     * @throws IOException if parsing fails, wrapping any runtime exceptions that occur during parsing
     */
    @Override
    protected T parseBytes(byte[] data) throws IOException {
        Objects.requireNonNull(data, "Input data must not be null");

        try {
            return parser.apply(data);
        } catch (RuntimeException e) {
            throw new IOException("Failed to parse data: " + e.getMessage(), e);
        }
    }
}