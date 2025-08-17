package org.foxesworld.cge.tmp.menu.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;
import org.foxesworld.cge.tmp.menu.Settings;
import org.foxesworld.cge.tmp.menu.components.KeyBindingsComponent;
import org.foxesworld.cge.tmp.menu.components.UIComponent;
import org.foxesworld.cge.tmp.menu.components.ViceCheckbox;
import org.foxesworld.cge.tmp.menu.components.ViceSlider;

import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Lightweight, non-repetitive SaveSettingsAction.
 * - minimal duplication
 * - clear small helpers for reflection tasks
 * - prefers serializing real instance via Gson
 */
public final class SaveSettingsAction implements MenuAction {

    private MainMenuAppState mainMenuAppState;
    private Class<?> settingsClass;

    @Override
    public void execute(MainMenuAppState mainMenuAppState) {
        this.mainMenuAppState = Objects.requireNonNull(mainMenuAppState, "mainMenuAppState");
        this.settingsClass = mainMenuAppState.getSettingsClass();
        if (settingsClass == null) throw new IllegalStateException("settingsClass is null");

        ensureSettingsInstance();

        // apply UI values to settings instance (or static fields)
        for (UIComponent comp : mainMenuAppState.getBuilder().getContext().allComponents()) {
            String bind = comp.getBind();
            Object value = "";
            if (bind == null || bind.isBlank()) continue;

            if(comp instanceof ViceCheckbox cb) {
                value = cb.isChecked();
            }

            if(comp instanceof ViceSlider vs) {
                value = vs.getValue();
            }

            if(comp instanceof KeyBindingsComponent kc) {
                //System.out.println(kc.getValue());
            }

            try {
                setByPath(mainMenuAppState.getSettingsInstance(), bind.split("\\."), value);
            } catch (Exception e) {
                logErr("Failed to set '%s' : %s", bind, e.toString());
            }
        }

        // write JSON
        try {
            saveJson(mainMenuAppState.getSettingsPath());
            this.mainMenuAppState.getGameEngine().getSoundManager().play("ui.submit");
        } catch (Exception e) {
            logErr("Save JSON failed: %s", e.toString());
        }
    }

    // ---------------------- core: set by dotted path ----------------------

    private void setByPath(Object rootInstance, String[] path, Object value) throws Exception {
        if (path.length == 0) throw new IllegalArgumentException("Empty path");

        Object container = rootInstance;
        Class<?> containerClass = (container != null) ? container.getClass() : settingsClass;

        // walk until container for last segment
        for (int i = 0; i < path.length - 1; i++) {
            String segment = path[i];
            Object next = resolveOrCreateNested(container, containerClass, segment);
            if (next == null) throw new NoSuchFieldException("No nested target for '" + segment + "' in " + containerClass.getName());
            container = next;
            containerClass = container.getClass();
        }

        String last = path[path.length - 1];

        // try field then setter on container
        if (trySetField(container, containerClass, last, value)) return;
        if (tryInvokeSetter(container, containerClass, last, value)) return;

        // last resort: static field on root settingsClass
        Field staticCandidate = findField(settingsClass, last);
        if (staticCandidate != null && Modifier.isStatic(staticCandidate.getModifiers())) {
            setFieldValue(null, staticCandidate, convert(value, staticCandidate.getType()));
            log("Set static %s = %s", last, value);
            return;
        }

        throw new NoSuchFieldException("No field/setter '" + last + "' found in " + containerClass.getName());
    }

    private Object resolveOrCreateNested(Object currentInstance, Class<?> currentClass, String name) throws Exception {
        // 1) field flexible (name, nameSettings, by type simple name)
        Field f = findFieldFlexible(currentClass, name);
        if (f != null) {
            Object nested = getFieldValue(currentInstance, f);
            if (nested == null) {
                nested = createForField(f, currentInstance);
                if (nested != null) assignFieldValue(currentInstance, f, nested);
            }
            return nested;
        }

        // 2) getter + setter (getX/isX + setX)
        Method getter = findGetter(currentClass, name);
        if (getter != null) {
            Object nested = invokeSafe(getter, currentInstance);
            if (nested == null) {
                Class<?> nestedType = getter.getReturnType();
                Object created = createInstance(nestedType, currentInstance);
                if (created != null) {
                    if (!invokeSetterIfExists(currentInstance, currentClass, name, created)) {
                        // fallback: try to find matching field and assign
                        Field pf = findFieldFlexible(currentClass, name);
                        if (pf != null) assignFieldValue(currentInstance, pf, created);
                    }
                    nested = created;
                }
            }
            return nested;
        }

        // 3) nothing found
        return null;
    }

    // ---------------------- try set helpers ----------------------

