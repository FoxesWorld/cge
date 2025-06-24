package org.foxesworld.cge.core.file;

/**
 * Custom runtime exception for file format issues.
 */
public class FileFormatException extends RuntimeException {
    public FileFormatException(String msg) { super(msg); }
    public FileFormatException(String msg, Throwable t) { super(msg, t); }
}