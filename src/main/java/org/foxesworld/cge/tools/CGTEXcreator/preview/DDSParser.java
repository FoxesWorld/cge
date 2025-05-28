package org.foxesworld.cge.tools.CGTEXcreator.preview;

import org.foxesworld.cge.tools.CGTEXcreator.info.TextureInfo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class DDSParser {

    public static TextureInfo parse(File f) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] hdr = new byte[128];
            raf.readFully(hdr);

            // Проверка, что файл - это DDS
            if (hdr[0] != 'D' || hdr[1] != 'D' || hdr[2] != 'S' || hdr[3] != ' ') {
                throw new IOException("Not a DDS file: " + f.getName());
            }

            ByteBuffer buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);

            // Пропуск 12 байтов заголовка
            buf.position(12);

            // Чтение высоты и ширины
            int height = buf.getInt();
            int width = buf.getInt();

            // Пропуск до 84-го байта, где начинается имя и формат
            buf.position(84);

            // Чтение формата (DXT1, DXT3, DXT5)
            byte[] four = new byte[4];
            buf.get(four);
            String code = new String(four, StandardCharsets.UTF_8);

            // Чтение имени текстуры (до 8 байтов)
            byte[] nameBytes = new byte[8];
            buf.get(nameBytes);
            String texName = new String(nameBytes, StandardCharsets.UTF_8).trim();

            // Определение формата текстуры
            byte fmt = switch (code) {
                case "DXT1" -> 1;
                case "DXT3" -> 3;
                case "DXT5" -> 5;
                default -> throw new IOException("Unsupported DDS format: " + code);
            };

            // Чтение данных текстуры
            long remaining = raf.length() - 128;
            byte[] data = new byte[(int) remaining];
            raf.readFully(data);

            return new TextureInfo(f, width, height, texName, fmt, data);
        }
    }

    public static TextureInfo parseBytes(byte[] fileBytes) throws IOException {
        // Чтение данных из байтового массива
        ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
        byte[] hdr = new byte[128];
        bais.read(hdr);

        // Проверка, что файл - это DDS
        if (hdr[0] != 'D' || hdr[1] != 'D' || hdr[2] != 'S' || hdr[3] != ' ') {
            throw new IOException("Not a DDS file: Invalid data");
        }

        ByteBuffer buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);

        // Пропуск 12 байтов заголовка
        buf.position(12);

        // Чтение высоты и ширины
        int height = buf.getInt();
        int width = buf.getInt();

        // Пропуск до 84-го байта, где начинается имя и формат
        buf.position(84);

        // Чтение формата (DXT1, DXT3, DXT5)
        byte[] four = new byte[4];
        buf.get(four);
        String code = new String(four, StandardCharsets.UTF_8);

        // Чтение имени текстуры (до 8 байтов)
        byte[] nameBytes = new byte[8];
        buf.get(nameBytes);
        String texName = new String(nameBytes, StandardCharsets.UTF_8).trim();

        // Определение формата текстуры
        byte fmt = switch (code) {
            case "DXT1" -> 1;
            case "DXT3" -> 3;
            case "DXT5" -> 5;
            default -> throw new IOException("Unsupported DDS format: " + code);
        };

        // Чтение данных текстуры из оставшихся байтов
        byte[] data = new byte[fileBytes.length - 128];
        bais.read(data);

        return new TextureInfo(new File(texName), width, height, texName, fmt, data);
    }
}
