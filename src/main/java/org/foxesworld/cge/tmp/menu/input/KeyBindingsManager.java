package org.foxesworld.cge.tmp.menu.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.KeyTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Менеджер привязок клавиш.
 *
 * Хранение пользовательских привязок теперь осуществляется в JSON файле (человеко-читаемый).
 * ВАЖНО: имена сохраняются с префиксами (например KEY_W, KEY_SPACE, KEY_LSHIFT).
 */
public final class KeyBindingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(KeyBindingsManager.class);

    /** Префикс для mapping names в InputManager */
    private static final String MAPPING_PREFIX = "kb_action_";

    /** Отражённые мапы имя → keyCode и обратно. CODE_TO_NAME хранит префиксированные имена (KEY_...). */
    private static final Map<String, Integer> NAME_TO_CODE = new HashMap<>();
    private static final Map<Integer, String> CODE_TO_NAME = new HashMap<>();

    static {
        try {
            // Заполним карты именами из KeyInput, **сохраняя полный KEY_* формат** в CODE_TO_NAME.
            for (Field f : KeyInput.class.getFields()) {
                String fname = f.getName();           // например "KEY_A", "KEY_SPACE"
                if (!fname.startsWith("KEY_")) continue;
                int code = f.getInt(null);

                // сохраняем полное имя с префиксом
                NAME_TO_CODE.put(fname, code);
                CODE_TO_NAME.put(code, fname);

                // и сохраняем удобные алиасы (без префикса), чтобы парсинг принимал "W", "SPACE" и т.д.
                String shortName = fname.substring(4); // KEY_A -> A, KEY_SPACE -> SPACE
                NAME_TO_CODE.putIfAbsent(shortName, code);
            }

            // дополнительные удобные алиасы (без префикса) — на всякий случай
            NAME_TO_CODE.putIfAbsent("ENTER", KeyInput.KEY_RETURN);
            NAME_TO_CODE.putIfAbsent("RETURN", KeyInput.KEY_RETURN);
            NAME_TO_CODE.putIfAbsent("ESC", KeyInput.KEY_ESCAPE);
            NAME_TO_CODE.putIfAbsent("ESCAPE", KeyInput.KEY_ESCAPE);
            NAME_TO_CODE.putIfAbsent("CTRL", KeyInput.KEY_LCONTROL);
            NAME_TO_CODE.putIfAbsent("CONTROL", KeyInput.KEY_LCONTROL);
            NAME_TO_CODE.putIfAbsent("ALT", KeyInput.KEY_LMENU);
            NAME_TO_CODE.putIfAbsent("SHIFT", KeyInput.KEY_LSHIFT);
            NAME_TO_CODE.putIfAbsent("SPACE", KeyInput.KEY_SPACE);
            NAME_TO_CODE.putIfAbsent("UP", KeyInput.KEY_UP);
            NAME_TO_CODE.putIfAbsent("DOWN", KeyInput.KEY_DOWN);
            NAME_TO_CODE.putIfAbsent("LEFT", KeyInput.KEY_LEFT);
            NAME_TO_CODE.putIfAbsent("RIGHT", KeyInput.KEY_RIGHT);

            // ensure CODE_TO_NAME has entries for aliased codes (they already added from KeyInput loop)
            // (no-op here, but kept for clarity)
            for (Map.Entry<String, Integer> e : NAME_TO_CODE.entrySet()) {
                CODE_TO_NAME.putIfAbsent(e.getValue(), e.getKey().startsWith("KEY_") ? e.getKey() : "KEY_" + e.getKey());
            }

        } catch (Exception ex) {
            LOG.warn("KeyInput reflection initialization failed", ex);
        }
    }

    /** Модель одной привязки */
    public static final class KeyBind {
        public final String id;         // уникальный id (из XML)
        public final String action;     // человекочитаемое описание действия
        public final String defaultKey; // строковое имя дефолтной клавиши, как в XML (может быть "SPACE" или "KEY_SPACE")
        private volatile int currentKeyCode; // текущий KeyInput код (-1 == none)

        KeyBind(String id, String action, String defaultKey, int defaultKeyCode) {
            this.id = Objects.requireNonNull(id);
            this.action = (action == null || action.isEmpty()) ? id : action;
            this.defaultKey = defaultKey;
            this.currentKeyCode = defaultKeyCode;
        }

        public int getCurrentKeyCode() { return currentKeyCode; }
        void setCurrentKeyCode(int code) { this.currentKeyCode = code; }
    }

    /** внутреннее хранилище — LinkedHashMap чтобы сохранять порядок из XML */
    private final LinkedHashMap<String, KeyBind> binds = new LinkedHashMap<>();

    /** jme InputManager (null-safe external) */
    private final InputManager inputManager;

    /** listeners для изменений привязок */
    public interface BindingChangeListener {
        void onBindingChanged(KeyBind oldBind, KeyBind newBind);
    }
    private final List<BindingChangeListener> listeners = new CopyOnWriteArrayList<>();

    /** JSON persistence */
    private final Path storageFile;
    private final Gson gson;

    /**
     * Конструктор с дефолтным файлом хранения (./keybindings.json).
     */
    public KeyBindingsManager(InputManager inputManager) {
        this(inputManager, new File("keybindings.json").toPath());
    }

    /**
     * Конструктор с указанным файлом хранения.
     */
    public KeyBindingsManager(InputManager inputManager, Path storageFile) {
        this.inputManager = inputManager;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        if (storageFile != null) {
            this.storageFile = storageFile;
        } else {
            String userHome = System.getProperty("user.home");
            Path cfg = Paths.get(userHome, ".config", "foxesworld");
            this.storageFile = cfg.resolve("keybindings_v1.json");
        }
    }

    // ================= XML парсинг =================

    /**
     * Загружает определения привязок из XML-стрима.
     * Ожидается корневой элемент <KeyBindings> и элементы <KeyBind id="" action="" defaultKey=""/>
     */
    public void loadDefinitionsFromXml(InputStream xmlStream) throws Exception {
        Objects.requireNonNull(xmlStream);
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlStream);
        Element root = doc.getDocumentElement();
        if (root == null) throw new IllegalArgumentException("Empty XML document");
        if (!"KeyBindings".equals(root.getNodeName())) {
            LOG.warn("Root node is '{}', expected KeyBindings", root.getNodeName());
        }

        NodeList nodes = root.getElementsByTagName("KeyBind");
        LinkedHashMap<String, KeyBind> tmp = new LinkedHashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;

            String id = el.getAttribute("id");
            String action = el.getAttribute("action");
            String defaultKey = el.getAttribute("defaultKey");

            if (id == null || id.isEmpty()) {
                LOG.warn("Skipping KeyBind with missing id (action='{}')", action);
                continue;
            }

            int defaultCode = parseKeyNameToCode(defaultKey);
            KeyBind kb = new KeyBind(id, action, defaultKey, defaultCode);
            tmp.put(id, kb);
        }
        synchronized (binds) {
            binds.clear();
            binds.putAll(tmp);
        }

        // restore user overrides from JSON file
        restoreFromFile();
    }

    // ================= Persistence (JSON) =================

    /** Восстановить (override) сохранённые значения из JSON файла. */
    public void restoreFromFile() {
        if (storageFile == null) { return;}
        if (!Files.exists(storageFile)) {
            saveToFile();
            return;
        }

        try {
            String json = Files.readString(storageFile, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) return;
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> stored = gson.fromJson(json, mapType);
            if (stored == null) return;

            synchronized (binds) {
                for (KeyBind kb : binds.values()) {
                    String val = stored.get(kb.id);
                    if (val == null) continue;
                    int code = parseKeyNameToCode(val);
                    if (code >= 0) {
                        kb.setCurrentKeyCode(code);
                    } else {
                        LOG.warn("Unknown saved key '{}' for bind '{}', ignoring", val, kb.id);
                    }
                }
            }
        } catch (Exception ex) {
            LOG.warn("Failed to restore keybindings from JSON '{}': {}", storageFile.toAbsolutePath(), ex.toString());
        }
    }

    /** Сохраняет текущие привязки в JSON файл. Имена будут в формате KEY_* (при возможности). */
    public void saveToFile() {
        if (storageFile == null) return;
        Map<String, String> out = new LinkedHashMap<>();
        synchronized (binds) {
            for (KeyBind kb : binds.values()) {
                String name = keyCodeToName(kb.getCurrentKeyCode()); // prefixed name if known
                if (name == null) {
                    // convert defaultKey to prefixed form when possible
                    name = convertToPrefixedName(kb.defaultKey);
                }
                if (name != null) out.put(kb.id, name);
            }
        }
        try {
            // ensure parent exists
            Path parent = storageFile.getParent();
            if (parent != null) Files.createDirectories(parent);

            String json = gson.toJson(out);
            Path tmp = storageFile.resolveSibling(storageFile.getFileName().toString() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            try {
                Files.move(tmp, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException amnse) {
                // fallback
                Files.move(tmp, storageFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException io) {
            LOG.warn("Failed to save keybindings to JSON '{}': {}", storageFile.toAbsolutePath(), io.toString());
        }
    }

    /** Сбросить все привязки к дефолтам (и сохранить). */
    public void resetToDefaults() {
        synchronized (binds) {
            for (KeyBind kb : binds.values()) {
                int def = parseKeyNameToCode(kb.defaultKey);
                kb.setCurrentKeyCode(def);
            }
        }
        saveToFile();
        notifyAllBindingsChanged();
    }

    // ================= Query API =================

    public Collection<KeyBind> getAllBinds() {
        synchronized (binds) {
            return Collections.unmodifiableCollection(new ArrayList<>(binds.values()));
        }
    }

    public Optional<KeyBind> getBind(String id) {
        synchronized (binds) {
            return Optional.ofNullable(binds.get(id));
        }
    }

    public Optional<KeyBind> findByKeyCode(int keyCode) {
        synchronized (binds) {
            for (KeyBind kb : binds.values()) {
                if (kb.getCurrentKeyCode() == keyCode) return Optional.of(kb);
            }
            return Optional.empty();
        }
    }

    public boolean isKeyUsed(int keyCode) {
        return findByKeyCode(keyCode).isPresent();
    }

    // ================= Rebinding =================

    /**
     * Переназначить указанную привязку на новый keyCode.
     *
     * @param id             id привязки
     * @param newKeyCode     новый KeyInput код
     * @param swapIfConflict если true и ключ уже занят — выполнит swap между привязками.
     * @return true если операция успешна
     */
    public boolean rebind(String id, int newKeyCode, boolean swapIfConflict) {
        Objects.requireNonNull(id);
        synchronized (binds) {
            KeyBind target = binds.get(id);
            if (target == null) return false;
            int oldCode = target.getCurrentKeyCode();
            if (oldCode == newKeyCode) return true;

            Optional<KeyBind> other = findByKeyCode(newKeyCode);
            if (other.isPresent()) {
                if (!swapIfConflict) {
                    return false; // конфликт и не хотим менять
                }
                KeyBind otherKb = other.get();
                otherKb.setCurrentKeyCode(oldCode);
                target.setCurrentKeyCode(newKeyCode);
                notifyListenersForChange(otherKb.id, otherKb.getCurrentKeyCode(), oldCode);
                notifyListenersForChange(target.id, oldCode, newKeyCode);
            } else {
                target.setCurrentKeyCode(newKeyCode);
                notifyListenersForChange(target.id, oldCode, newKeyCode);
            }
            saveToFile();
            return true;
        }
    }

    // ================= InputManager integration =================

    /** Применить все текущие бинды к InputManager (создаёт или перезаписывает mapping'и). */
    public void applyAllToInputManager() {
        synchronized (binds) {
            for (KeyBind kb : binds.values()) {
                applyBindingToInputManager(kb);
            }
        }
    }

    /** Применить одну привязку к InputManager. */
    public void applyBindingToInputManager(KeyBind kb) {
        if (kb == null) return;
        if (inputManager == null) {
            LOG.debug("InputManager is null — skipping apply for {}", kb.id);
            return;
        }
        String mapping = getMappingName(kb.id);
        try {
            try { inputManager.deleteMapping(mapping); } catch (Exception ignored) {}
            int code = kb.getCurrentKeyCode();
            if (code >= 0) {
                inputManager.addMapping(mapping, new KeyTrigger(code));
            }
        } catch (Exception ex) {
            LOG.warn("Failed to apply binding {} -> {} (mapping {})", kb.id, keyCodeToName(kb.getCurrentKeyCode()), mapping, ex);
        }
    }

    /** Возвращает имя мэппинга, используемое в InputManager для данного id. */
    public static String getMappingName(String id) {
        return MAPPING_PREFIX + id;
    }

    // ================= Listeners API =================

    public void addListener(BindingChangeListener listener) {
        if (listener == null) return;
        listeners.add(listener);
    }

    public void removeListener(BindingChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListenersForChange(String id, int oldCode, int newCode) {
        KeyBind oldKb, newKb;
        synchronized (binds) {
            oldKb = binds.get(id);
            if (oldKb == null) return;
            newKb = new KeyBind(oldKb.id, oldKb.action, oldKb.defaultKey, newCode);
        }
        for (BindingChangeListener l : listeners) {
            try { l.onBindingChanged(oldKb, newKb); } catch (Exception ex) { LOG.warn("Listener failed", ex); }
        }
    }

    private void notifyAllBindingsChanged() {
        synchronized (binds) {
            for (KeyBind kb : binds.values()) {
                notifyListenersForChange(kb.id, -1, kb.getCurrentKeyCode());
            }
        }
    }

    // ================= Key name/code utils =================

    /**
     * Попытаться распарсить строковое имя клавиши (например "KEY_W", "W", "SPACE", "KEY_LSHIFT") в KeyInput код.
     * Возвращает -1 если неизвестно.
     */
    public static int parseKeyNameToCode(String name) {
        if (name == null) return -1;
        String s = name.trim().toUpperCase();
        if (s.isEmpty()) return -1;

        // 1) прямой поиск (поддерживает и "KEY_W" и алиасы "W", "SPACE")
        Integer code = NAME_TO_CODE.get(s);
        if (code != null) return code;

        // 2) если ввели без префикса — попробуем добавить "KEY_" и искать
        if (!s.startsWith("KEY_") && s.length() > 0) {
            code = NAME_TO_CODE.get("KEY_" + s);
            if (code != null) return code;
        }

        return -1;
    }

    /**
     * Возвращает имя по коду в **префиксированном виде** (например "KEY_W") или null, если неизвестно.
     */
    public static String keyCodeToName(int code) {
        return CODE_TO_NAME.get(code);
    }

    /** Попытка конвертации значения defaultKey в префиксированную форму, если возможно. */
    private static String convertToPrefixedName(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase();
        if (s.isEmpty()) return null;
        // если уже в префиксированном виде — вернуть как есть
        if (s.startsWith("KEY_") || s.startsWith("BUTTON_") || s.startsWith("MOUSE_")) return s;
        int code = parseKeyNameToCode(s);
        if (code >= 0) {
            String pref = keyCodeToName(code);
            return pref != null ? pref : s;
        }
        return s;
    }

    /** Опционально: доступ к используемому файлу */
    public Path getStorageFile() {
        return storageFile;
    }
}
