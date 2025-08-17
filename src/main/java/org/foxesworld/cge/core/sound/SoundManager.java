package org.foxesworld.cge.core.sound;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SoundManager (Gson edition)
 *
 * JSON формат: либо плоский { "event.name": [ { SoundDescriptor }, ... ], ... }
 * либо группированный { "group": { "event": [ ... ], "event2": [...] }, "other": { ... } }
 *
 * При загрузке вложенные ключи будут преобразованы в 'group.event' и зарегистрированы.
 */
public class SoundManager {

    private static final Logger LOGGER = LogManager.getLogger(SoundManager.class);

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
     * Поддерживает как плоский формат "event.name": [ ... ], так и вложенный:
     * { "ui": { "press": [...], "hover": [...] }, "player": { "step": [...] } }
     * Вложенные ключи будут склеены через точку: "ui.press", "player.step".
     */
    public void loadFromJson(InputStream jsonStream)  {
        if (jsonStream == null) throw new IllegalArgumentException("jsonStream == null");
        try (InputStreamReader reader = new InputStreamReader(jsonStream)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || root.isJsonNull()) return;
            if (root.isJsonObject()) {
                processJsonObject("", root.getAsJsonObject());
            } else {
                // unexpected top-level type, try to treat as map anyway (fallback)
                Type type = new TypeToken<Map<String, List<SoundDescriptor>>>() {}.getType();
                Map<String, List<SoundDescriptor>> map = gson.fromJson(root, type);
                if (map == null) return;
                map.forEach((k, v) -> {
                    List<SoundDescriptor> list = v != null ? v : Collections.emptyList();
                    registry.put(k, new CopyOnWriteArrayList<>(list));
                    LOGGER.info("Loaded sound event: {} ({} descriptors)", k, list.size());
                });
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Рекурсивно обходит объект и регистрирует списки дескрипторов.
     * prefix пустой для корня; при заходе внутрь объект ключ становится prefix.key.
     */
    private void processJsonObject(String prefix, JsonObject obj) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            String composed = (prefix == null || prefix.isEmpty()) ? key : (prefix + "." + key);

            if (value == null || value.isJsonNull()) {
                continue;
            }

            // --- новая логика для readFrom ---
            if (value.isJsonObject() && value.getAsJsonObject().has("readFrom")) {
                String includePath = value.getAsJsonObject().get("readFrom").getAsString();
                LOGGER.info("Чтение внешнего файла для '{}': {}", composed, includePath);

                try (InputStream includeStream = getClass().getClassLoader().getResourceAsStream(includePath)) {
                    if (includeStream == null) {
                        LOGGER.error("Не найден файл '{}'", includePath);
                        continue;
                    }
                    JsonElement includedRoot = JsonParser.parseReader(new InputStreamReader(includeStream, StandardCharsets.UTF_8));
                    if (includedRoot.isJsonObject()) {
                        processJsonObject(composed, includedRoot.getAsJsonObject());
                    } else {
                        Type listType = new TypeToken<List<SoundDescriptor>>() {}.getType();
                        List<SoundDescriptor> list = gson.fromJson(includedRoot, listType);
                        if (list == null) list = Collections.emptyList();
                        registry.put(composed, new CopyOnWriteArrayList<>(list));
                        LOGGER.info("Loaded sound event (from include): {} ({} descriptors)", composed, list.size());
                    }
                } catch (IOException e) {
                    LOGGER.error("Ошибка при чтении '{}': {}", includePath, e.getMessage(), e);
                }
                continue; // переходим к следующему ключу
            }

            if (value.isJsonArray()) {
                Type listType = new TypeToken<List<SoundDescriptor>>() {}.getType();
                List<SoundDescriptor> list = gson.fromJson(value, listType);
                if (list == null) list = Collections.emptyList();
                registry.put(composed, new CopyOnWriteArrayList<>(list));
                LOGGER.info("Loaded sound event: {} ({} descriptors)", composed, list.size());
            } else if (value.isJsonObject()) {
                processJsonObject(composed, value.getAsJsonObject());
            } else {
                // unsupported primitive type — можно логировать warning
                LOGGER.warn("Пропущен ключ '{}': неподдерживаемый тип {}", composed, value.getClass().getSimpleName());
            }
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
        Objects.requireNonNull(descriptor, "descriptor");
        registry.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(descriptor);
        // log registration
        String path = descriptor.path == null ? "<null>" : descriptor.path;
        LOGGER.info("Registered sound for event: {} -> {}", eventName, path);
    }

    public void clearEvent(String eventName) {
        registry.remove(eventName);
        lastPlayedMillis.remove(eventName);
        LOGGER.debug("Cleared event: {}", eventName);
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
                node.setPitch(sd.pitch);
                sd.audioNode = node;
                //LOGGER.debug("Preloaded sound: {}", sd.path);
            } catch (Exception ex) {
                LOGGER.warn("SoundManager: failed to preload '{}' -> {}", sd.path, ex.getMessage(), ex);
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
            LOGGER.trace("Cooldown active for event {} (now {} last {} cooldown {})", eventName, now, lastMillis, cooldown);
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
            selected.audioNode.setVolume(volume * (float) MainMenuAppState.getSettingsValue( "audio", "master"));
            try { selected.audioNode.setPitch(pitch); } catch (Throwable ignored) {}
            selected.audioNode.playInstance();
            //LOGGER.debug("Played sound: {} (event: {}, vol: {}, pitch: {})", selected.path, eventName, volume, pitch);
            return true;
        } catch (Exception ex) {
            LOGGER.warn("SoundManager: failed to play {} -> {}", selected.path, ex.getMessage(), ex);
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
        LOGGER.debug("Stopped sounds for event: {}", eventName);
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
        LOGGER.debug("Unloaded all sounds and cleared cooldowns");
    }

    public void shutdown() {
        preloadExecutor.shutdownNow();
        unloadAll();
        LOGGER.debug("SoundManager shutdown");
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
