package org.foxesworld.cge.core.loader;

import org.foxesworld.cge.core.io.progressBar.ProgressListener;
import org.foxesworld.cge.core.utils.CallbackLatch;

/** Simple wrapper for type safety */
class LoaderWrapper {
    private final ILoader loader;

    LoaderWrapper(ILoader loader) {
        this.loader = loader;
    }

    void setProgressListener(ProgressListener l) {
        loader.setProgressListener(l);
    }

    void loadWithLatch(CallbackLatch latch) {
        loader.loadWithLatch(latch);
    }
}