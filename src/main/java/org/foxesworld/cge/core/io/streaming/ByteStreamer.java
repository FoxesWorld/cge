package org.foxesworld.cge.core.io.streaming;

import java.io.InputStream;
import java.io.IOException;

/**
 * Interface for streaming content generation when a file or resource is not found.
 * <p>
 * Provides a fallback mechanism to generate alternative content when standard
 * resource loading fails.
 */
@FunctionalInterface
public interface ByteStreamer {
    /**
     * Creates a stream for fallback loading.
     *
     * @param path the path that couldn't be opened through normal means
     * @return {@link InputStream} with fallback data
     * @throws IOException if an error occurs during stream generation
     * @throws NullPointerException if the path is null and implementation doesn't handle null values
     */
    InputStream stream(String path) throws IOException;
}