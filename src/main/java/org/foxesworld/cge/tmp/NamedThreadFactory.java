package org.foxesworld.cge.tmp;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

// ThreadFactory с понятными именами потоков:
public class NamedThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger idx = new AtomicInteger();
    NamedThreadFactory(String prefix) { this.prefix = prefix; }
    @Override public Thread newThread(Runnable r) {
        Thread t = new Thread(r, prefix + "-" + idx.getAndIncrement());
        t.setDaemon(true);
        return t;
    }
}