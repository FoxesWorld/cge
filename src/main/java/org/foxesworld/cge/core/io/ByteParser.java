package org.foxesworld.cge.core.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Абстрактный класс для парсинга файлов в виде байтов.
 * Читает InputStream целиком в байты и вызывает абстрактный метод
 * для парсинга конкретного объекта из массива байтов.
 */
public abstract class ByteParser<T> {

    /**
     * Метод для парсинга конкретного объекта из массива байт.
     * Наследники реализуют этот метод.
     *
     * @param data байтовый массив файла
     * @return объект типа T, распарсенный из байтов
     * @throws IOException при ошибках парсинга
     */
    protected abstract T parseBytes(byte[] data) throws IOException;

    /**
     * Метод для чтения из InputStream и запуска парсинга.
     *
     * @param inputStream входящий поток
     * @return объект типа T
     * @throws IOException при ошибках чтения или парсинга
     */
    public T parse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        byte[] data = baos.toByteArray();

        return parseBytes(data);
    }
}