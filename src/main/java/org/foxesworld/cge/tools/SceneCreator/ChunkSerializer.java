package org.foxesworld.cge.tools.SceneCreator;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Вспомогательный класс, который сериализует одну запись чанкa по
 * описанию types.
 */
class ChunkSerializer {
    static byte[] serialize(
            String subtype,
            Map<String,Object> values,
            Map<String,String> fieldTypes
    ) throws IOException {
        try (var baos = new ByteArrayOutputStream();
             var dos  = new DataOutputStream(baos)) {
            // 1) subtype как UTF-16BE длина+bytes
            writeString(dos, subtype);

            // 2) поля в порядке fieldTypes.keySet()
            for (var entry: fieldTypes.entrySet()) {
                String name = entry.getKey();
                String type = entry.getValue().toUpperCase();
                Object val  = values.get(name);

                switch (type) {
                    case "INT"    -> dos.writeInt(((Number)val).intValue());
                    case "FLOAT"  -> dos.writeFloat(((Number)val).floatValue());
                    case "BOOLEAN"-> dos.writeBoolean((Boolean)val);
                    case "STRING" -> writeString(dos, val.toString());
                    case "COLOR","FLOAT4","VEC4" -> writeColorOrVec4(dos, val);
                    case "VECTOR3F" -> writeVector3f(dos, val);
                    default -> throw new IllegalArgumentException("Unknown type "+type);
                }
            }
            return baos.toByteArray();
        }
    }

    private static void writeString(DataOutputStream dos, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(b.length);
        dos.write(b);
    }

    /**
     * Записывает либо цвет (4 байта), либо вектор float[4] (4 float).
     *
     * @param dos поток для записи
     * @param val Color, или String "R,G,B" / "R,G,B,A", или float[4]
     */
    private static void writeColorOrVec4(DataOutputStream dos, Object val) throws IOException {
        // 1) java.awt.Color → 4 байта
        if (val instanceof Color c) {
            dos.writeByte(c.getRed());
            dos.writeByte(c.getGreen());
            dos.writeByte(c.getBlue());
            dos.writeByte(c.getAlpha());
            return;
        }

        // 2) String "R,G,B" or "R,G,B,A"
        if (val instanceof String s) {
            String[] parts = s.split(",");
            if (parts.length < 3 || parts.length > 4) {
                throw new IllegalArgumentException(
                        "Expected comma-separated 3 or 4 ints, got " + parts.length);
            }
            for (int i = 0; i < parts.length; i++) {
                int v;
                try {
                    v = Integer.parseInt(parts[i].trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(
                            "Invalid integer in color string at index " + i + ": \"" + parts[i] + "\"", ex);
                }
                dos.writeByte(v);
            }
            // Если было только R,G,B — дописываем альфу = 255
            if (parts.length == 3) {
                dos.writeByte(255);
            }
            return;
        }

        // 3) float[4] → 4 float
        if (val instanceof float[] arr) {
            if (arr.length != 4) {
                throw new IllegalArgumentException("Expected float[4], got length=" + arr.length);
            }
            for (float f : arr) {
                dos.writeFloat(f);
            }
            return;
        }

        throw new IllegalArgumentException(
                "Expected Color, String \"R,G,B(,A)\", or float[4], but got " +
                        (val == null ? "null" : val.getClass().getSimpleName()));
    }


    /**
     * Записывает в поток три float-значения из массива или из строки вида "x,y,z".
     *
     * @param dos поток, в который записываем
     * @param val либо float[3], либо строка "v0,v1,v2"
     * @throws IOException при ошибке записи
     * @throws IllegalArgumentException если формат val неверен
     */
    private static void writeVector3f(DataOutputStream dos, Object val) throws IOException {
        final int EXPECTED_SIZE = 3;
        float[] arr;

        if (val instanceof float[] fArr) {
            arr = fArr;
        }
        else if (val instanceof String s) {
            String[] parts = s.split(",");
            if (parts.length != EXPECTED_SIZE) {
                throw new IllegalArgumentException(
                        "Expected comma-separated 3 floats, got " + parts.length
                );
            }
            arr = new float[EXPECTED_SIZE];
            for (int i = 0; i < EXPECTED_SIZE; i++) {
                try {
                    arr[i] = Float.parseFloat(parts[i].trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(
                            "Invalid float value at position " + i + ": \"" + parts[i] + "\"",
                            ex
                    );
                }
            }
        }
        else {
            throw new IllegalArgumentException(
                    "Expected float[3] or comma-separated String, got " + val.getClass().getSimpleName()
            );
        }

        if (arr.length != EXPECTED_SIZE) {
            throw new IllegalArgumentException("Expected array length 3, got " + arr.length);
        }

        for (float v : arr) {
            dos.writeFloat(v);
        }
    }

}
