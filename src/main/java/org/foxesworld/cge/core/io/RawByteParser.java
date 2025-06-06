package org.foxesworld.cge.core.io;

import com.jme3.math.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class RawByteParser {
    private final ByteBuffer buffer;

    public RawByteParser(byte[] data) {
        this(data, ByteOrder.LITTLE_ENDIAN);
    }

    public RawByteParser(byte[] data, ByteOrder order) {
        this.buffer = ByteBuffer.wrap(data).order(order);
    }

    public byte readByte() { return buffer.get(); }
    public int readUnsignedByte() { return buffer.get() & 0xFF; }

    public short readShort() { return buffer.getShort(); }
    public int readUnsignedShort() { return buffer.getShort() & 0xFFFF; }

    public int readInt() { return buffer.getInt(); }
    public long readLong() { return buffer.getLong(); }

    public float readFloat() { return buffer.getFloat(); }
    public double readDouble() { return buffer.getDouble(); }
    public int readLEShort() {
        return (readUnsignedByte()) | (readUnsignedByte() << 8);
    }

    public int readLEInt() {
        return (readUnsignedByte()) |
                (readUnsignedByte() << 8) |
                (readUnsignedByte() << 16) |
                (readUnsignedByte() << 24);
    }

    public void readBytes(byte[] dst) {
        buffer.get(dst);
    }

    public void readBytes(byte[] dst, int offset, int length) {
        buffer.get(dst, offset, length);
    }

    public byte[] readAll() {
        byte[] all = new byte[buffer.remaining()];
        buffer.get(all);
        return all;
    }

    public RawByteParser slice(int offset, int length) {
        ByteBuffer dup = buffer.duplicate();
        dup.position(offset).limit(offset + length);
        ByteBuffer sliced = dup.slice().order(buffer.order());

        byte[] data = new byte[sliced.remaining()];
        sliced.get(data);
        return new RawByteParser(data, buffer.order());
    }

    public int size() {
        return buffer.capacity();
    }

    // --- Векторные и цветовые типы ---
    public Vector3f readVec3f() {
        return new Vector3f(readFloat(), readFloat(), readFloat());
    }

    public int[] readRGBA() {
        return new int[] {
                readUnsignedByte(),
                readUnsignedByte(),
                readUnsignedByte(),
                readUnsignedByte()
        };
    }

    // --- Строки ---
    public String readVarString8() {
        int len = readUnsignedByte();
        return readString(len);
    }

    public String readVarString16() {
        int len = readUnsignedShort();
        return readString(len);
    }

    public String readString(int len) {
        byte[] data = new byte[len];
        buffer.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    // --- Навигация ---
    public void skip(int bytes) {
        buffer.position(buffer.position() + bytes);
    }

    public void seek(int pos) {
        buffer.position(pos);
    }

    public int position() {
        return buffer.position();
    }

    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }

    public int remaining() {
        return buffer.remaining();
    }

    public void rewind() {
        buffer.rewind();
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }
}
