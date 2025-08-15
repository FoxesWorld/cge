package org.foxesworld.cge.core.io;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;

import java.io.*;
import java.util.*;

/**
 * KeyBindManager — лёгкий менеджер биндов клавиш.
 *
 * <p>Особенности:
 * <ul>
 *   <li>Хранит бинды в виде Map<String action, Integer keyCode> (KeyInput.KEY_*).</li>
 *   <li>Поддерживает "capture mode": startListeningFor(action) — ждёт следующего нажатия клавиши и назначает.</li>
 *   <li>Применяет бинды в InputManager как mappings с префиксом {@code kb:action} и {@link KeyTrigger}.</li>
 *   <li>Сохраняет/загружает бинды в Properties-файл (action=keyCode).</li>
 *   <li>Ретранслирует события action'ов внешним слушателям {@link KeyBindListener}.</li>
 * </ul>
 *
 * Замечание: пока реализована поддержка одиночных клавиш (без модификаторов). Для комбинаций можно
 * расширить модель (структура Bind с булевыми флагами для Ctrl/Shift/Alt).
 */
public class KeyBindManager implements RawInputListener {

    /**
     * Слушатель биндов: вызывается при срабатывании action-а (через InputManager mappings).
     */
    public interface KeyBindListener {
        void onAction(String action, boolean isPressed, float tpf);
    }

    private static final String MAPPING_PREFIX = "kb:";

    private final InputManager inputManager;
    // action -> keyCode (KeyInput.KEY_*)
    private final Map<String, Integer> binds = new LinkedHashMap<>();

    // listeners to forward action events to
    private final List<KeyBindListener> listeners = Collections.synchronizedList(new ArrayList<>());

    // actionForwarder forwards InputManager action events to our listeners
    private final ActionListener actionForwarder = (name, isPressed, tpf) -> {
        // name will be MAPPING_PREFIX + action
        String action = (name != null && name.startsWith(MAPPING_PREFIX)) ? name.substring(MAPPING_PREFIX.length()) : name;
        synchronized (listeners) {
            for (KeyBindListener l : listeners) {
                try { l.onAction(action, isPressed, tpf); } catch (Exception ignored) {}
            }
        }
    };

    // capture-mode: if non-null, we wait for next key press and bind it to actionToCapture
    private String actionToCapture = null;

    /**
     * Создаёт KeyBindManager и регистрирует RawInputListener в переданном InputManager.
     *
     * @param inputManager не null
     */
    public KeyBindManager(InputManager inputManager) {
        if (inputManager == null) throw new IllegalArgumentException("inputManager == null");
        this.inputManager = inputManager;
        this.inputManager.addRawInputListener(this);
    }

    /* ===========================
       CRUD для биндов
       =========================== */

    /** Возвращает keyCode, назначенный на action, или -1 если нет. */
    public int getBind(String action) {
        return binds.getOrDefault(action, -1);
    }

    /** Назначить keyCode (KeyInput.KEY_*) на action. Немедленно применяет mapping в InputManager. */
    public void setBind(String action, int keyCode) {
        if (action == null) throw new IllegalArgumentException("action == null");
        if (keyCode < 0) {
            removeBind(action);
            return;
        }
        binds.put(action, keyCode);
        applyBinding(action, keyCode);
    }

    /** Удалить bind (и mapping) для action. */
    public void removeBind(String action) {
        if (action == null) return;
        binds.remove(action);
        removeMapping(action);
    }

    /** Вернуть все действия (в порядке добавления). */
    public List<String> getActions() {
        return new ArrayList<>(binds.keySet());
    }

    /** Количество биндов. */
    public int getBindCount() {
        return binds.size();
    }

    /** Полностью очистить все бинды и mappings. */
    public void clearAll() {
        for (String action : new ArrayList<>(binds.keySet())) {
            removeMapping(action);
        }
        binds.clear();
    }

    /* ===========================
       Capture mode (назначение следующего нажатия)
       =========================== */

    /**
     * Начать слушать следующую клавишу и назначить её на action.
     * Если уже в режиме назначения — перезаписывает action, для которого идёт ожидание.
     *
     * @param action действие (например "open_inventory.primary")
     */
    public void startListeningFor(String action) {
        this.actionToCapture = action;
    }

    /** Отменить режим назначения. */
    public void stopListening() {
        this.actionToCapture = null;
    }

    /** Возвращает action, для которого сейчас идёт назначение, или null. */
    public String getListeningAction() {
        return actionToCapture;
    }

    /* ===========================
       Применение биндов в InputManager
       =========================== */

    /** Применить (пересоздать) все mappings в InputManager. */
    public void applyAllBindings() {
        // удаляем старые наши mappings
        for (String action : new ArrayList<>(binds.keySet())) {
            removeMapping(action);
        }
        // создаём снова
        for (Map.Entry<String, Integer> e : binds.entrySet()) {
            applyBinding(e.getKey(), e.getValue());
        }
    }