    private boolean trySetField(Object targetInstance, Class<?> cls, String fieldName, Object value) throws Exception {
        Field f = findField(cls, fieldName);
        if (f == null) return false;
        Object converted = convert(value, f.getType());
        setFieldValue(Modifier.isStatic(f.getModifiers()) ? null : ensureInstance(targetInstance, cls), f, converted);
        log("Set field %s.%s = %s", cls.getSimpleName(), fieldName, String.valueOf(converted));
        return true;
    }

    private boolean tryInvokeSetter(Object targetInstance, Class<?> cls, String prop, Object value) throws Exception {
        Method setter = findSetter(cls, prop);
        if (setter == null) return false;
        Class<?> param = setter.getParameterTypes()[0];
        Object converted = convert(value, param);
        invokeSafe(setter, ensureInstance(targetInstance, cls), converted);
        log("Set via setter %s.%s = %s", cls.getSimpleName(), prop, String.valueOf(converted));
        return true;
    }

    // ---------------------- reflection small tools ----------------------

    private Field findField(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try { return cur.getDeclaredField(name); } catch (NoSuchFieldException ignored) { cur = cur.getSuperclass(); }
        }
        return null;
    }

    private Field findFieldFlexible(Class<?> cls, String prop) {
        Field f = findField(cls, prop);
        if (f != null) return f;
        f = findField(cls, prop + "Settings");
        if (f != null) return f;
        // by simple type name
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            for (Field ff : cur.getDeclaredFields()) {
                if (ff.getType() != null && ff.getType().getSimpleName().equalsIgnoreCase(prop)) return ff;
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private Method findGetter(Class<?> cls, String prop) {
        String cap = capitalize(prop);
        Method m = findMethod(cls, "get" + cap);
        return m != null ? m : findMethod(cls, "is" + cap);
    }

    private Method findSetter(Class<?> cls, String prop) {
        String cap = capitalize(prop);
        // public methods first
        for (Method m : cls.getMethods()) if (m.getName().equals("set" + cap) && m.getParameterCount() == 1) return m;
        // declared methods next
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            for (Method m : cur.getDeclaredMethods()) if (m.getName().equals("set" + cap) && m.getParameterCount() == 1) return m;
            cur = cur.getSuperclass();
        }
        return null;
    }

    private Method findMethod(Class<?> cls, String name) {
        try { return cls.getDeclaredMethod(name); } catch (NoSuchMethodException ignored) {}
        try { return cls.getMethod(name); } catch (NoSuchMethodException ignored) {}
        return null;
    }

    private Object getFieldValue(Object instance, Field f) throws IllegalAccessException {
        f.setAccessible(true);
        return Modifier.isStatic(f.getModifiers()) ? f.get(null) : (instance == null ? null : f.get(instance));
    }

    private void setFieldValue(Object instanceOrNull, Field f, Object value) throws IllegalAccessException {
        f.setAccessible(true);
        if (Modifier.isStatic(f.getModifiers())) f.set(null, value);
        else f.set(instanceOrNull, value);
    }

    private void assignFieldValue(Object onInstance, Field f, Object value) {
        try {
            f.setAccessible(true);
            if (Modifier.isStatic(f.getModifiers())) f.set(null, value);
            else {
                Object inst = ensureInstance(onInstance, f.getDeclaringClass());
                f.set(inst, value);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean invokeSetterIfExists(Object instance, Class<?> cls, String prop, Object arg) {
        Method setter = findSetter(cls, prop);
        if (setter == null) return false;
        invokeSafe(setter, instance, arg);
        return true;
    }

    private Object invokeSafe(Method m, Object instance, Object... args) {
        try {
            if (Modifier.isStatic(m.getModifiers())) return m.invoke(null, args);
            if (instance == null) return m.invoke(null, args);
            return m.invoke(instance, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------- creation helpers ----------------------

    private Object createForField(Field f, Object outerInstance) {
        Class<?> t = f.getType();
        return createInstance(t, outerInstance);
    }

    private Object createInstance(Class<?> clazz, Object outerInstance) {
        if (clazz == null) return null;
        try {
            Constructor<?> noArg = clazz.getDeclaredConstructor();
            noArg.setAccessible(true);
            return noArg.newInstance();
        } catch (NoSuchMethodException ignored) {
            // try single-arg constructor matching outerInstance
            if (outerInstance != null) {
                for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                    Class<?>[] ps = ctor.getParameterTypes();
                    if (ps.length == 1 && ps[0].isAssignableFrom(outerInstance.getClass())) {
                        try {
                            ctor.setAccessible(true);
                            return ctor.newInstance(outerInstance);
                        } catch (Throwable ignored2) {}
                    }
                }
            }
            // fallback: any zero-arg declared constructor attempt
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                if (ctor.getParameterCount() == 0) {
                    try { ctor.setAccessible(true); return ctor.newInstance(); } catch (Throwable ignored2) {}
                }
            }
            return null;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private Object ensureInstance(Object instance, Class<?> cls) {
        if (instance != null) return instance;
        Object created = createInstance(cls, null);
        if (created == null) throw new RuntimeException("Cannot create instance for " + cls.getName());
        return created;
    }

    // ---------------------- conversion ----------------------

    private Object convert(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        if (targetType.isEnum()) {
            @SuppressWarnings("unchecked") Class<Enum> ec = (Class<Enum>) targetType;
            return Enum.valueOf(ec, value.toString());
        }

        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value.toString());

        if (targetType == String.class) return value.toString();

        // numeric conversions
        if (Number.class.isAssignableFrom(primitiveWrapperOf(targetType)) || targetType.isPrimitive()) {
            if (value instanceof Number n) return convertNumber(n, targetType);
            String s = value.toString();
            try {
                if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(s);
                if (targetType == long.class || targetType == Long.class) return Long.parseLong(s);
                if (targetType == float.class || targetType == Float.class) return Float.parseFloat(s);
                if (targetType == double.class || targetType == Double.class) return Double.parseDouble(s);
                if (targetType == short.class || targetType == Short.class) return Short.parseShort(s);
                if (targetType == byte.class || targetType == Byte.class) return Byte.parseByte(s);
                if (targetType == char.class || targetType == Character.class) return s.isEmpty() ? '\0' : s.charAt(0);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Cannot convert '" + s + "' to " + targetType.getSimpleName(), ex);
            }
        }

        return value;
    }

    private Object convertNumber(Number n, Class<?> t) {
        if (t == int.class || t == Integer.class) return n.intValue();
        if (t == long.class || t == Long.class) return n.longValue();
        if (t == float.class || t == Float.class) return n.floatValue();
        if (t == double.class || t == Double.class) return n.doubleValue();
        if (t == short.class || t == Short.class) return n.shortValue();
        if (t == byte.class || t == Byte.class) return n.byteValue();
        if (t == char.class || t == Character.class) return (char) n.intValue();
        return n;
    }

    private Class<?> primitiveWrapperOf(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == boolean.class) return Boolean.class;
        if (c == char.class) return Character.class;
        return c;
    }

    private String capitalize(String s) { return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    // ---------------------- serialization ----------------------

    private void saveJson(Path out) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Object instance = mainMenuAppState.getSettingsInstance();

        String json;
        if (instance != null) {
            json = gson.toJson(instance);
            write(out, json);
            log("JSON (instance) saved to %s", out.toAbsolutePath());
            return;
        }

        // fallback - produce map of static fields + (temp) instance fields
        Map<String, Object> result = new LinkedHashMap<>();
        // static fields
        for (Field f : settingsClass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(null);
                if (v != null) {
                    String name = f.getName();
                    if (name.endsWith("Settings")) name = name.substring(0, name.length() - "Settings".length());
                    result.put(name, v);
                }
            } catch (Throwable ignored) {}
        }

        // try temp instance
        Object tmp = null;
        try { tmp = settingsClass.getDeclaredConstructor().newInstance(); } catch (Throwable ignored) {}
        if (tmp != null) {
            Class<?> cur = tmp.getClass();
            while (cur != null && cur != Object.class) {
                for (Field f : cur.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(tmp);
                        if (v != null) result.putIfAbsent(f.getName(), v);
                    } catch (Throwable ignored) {}
                }
                cur = cur.getSuperclass();
            }
        }

        json = gson.toJson(result.isEmpty() ? (tmp != null ? tmp : Collections.emptyMap()) : result);
        write(out, json);
        log("JSON (fallback) saved to %s", out.toAbsolutePath());
    }

    private void write(Path out, String s) throws IOException {
        Path parent = (out.getParent() == null) ? Path.of(".") : out.getParent();
        Files.createDirectories(parent);
        Files.writeString(out, s, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // ---------------------- small helpers / debug ----------------------

    private void ensureSettingsInstance() {
        if (mainMenuAppState.getSettingsInstance() != null) return;
        try {
            Object inst = settingsClass.getDeclaredConstructor().newInstance();
            if (inst instanceof Settings) mainMenuAppState.setSettingsInstance((Settings) inst);
            else mainMenuAppState.setSettingsInstance((Settings) inst); // best-effort cast (keeps previous API)
            log("Created settings instance");
        } catch (Throwable t) {
            mainMenuAppState.setSettingsInstance(null);
            logErr("Cannot create settings instance: %s", t.toString());
        }
    }

    public void debugDumpSettings() {
        Object inst = mainMenuAppState == null ? null : mainMenuAppState.getSettingsInstance();
        if (inst == null) { logErr("dump: settingsInstance == null"); return; }
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(inst);
        log("DEBUG DUMP:\n%s", json);
    }

    private void log(String fmt, Object... args) { System.out.println("[SAVE SETTINGS] " + String.format(fmt, args)); }
    private void logErr(String fmt, Object... args) { System.err.println("[SAVE SETTINGS] " + String.format(fmt, args)); }
}