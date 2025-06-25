package com.jme3.phonon.utils;

import java.nio.Buffer;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DirectBufferUtils {
    private static final Logger LOGGER = Logger.getLogger(DirectBufferUtils.class.getName());

    /**
     * Attempts to get the native address of a direct buffer using reflection.
     * Throws an exception if the address cannot be determined.
     */
    public static long getAddr(Buffer directBuffer) {
        if (directBuffer == null) {
            LOGGER.severe("DirectBufferUtils.getAddr called with null buffer.");
            throw new IllegalArgumentException("Buffer is null");
        }
        if (!directBuffer.isDirect()) {
            LOGGER.warning("Buffer is not direct: " + directBuffer.getClass().getName());
            throw new UnsupportedOperationException("Can't get native address from a non-direct buffer");
        }
        try {
            LOGGER.fine("Attempting to access 'address' field via reflection for: " + directBuffer.getClass().getName());
            Field addressField = Buffer.class.getDeclaredField("address");
            addressField.setAccessible(true);
            long address = addressField.getLong(directBuffer);
            LOGGER.fine("Obtained address: " + address + " for buffer of type: " + directBuffer.getClass().getName() +
                    ", capacity: " + directBuffer.capacity() + ", limit: " + directBuffer.limit() +
                    ", position: " + directBuffer.position());
            if (address == 0L) {
                LOGGER.severe("Direct buffer address field is zero (unallocated or inaccessible). Buffer: " + directBuffer);
                throw new IllegalStateException("Native address for direct buffer is zero (unallocated or inaccessible)");
            }
            return address;
        } catch (NoSuchFieldException nsfe) {
            LOGGER.log(Level.SEVERE, "Direct buffer does not have an 'address' field (JVM incompatible). Buffer: " + directBuffer.getClass().getName(), nsfe);
            throw new UnsupportedOperationException("Direct buffer does not have an 'address' field", nsfe);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to get native address from direct buffer: " + directBuffer.getClass().getName(), e);
            throw new IllegalStateException("Failed to get native address from direct buffer", e);
        }
    }
}