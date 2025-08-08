package org.foxesworld.cge.core.sound;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SoundManager (Gson edition)
 *
 * JSON формат: { "event.name": [ { SoundDescriptor }, { SoundDescriptor } ], ... }
 *
 * Пример: ресурc "soundmap.json" в classpath
 * {
 *   "ui.hover": [
 *     { "path":"Sounds/ui/hover1.ogg", "volume":0.6, "pitch":1.0, "pitchVariance":0.05, "cooldownMs":80 },
 *     { "path":"Sounds/ui/hover2.ogg", "volume":0.5 }
 *   ],
 *   "ui.click": [
 *     { "path":"Sounds/ui/pop.ogg", "volume":1.0 }
 *   ]
 * }
 */
public class SoundManager {

    private final AssetManager assetManager;
    private final Random rnd = new Random();

    // event -> list of descriptors
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SoundDescriptor>> registry = new ConcurrentHashMap<>();
    // per-event last-play time (ms) для cooldown
    private final ConcurrentHashMap<String, AtomicLong> lastPlayedMillis = new ConcurrentHashMap<>();

    // executor для предзагрузки (однопоточный, daemon)
    private final ExecutorService preloadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SoundManager-Preloader");
        t.setDaemon(true);
        return t;
    });

    private final Gson gson = new Gson();

    public SoundManager(AssetManager assetManager) {
        this.assetManager = Objects.requireNonNull(assetManager, "AssetManager required");
    }

    // ---------------- JSON загрузка ----------------

    /**
     * Загружает маппинг из InputStream (Gson).
     */
    public void loadFromJson(InputStream jsonStream)  {
        if (jsonStream == null) throw new IllegalArgumentException("jsonStream == null");
        try (InputStreamReader reader = new InputStreamReader(jsonStream)) {
            Type type = new TypeToken<Map<String, List<SoundDescriptor>>>() {}.getType();
            Map<String, List<SoundDescriptor>> map = gson.fromJson(reader, type);
            if (map == null) return;
            map.forEach((k, v) -> {
                List<SoundDescriptor> list = v != null ? v : Collections.emptyList();
                registry.put(k, new CopyOnWriteArrayList<>(list));
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convenience: load from resource on classpath.
     */
    public void loadFromJsonResource(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IOException("Resource not found: " + resourcePath);
            loadFromJson(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------- Registration API ----------------

    public void register(String eventName, SoundDescriptor descriptor) {
        registry.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(Objects.requireNonNull(descriptor));
    }

    public void clearEvent(String eventName) {
        registry.remove(eventName);
        lastPlayedMillis.remove(eventName);
    }

    // ---------------- Preload ----------------

    /**
     * Предзагружает все звуки. Если async==true — выполняется в фоновом потоке.
     * Возвращает Future (null если sync).
     */
    public Future<?> preloadAll(boolean async) {
        Runnable task = () -> registry.forEach((evt, list) -> {
            for (SoundDescriptor sd : list) preloadDescriptor(sd);
        });
        if (async) return preloadExecutor.submit(task);
        task.run();
        return null;
    }

    /**
     * Предзагружает конкретный дескриптор (если ещё не загружен).
     */
    public void preloadDescriptor(SoundDescriptor sd) {
        if (sd == null || sd.path == null) return;
        synchronized (sd) {
            if (sd.audioNode != null) return;
            try {
                AudioNode node = new AudioNode(assetManager, sd.path, false);
                node.setPositional(false);
                node.setLooping(sd.loop);
                node.setVolume(sd.volume);
                sd.audioNode = node;
            } catch (Exception ex) {
                System.err.println("SoundManager: failed to preload '" + sd.path + "' -> " + ex.getMessage());
            }
        }
    }

    // ---------------- Playback ----------------

    /**
     * Проиграть событие (стандартно).
     */
    public boolean play(String eventName) {
        return play(eventName, 1.0f, 0f);
    }

    /**
     * Проиграть событие с множителем громкости и доп. питчем.
     */
    public boolean play(String eventName, float volumeMultiplier, float extraPitch) {
        CopyOnWriteArrayList<SoundDescriptor> list = registry.get(eventName);
        if (list == null || list.isEmpty()) return false;

        // pick random descriptor
        SoundDescriptor selected = list.get(rnd.nextInt(list.size()));
        long now = System.currentTimeMillis();
        AtomicLong last = lastPlayedMillis.computeIfAbsent(eventName, k -> new AtomicLong(0L));
        long lastMillis = last.get();
        long cooldown = Math.max(selected.cooldownMs, 0L);

        if (now - lastMillis < cooldown) {
            return false; // cooldown active
        }
        last.set(now);

        // lazy preload if necessary
        if (selected.audioNode == null) preloadDescriptor(selected);
        if (selected.audioNode == null) return false;

        // compute pitch jitter and volume
        float pitchJitter = (selected.pitchVariance != 0f)
                ? (float) ((rnd.nextDouble() - 0.5) * 2.0 * selected.pitchVariance)
                : 0f;
        float pitch = selected.pitch + pitchJitter + extraPitch;
        float volume = Math.max(0f, Math.min(1f, selected.volume * volumeMultiplier));

        try {
            selected.audioNode.setVolume(volume);
            try { selected.audioNode.setPitch(pitch); } catch (Throwable ignored) {}
            selected.audioNode.playInstance();
            return true;
        } catch (Exception ex) {
            System.err.println("SoundManager: failed to play " + selected.path + " -> " + ex.getMessage());
            return false;
        }
    }

    /**
     * Остановить looping звуки для события.
     */
    public void stopAllForEvent(String eventName) {
        CopyOnWriteArrayList<SoundDescriptor> list = registry.get(eventName);
        if (list == null) return;
        for (SoundDescriptor sd : list) {
            if (sd.audioNode != null) {
                try { sd.audioNode.stop(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Выгрузить все предзагруженные AudioNode (освободить память).
     */
    public void unloadAll() {
        registry.forEach((evt, list) -> {
            for (SoundDescriptor sd : list) {
                if (sd.audioNode != null) {
                    try { sd.audioNode.stop(); } catch (Exception ignored) {}
                    sd.audioNode = null;
                }
            }
        });
        lastPlayedMillis.clear();
    }

    public void shutdown() {
        preloadExecutor.shutdownNow();
        unloadAll();
    }

    // ---------------- Utilities ----------------

    public Set<String> getRegisteredEvents() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    public List<SoundDescriptor> getDescriptors(String event) {
        CopyOnWriteArrayList<SoundDescriptor> list = registry.get(event);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }
}