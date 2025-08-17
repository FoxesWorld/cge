package org.foxesworld.cge.tmp.menu.input;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.KeyTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

/**
 * Менеджер привязок клавиш.
 *
 * Поддерживает:
 *  - загрузку определений из XML (KeyBindings -> KeyBind elements),
 *  - хранение текущих привязок (и их сохранение в Preferences),
 *  - применение привязок в JME InputManager,
 *  - переназначение клавиш с опцией swap при конфликте,
 *  - слушатели изменений.
 */
public final class KeyBindingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(KeyBindingsManager.class);

    // node в Preferences для сохранения пользовательских привязок
    private static final String PREFS_NODE = "/org/foxesworld/cge/keybindings_v1";

    // префикс для mapping names в InputManager
    private static final String MAPPING_PREFIX = "kb_action_";

    // отражённые мапы имени -> keyCode и обратно
    private static final Map<String, Integer> NAME_TO_CODE = new HashMap<>();
    private static final Map<Integer, String> CODE_TO_NAME = new HashMap<>();

    static {
        // Инициализация мапов из KeyInput.* (KEY_A, KEY_0, KEY_RETURN и т.д.)
        try {
            for (Field f : KeyInput.class.getFields()) {
                String fname = f.getName();
                if (!fname.startsWith("KEY_")) continue;
                int code = f.getInt(null);
                String nice = fname.substring(4); // KEY_A -> A
                NAME_TO_CODE.put(nice, code);
                CODE_TO_NAME.put(code, nice);
            }
            // дополнительные синонимы
            NAME_TO_CODE.putIfAbsent("SPACE", KeyInput.KEY_SPACE);
            NAME_TO_CODE.putIfAbsent("ENTER", KeyInput.KEY_RETURN);
            NAME_TO_CODE.putIfAbsent("RETURN", KeyInput.KEY_RETURN);
            NAME_TO_CODE.putIfAbsent("ESC", KeyInput.KEY_ESCAPE);
            NAME_TO_CODE.putIfAbsent("ESCAPE", KeyInput.KEY_ESCAPE);
            NAME_TO_CODE.putIfAbsent("LEFT", KeyInput.KEY_LEFT);
            NAME_TO_CODE.putIfAbsent("RIGHT", KeyInput.KEY_RIGHT);
            NAME_TO_CODE.putIfAbsent("UP", KeyInput.KEY_UP);
            NAME_TO_CODE.putIfAbsent("DOWN", KeyInput.KEY_DOWN);
            // mirror into CODE_TO_NAME if missing
            for (Map.Entry<String, Integer> e : NAME_TO_CODE.entrySet()) {
                CODE_TO_NAME.putIfAbsent(e.getValue(), e.getKey());
            }
        } catch (Exception ex) {
            LOG.warn("KeyInput reflection initialization failed", ex);
        }
    }

    // Модель одной привязки
    public static final class KeyBind {
        public final String id;         // уникальный id (из XML)
        public final String action;     // человекочитаемое описание действия
        public final String defaultKey; // строковое имя дефолтной клавиши, как в XML
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

    // внутреннее хранилище — LinkedHashMap чтобы сохранять порядок из XML
    private final LinkedHashMap<String, KeyBind> binds = new LinkedHashMap<>();

    // preferences node
    private final Preferences prefs = Preferences.userRoot().node(PREFS_NODE);

    // jme InputManager (null-safe external)
    private final InputManager inputManager;

    // listeners для изменений привязок
    public interface BindingChangeListener {
        void onBindingChanged(KeyBind oldBind, KeyBind newBind);
    }
    private final List<BindingChangeListener> listeners = new CopyOnWriteArrayList<>();

    public KeyBindingsManager(InputManager inputManager) {
        this.inputManager = inputManager;
    }

    // ========== XML парсинг ==========

    /**
     * Загружает определения привязок из XML-стрима.
     * Ожидается корневой элемент <KeyBindings> и элементы <KeyBind id="" action="" defaultKey=""/>
     * После загрузки будут применены сохранённые в Preferences переопределения.
     *
     * @param xmlStream InputStream XML (будет прочитан)
     * @throws Exception в случае ошибок парсинга
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

        // restore user overrides
        restoreFromPreferences();
    }

    // ========== Persistence ==========

    /**
     * Восстановить (override) сохранённые значения из Preferences.
     * Для каждого KeyBind ищем запись prefs.get(id) и, если валидный ключ, применяем.
     */
    public void restoreFromPreferences() {
        synchronized (binds) {
            for (KeyBind kb : binds.values()) {
                String stored = prefs.get(kb.id, null);
                if (stored == null) continue;
                int code = parseKeyNameToCode(stored);
                if (code >= 0) {
                    kb.setCurrentKeyCode(code);
                } else {
                    LOG.warn("Unknown saved key '{}' for bind '{}', ignoring", stored, kb.id);
                }
            }
        }
    }

    /**
     * Сохраняет текущие привязки (в виде строковых имён клавиш) в Preferences.
     */
    public void saveToPreferences() {
        synchronized (binds) {
            for (KeyBind kb : binds.values()) {
                String name = keyCodeToName(kb.getCurrentKeyCode());
                if (name == null) name = kb.defaultKey;
                if (name != null) prefs.put(kb.id, name);
            }
            try { prefs.flush(); } catch (Exception ignored) {}
        }
    }

    /**
     * Сбросить все привязки к дефолтам (и сохранить).
     */
    public void resetToDefaults() {
        synchronized (binds) {
            for (KeyBind kb : binds.values()) {
                int def = parseKeyNameToCode(kb.defaultKey);
                kb.setCurrentKeyCode(def);
            }
        }
        saveToPreferences();
        notifyAllBindingsChanged();
    }

    // ========== Query API ==========

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

    // ========== Rebinding ==========

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
                // notify listeners about both changes
                notifyListenersForChange(otherKb.id, oldCode, otherKb.getCurrentKeyCode());
                notifyListenersForChange(target.id, oldCode, newKeyCode); // oldCode here is previous target code
            } else {
                target.setCurrentKeyCode(newKeyCode);
                notifyListenersForChange(target.id, oldCode, newKeyCode);
            }
            // persist change
            saveToPreferences();
            return true;
        }
    }

    // ========== InputManager integration ==========

    /**
     * Применить все текущие бинды к InputManager (создаёт или перезаписывает mapping'и).
     * Это НЕ регистрирует ActionListener — ожидается, что вызывающий зарегистрирует ActionListener
     * и обрабатывает события по mappingName = getMappingName(id).
     */
    public void applyAllToInputManager() {
        synchronized (binds) {
            for (KeyBind kb : binds.values()) {
                applyBindingToInputManager(kb);
            }
        }
    }

    /**
     * Применить одну привязку: удаляет старый mapping (если есть) и создаёт новый с KeyTrigger.
     *
     * @param kb KeyBind
     */
    public void applyBindingToInputManager(KeyBind kb) {
        if (kb == null) return;
        if (inputManager == null) {
            LOG.debug("InputManager is null — skipping apply for {}", kb.id);
            return;
        }
        String mapping = getMappingName(kb.id);
        try {
            // safely delete old mapping and add new
            try { inputManager.deleteMapping(mapping); } catch (Exception ignored) {}
            int code = kb.getCurrentKeyCode();
            if (code >= 0) {
                inputManager.addMapping(mapping, new KeyTrigger(code));
            }
        } catch (Exception ex) {
            LOG.warn("Failed to apply binding {} -> {} (mapping {})", kb.id, keyCodeToName(kb.getCurrentKeyCode()), mapping, ex);
        }
    }

    /**
     * Возвращает имя мэппинга, используемое в InputManager для данного id.
     */
    public static String getMappingName(String id) {
        return MAPPING_PREFIX + id;
    }

    // ========== Listeners API ==========

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
            // make snapshots
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

    // ========== Key name/code utilities ==========

    /**
     * Попытаться распарсить строковое имя клавиши (например "P", "F1", "SPACE", "RETURN") в KeyInput код.
     * Возвращает -1 если неизвестно.
     */
    public static int parseKeyNameToCode(String name) {
        if (name == null) return -1;
        String s = name.trim().toUpperCase();
        if (s.isEmpty()) return -1;

        // одиночный символ A..Z / 0..9
        if (s.length() == 1) {
            char c = s.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return NAME_TO_CODE.getOrDefault(String.valueOf(c), -1);
            }
            if (c >= '0' && c <= '9') {
                return NAME_TO_CODE.getOrDefault(String.valueOf(c), -1);
            }
        }

        // прямое совпадение
        Integer code = NAME_TO_CODE.get(s);
        if (code != null) return code;

        // убираем неячественные символы (например "KeyP" -> "P")
        String stripped = s.replaceAll("[^A-Z0-9]+", "");
        if (!stripped.isEmpty()) {
            code = NAME_TO_CODE.get(stripped);
            if (code != null) return code;
        }
        return -1;
    }

    /**
     * Получить текстовое имя клавиши по коду, либо null если неизвестно.
     */
    public static String keyCodeToName(int code) {
        return CODE_TO_NAME.get(code);
    }
}
