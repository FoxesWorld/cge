package org.foxesworld.cge.core.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.function.Function;

public class FileReader implements Closeable {
    private static final Logger logger = LogManager.getLogger(FileReader.class);

    private final RandomAccessFile raf;
    private final AbstractFile abstractFile;
    private final byte[] fileBytes;

    public FileReader(AbstractFile abstractFile, String mode) {
        if (abstractFile == null || abstractFile.getFile() == null)
            throw new IllegalArgumentException("abstractFile or its file is null");
        if (!"r".equals(mode) && !"rw".equals(mode))
            throw new IllegalArgumentException("Invalid mode: " + mode);

        this.abstractFile = abstractFile;
        this.raf = openRandomAccess(abstractFile.getFile(), mode);
        this.fileBytes = readSafely(abstractFile.getFile(), mode, raf -> {
            try {
                byte[] bytes = new byte[(int) raf.length()];
                raf.readFully(bytes);
                return bytes;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private RandomAccessFile openRandomAccess(File file, String mode) {
        try {
            return new RandomAccessFile(file, mode);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Cannot open file: " + file.getAbsolutePath(), e);
        }
    }

    public RandomAccessFile getRaf() {
        return raf;
    }

    public byte[] getFileBytes() {
        return fileBytes;
    }

    public <T> T readSafely(File file, String mode, Function<RandomAccessFile, T> reader) {
        try (RandomAccessFile raf = new RandomAccessFile(file, mode)) {
            return reader.apply(raf);
        } catch (Exception e) {
            logger.error("Error reading file '{}': {}", file.getName(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            logger.debug("Closing file: {}", abstractFile.getFile().getName());
            raf.close();
        } catch (Exception e) {
            logger.warn("Failed to close file: {}", e.getMessage());
        }
    }

    @FunctionalInterface
    public interface IOFunction<T, R> {
        R apply(T t) throws IOException;
    }

    public <R> R readSafely(IOFunction<RandomAccessFile, R> function) {
        try {
            return function.apply(raf);
        } catch (IOException e) {
            throw new RuntimeException("Read failed", e);
        }
    }

}
