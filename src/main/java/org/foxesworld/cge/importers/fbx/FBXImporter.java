package org.foxesworld.cge.importers.fbx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class FBXImporter {

    public static FBXScene load(byte[] data) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(Objects.requireNonNull(data))) {
            return load(in);
        }
    }

    public static FBXScene load(InputStream in) throws IOException {
        FBXParser parser = new FBXParser();
        FBXNode root = parser.parse(in);
        return FBXScene.fromFBXNode(root);
    }
}