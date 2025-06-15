package org.foxesworld.cge.core.file.extensions.cgmat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.foxesworld.cge.core.file.Metadata;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CGMATMetadata extends Metadata {
    private final String name;
    private final Map<String,Object> properties;
    private final List<String> texturePaths;

    private static final Gson GSON = new Gson();

    public CGMATMetadata(String headerJson,
                         Map<String, Object> properties,
                         List<String> texturePaths) {
        this.name = parseNameFromJson(headerJson);
        this.properties = Collections.unmodifiableMap(properties);
        this.texturePaths = Collections.unmodifiableList(texturePaths);
    }

    private static String parseNameFromJson(String headerJson) {
        try {
            JsonObject root = GSON.fromJson(headerJson, JsonObject.class);
            if (root != null && root.has("name") && !root.get("name").isJsonNull()) {
                return root.get("name").getAsString();
            }
            return "";
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid header JSON: " + e.getMessage(), e);
        }
    }

    public String getName() {
        return name;
    }

    public Map<String,Object> getProperties() {
        return properties;
    }

    public List<String> getTexturePaths() {
        return texturePaths;
    }
}