    private void applyBinding(String action, int keyCode) {
        if (action == null) return;
        String mapping = mappingNameFor(action);
        // delete old mapping if exists
        if (inputManager.hasMapping(mapping)) {
            try { inputManager.deleteMapping(mapping); } catch (Exception ignored) {}
        }
        if (keyCode <= 0) return; // ignore invalid
        inputManager.addMapping(mapping, new KeyTrigger(keyCode));
        inputManager.addListener(actionForwarder, mapping);
    }

    private void removeMapping(String action) {
        if (action == null) return;
        String mapping = mappingNameFor(action);
        if (inputManager.hasMapping(mapping)) {
            try {
                // best-effort: remove listener then delete mapping
                inputManager.removeListener(actionForwarder);
            } catch (Exception ignored) {}
            try { inputManager.deleteMapping(mapping); } catch (Exception ignored) {}
        }
    }

    private static String mappingNameFor(String action) {
        return MAPPING_PREFIX + action;
    }

    /* ===========================
       Persistence (properties)
       =========================== */

    /**
     * Сохранить бинды в properties-файл: ключ = action, значение = keyCode (int).
     * @param file файл для записи
     * @throws IOException при ошибке ввода-вывода
     */
    public void saveToFile(File file) throws IOException {
        Properties p = new Properties();
        for (Map.Entry<String, Integer> e : binds.entrySet()) {
            p.setProperty(e.getKey(), Integer.toString(e.getValue()));
        }
        try (OutputStream os = new FileOutputStream(file)) {
            p.store(os, "Key bindings (action=keyCode)");
        }
    }

    /**
     * Загрузить бинды из properties-файла. Существующие бинды будут заменены.
     * @param file файл для чтения
     * @throws IOException при ошибке ввода-вывода
     */
    public void loadFromFile(File file) throws IOException {
        if (file == null || !file.exists()) return;
        Properties p = new Properties();
        try (InputStream is = new FileInputStream(file)) {
            p.load(is);
        }
        binds.clear();
        for (String name : p.stringPropertyNames()) {
            String val = p.getProperty(name);
            try {
                int key = Integer.parseInt(val);
                binds.put(name, key);
            } catch (NumberFormatException ex) {
                // skip invalid entries
            }
        }
        applyAllBindings();
    }

    /* ===========================
       Listeners API
       =========================== */

    public void addKeyBindListener(KeyBindListener l) {
        if (l == null) return;
        listeners.add(l);
    }

    public void removeKeyBindListener(KeyBindListener l) {
        if (l == null) return;
        listeners.remove(l);
    }

    /* ===========================
       RawInputListener — для capture-mode
       =========================== */

    @Override
    public void onKeyEvent(KeyInputEvent evt) {
        if (actionToCapture == null) return;
        if (!evt.isPressed()) return;

        int kc = evt.getKeyCode();
        String action = actionToCapture;
        // назначим бинду и сразу применим mapping
        setBind(action, kc);

        // выйти из capture-mode
        actionToCapture = null;
    }

    // unused RawInputListener methods (no-op implementations)
    @Override public void beginInput() {}
    @Override public void endInput() {}
    @Override public void onJoyAxisEvent(JoyAxisEvent evt) {}
    @Override public void onJoyButtonEvent(JoyButtonEvent evt) {}
    @Override public void onMouseMotionEvent(MouseMotionEvent evt) {}
    @Override public void onMouseButtonEvent(MouseButtonEvent evt) {}
    @Override public void onTouchEvent(TouchEvent evt) {}

    /**
     * Должен быть вызван при уничтожении менеджера — удаляет raw-listener и mappings.
     */
    public void destroy() {
        try { inputManager.removeRawInputListener(this); } catch (Exception ignored) {}
        // удалить mappings
        for (String action : new ArrayList<>(binds.keySet())) {
            removeMapping(action);
        }
        binds.clear();
        listeners.clear();
    }

    /* ===========================
       Утилиты
       =========================== */

    /**
     * Преобразовать KeyInput.KEY_* в удобочитаемое имя. Базовая реализация, расширяй по вкусу.
     * @param keyCode код клавиши
     * @return строковое представление
     */
    public static String keyCodeToString(int keyCode) {
        if (keyCode >= KeyInput.KEY_A && keyCode <= KeyInput.KEY_Z) {
            return Character.toString((char) ('A' + (keyCode - KeyInput.KEY_A)));
        }
        if (keyCode >= KeyInput.KEY_0 && keyCode <= KeyInput.KEY_9) {
            return Character.toString((char) ('0' + (keyCode - KeyInput.KEY_0)));
        }
        switch (keyCode) {
            case KeyInput.KEY_SPACE: return "SPACE";
            case KeyInput.KEY_LSHIFT: case KeyInput.KEY_RSHIFT: return "SHIFT";
            case KeyInput.KEY_LCONTROL: case KeyInput.KEY_RCONTROL: return "CTRL";
            case KeyInput.KEY_LMENU: case KeyInput.KEY_RMENU: return "ALT";
            case KeyInput.KEY_ESCAPE: return "ESC";
            case KeyInput.KEY_RETURN: return "ENTER";
            case KeyInput.KEY_TAB: return "TAB";
            case KeyInput.KEY_BACK: return "BACKSPACE";
            default: return "KEY_" + keyCode;
        }
    }
}