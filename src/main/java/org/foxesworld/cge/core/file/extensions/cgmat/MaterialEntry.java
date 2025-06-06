package org.foxesworld.cge.core.file.extensions.cgmat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

public class MaterialEntry {
    private final String name;
    private final int paramCount;
    private final byte[] data;

    public MaterialEntry(String name, int paramCount, byte[] data) {
        this.name = name;
        this.paramCount = paramCount;
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public int getParamCount() {
        return paramCount;
    }

    public byte[] getData() {
        return data;
    }

    /**
     * Создаёт материал из параметров.
     *
     * @param name    имя материала
     * @param params  список параметров: тип, имя, значение (например, float emissivePower = 3.0f)
     * @return материал
     */
    public static MaterialEntry fromParams(String name, List<MaterialParam> params) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            for (MaterialParam param : params) {
                dos.writeByte(param.getType().ordinal()); // Тип параметра
                writeString(dos, param.getName());
                switch (param.getType()) {
                    case FLOAT:
                        dos.writeFloat((Float) param.getValue());
                        break;
                    case BOOLEAN:
                        dos.writeBoolean((Boolean) param.getValue());
                        break;
                    case STRING:
                        writeString(dos, (String) param.getValue());
                        break;
                    case VECTOR2:
                        float[] vec2 = (float[]) param.getValue();
                        dos.writeFloat(vec2[0]);
                        dos.writeFloat(vec2[1]);
                        break;
                    case VECTOR3:
                        float[] vec3 = (float[]) param.getValue();
                        dos.writeFloat(vec3[0]);
                        dos.writeFloat(vec3[1]);
                        dos.writeFloat(vec3[2]);
                        break;
                    case VECTOR4:
                        float[] vec4 = (float[]) param.getValue();
                        dos.writeFloat(vec4[0]);
                        dos.writeFloat(vec4[1]);
                        dos.writeFloat(vec4[2]);
                        dos.writeFloat(vec4[3]);
                        break;
                }
            }

            return new MaterialEntry(name, params.size(), baos.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize material parameters", e);
        }
    }

    public static MaterialEntry fromMap(Map<String, Object> map) {
        String name = (String) map.get("name");
        int paramCount = (Integer) map.get("paramCount");
        byte[] data = (byte[]) map.get("data");
        return new MaterialEntry(name, paramCount, data);
    }

    private static void writeString(DataOutputStream dos, String value) throws IOException {
        byte[] bytes = value.getBytes("UTF-8");
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    @Override
    public String toString() {
        return "MaterialEntry{" +
                "name='" + name + '\'' +
                ", paramCount=" + paramCount +
                ", dataSize=" + (data != null ? data.length : 0) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MaterialEntry)) return false;
        MaterialEntry that = (MaterialEntry) o;
        return paramCount == that.paramCount &&
                Objects.equals(name, that.name) &&
                Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, paramCount);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    // Тип параметра
    public enum ParamType {
        FLOAT, BOOLEAN, STRING, VECTOR2, VECTOR3, VECTOR4
    }

    // Один параметр
    public static class MaterialParam {
        private final String name;
        private final ParamType type;
        private final Object value;

        public MaterialParam(String name, ParamType type, Object value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public ParamType getType() {
            return type;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public String toString() {
            return name + " (" + type + ") = " + value;
        }
    }
}
