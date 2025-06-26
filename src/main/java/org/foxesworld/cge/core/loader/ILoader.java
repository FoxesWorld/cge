package org.foxesworld.cge.core.loader;

import org.foxesworld.cge.core.utils.CallbackLatch;

/** Loader interface for dynamic registration. */
public interface ILoader {
    void setProgressListener(AssetProgressListener listener);
    void loadWithLatch(CallbackLatch latch);
}