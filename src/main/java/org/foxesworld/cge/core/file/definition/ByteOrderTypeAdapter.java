package org.foxesworld.cge.core.file.definition;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.nio.ByteOrder;

public class ByteOrderTypeAdapter extends TypeAdapter<ByteOrder> {

    @Override
    public void write(JsonWriter out, ByteOrder value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value == ByteOrder.BIG_ENDIAN ? "BIG_ENDIAN" : "LITTLE_ENDIAN");
        }
    }

    @Override
    public ByteOrder read(JsonReader in) throws IOException {
        String str = in.nextString();
        if ("LITTLE_ENDIAN".equalsIgnoreCase(str)) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        return ByteOrder.BIG_ENDIAN;
    }
}
