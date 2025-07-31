package org.foxesworld.cge.core.io.streaming;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.io.ByteParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Universal loader that opens a file, resource, or uses ByteStreamer as a fallback.
 * <p>
 * This implementation includes additional checks and logging to improve stability
 * and optimize resource loading. If the path points to a directory or the file is not readable,
 * loading switches to the fallback stream.
 *
 * @param <T> Type of object obtained after parsing
 */
public class StreamingParserLoader<T> {

    private static final Logger logger = LogManager.getLogger(StreamingParserLoader.class);

    protected final ByteParser<T> parser;
    protected final ByteStreamer fallbackStreamer;

    /**
     * Constructor for the loader without a fallback streamer.
     * If the file/resource is missing or not readable, an empty stream is used.
     *
     * @param parser parser, cannot be {@code null}
     * @throws NullPointerException if parser is null
     */
    public StreamingParserLoader(ByteParser<T> parser) {
        this(parser, path -> {
            logger.warn("No file or resource found for '{}'. Returning empty stream.", path);
            return new ByteArrayInputStream(new byte[0]);
        });
    }

    /**
     * Constructor for the loader with a custom fallback streamer.
     *
     * @param parser parser, cannot be {@code null}
     * @param fallbackStreamer streamer called if the file is not found or inaccessible
     * @throws NullPointerException if parser or fallbackStreamer is null
     */
    public StreamingParserLoader(ByteParser<T> parser, ByteStreamer fallbackStreamer) {
        this.parser = Objects.requireNonNull(parser, "Parser cannot be null");
        this.fallbackStreamer = Objects.requireNonNull(fallbackStreamer, "Fallback streamer cannot be null");
    }

    /**
     * Loads and parses an object from the specified path.
     *
     * @param path path to the file or resource
     * @return parsing result
     * @throws IOException if an error occurs during loading or parsing
     * @throws NullPointerException if path is null
     */
    public T load(String path) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");

        try (InputStream in = openInputStream(path)) {
            logger.debug("Loading stream from path: {}", path);
            return parser.parse(in);
        } catch (IOException e) {
            logger.error("Failed to load or parse stream from path: {}", path, e);
            throw new IOException("Failed to load or parse from path: " + path, e);
        }
    }


    /**
     * Opens an InputStream for the given path, using the AssetManager as the primary
     * mechanism and a custom streamer as a fallback.
     *
     * @param path The path to the resource. The AssetManager will look for it on the classpath and in asset folders.
     * @return An open InputStream.
     * @throws IOException If the resource cannot be found by any means.
     */
    protected InputStream openInputStream(String path) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");

        try {
            AssetKey<?> assetKey = new AssetKey<>(path);
            AssetInfo assetInfo = CalistaGameEngine.INSTANCE.getAssetManager().locateAsset(assetKey);

            if (assetInfo != null) {
                logger.debug("Opening resource via JME AssetManager: {}", path);
                return assetInfo.openStream();
            }
            logger.debug("AssetManager did not find the resource (locateAsset returned null): {}", path);

        } catch (AssetNotFoundException e) {
            logger.debug("Resource not found via AssetManager: {}. Trying fallback.", path);
        }

        logger.info("Resource not found in standard locations. Using fallback streamer for: {}", path);
        try {
            return fallbackStreamer.stream(path);
        } catch (Exception e) {
            logger.error("Fallback streamer also failed to process path: {}", path, e);
            throw new IOException("Failed to open stream for path: " + path, e);
        }
    }


    /**
     * Allows manually passing an {@link InputStream} for parsing.
     *
     * @param inputStream input stream
     * @return parsing result
     * @throws IOException if a reading error occurs
     * @throws NullPointerException if inputStream is null
     */
    public T parse(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "Input stream cannot be null");
        try {
            return parser.parse(inputStream);
        } catch (IOException e) {
            logger.error("Failed to parse input stream", e);
            throw new IOException("Error parsing input stream", e);
        }
    }
}