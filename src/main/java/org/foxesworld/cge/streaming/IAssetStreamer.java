package org.foxesworld.cge.streaming;

import java.io.IOException;
import java.io.InputStream;

public interface IAssetStreamer<T extends IStreamableAsset> {
    T load(InputStream stream) throws IOException;
}
