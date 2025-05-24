package org.foxesworld.cge.streaming;

import com.jme3.asset.AssetManager;
import org.foxesworld.cge.core.cgs.SceneChunk;
import org.foxesworld.cge.core.cgs.parser.ParsedCGSFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class StreamingManager {
    private final AssetManager assetManager;
    private final ExecutorService executor;
    private final Map<Integer, SceneChunk> loadedChunks = new ConcurrentHashMap<>();
    private ParsedCGSFile currentScene;

    public StreamingManager(AssetManager assetManager) {
        this.assetManager = assetManager;
        this.executor = Executors.newFixedThreadPool(2); // future: configurable/global
    }

    public void loadScene(File file) throws IOException {
        currentScene = new ParsedCGSFile(file);
        // preloadEssentialChunks(); // TO-DO: preload terrain, root, etc.
    }

    public void streamChunkAsync(int chunkId, Consumer<SceneChunk> onComplete) {
        SceneChunk cached = loadedChunks.get(chunkId);
        if (cached != null) {
            onComplete.accept(cached);
            return;
        }

        executor.submit(() -> {
            try {
                SceneChunk chunk = currentScene.getChunk(chunkId);
                loadedChunks.putIfAbsent(chunkId, chunk);
                onComplete.accept(chunk);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void unloadChunk(int chunkId) {
        SceneChunk chunk = loadedChunks.remove(chunkId);
        if (chunk != null) {
            chunk.cleanup(); // предполагаем, что chunk умеет освобождать ресурсы
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            currentScene.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ParsedCGSFile getCurrentScene() {
        return currentScene;
    }
}
