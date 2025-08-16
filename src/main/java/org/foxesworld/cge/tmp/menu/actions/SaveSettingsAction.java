package org.foxesworld.cge.tmp.menu.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jme3.app.Application;
import org.foxesworld.cge.tmp.menu.MainMenuAppState;
import org.foxesworld.cge.tmp.menu.components.UIComponent;
import org.foxesworld.cge.tmp.menu.components.ViceCheckbox;
import org.foxesworld.cge.tmp.menu.components.ViceSlider;
import org.foxesworld.cge.tmp.menu.Settings;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A more compact and optimized version of SaveSettingsAction.
 * - Less reflection duplication
 * - Reliable assembly of the serializable object (static + instance)
 * - Correct JSON writing via Gson
 * - Clearer logs for debugging
 */
public class SaveSettingsAction implements MenuAction {

    private Class<?> settingsClass;
    private MainMenuAppState mainMenuAppState;

    @Override
    public void execute(MainMenuAppState mainMenuAppState) {
        this.mainMenuAppState = mainMenuAppState;
        if (mainMenuAppState == null) throw new IllegalStateException("MainMenuAppState not found");

        settingsClass = mainMenuAppState.getSettingsClass();
        if (settingsClass == null) throw new IllegalStateException("settingsClass is null");

        tryCreateInstance();

        for (UIComponent component : mainMenuAppState.getBuilder().getContext().allComponents()) {
            String bind = component.getBind();
            if (bind == null || bind.isBlank()) continue;

            Object value = (component instanceof ViceCheckbox c) ? c.isChecked()
                    : (component instanceof ViceSlider s) ? s.getValue() : null;

            if (value == null) continue;

            String[] path = bind.split("\\.");
            try {
                setFieldValue(mainMenuAppState.getSettingsInstance(), path, value);
            } catch (Exception ex) {
                logErr("Failed to set %s: %s", bind, ex.getMessage());
            }
        }


        try {
            saveSettingsJsonToFileWithGson(mainMenuAppState.getSettingsPath());
        } catch (Exception e) {
            logErr("Error saving JSON: %s", e.getMessage());
        }
    }

    // ---------------------- core: setFieldValue ----------------------

    private void setFieldValue(Object rootInstance, String[] path, Object value) throws Exception {
        if (path.length == 0) throw new IllegalArgumentException("Empty path");

        Object currentInstance = rootInstance;
        Class<?> currentClass = (rootInstance != null) ? rootInstance.getClass() : settingsClass;

        // Walk all parts of the path except the last
        for (int i = 0; i < path.length - 1; i++) {
            String part = path[i];

            Field f = findFieldFlexible(currentClass, part);

            if (f == null) {
                Method getter = findGetterFlexible(currentClass, part);
                if (getter != null) {
                    Object nested = invokeGetter(getter, currentInstance);
                    if (nested == null) {
                        Class<?> nestedType = getter.getReturnType();
                        nested = createAndAssignNested(currentInstance, currentClass, part, nestedType, getter);
                    }
                    if (nested == null) throw new RuntimeException("Failed to obtain nested instance for " + part);
                    currentInstance = nested;
                    currentClass = currentInstance.getClass();
                    continue;
                }
                throw new NoSuchFieldException("Nested field '" + part + "' not found in " + currentClass.getName());
            }

            f.setAccessible(true);
            boolean isStatic = Modifier.isStatic(f.getModifiers());
            Object nestedInstance = isStatic ? f.get(null) : ensureInstanceAndGetField(currentInstance, currentClass, f);

            if (nestedInstance == null) {
                Object created = createInstance(f.getType(), currentInstance);
                if (created != null) {
                    assignField(f, currentInstance, created, isStatic);
                    nestedInstance = created;
                }
            }

            if (nestedInstance == null) throw new RuntimeException("Nested instance is null for " + part);

            currentInstance = nestedInstance;
            currentClass = currentInstance.getClass();
        }

        // Set the last field
        String last = path[path.length - 1];

        Field target = findFieldInClassHierarchy(currentClass, last);
        if (target != null) {
            target.setAccessible(true);
            Object converted = convertValueToFieldType(value, target.getType());
            if (Modifier.isStatic(target.getModifiers())) target.set(null, converted);
            else {
                currentInstance = ensureInstance(currentInstance, currentClass);
                target.set(currentInstance, converted);
            }
            log("Set %s = %s", String.join(".", path), converted);
            return;
        }

        Method setter = findSetterFlexible(currentClass, last);
        if (setter != null) {
            Class<?> param = setter.getParameterTypes()[0];
            Object converted = convertValueToFieldType(value, param);
            invokeSetter(setter, ensureInstance(currentInstance, currentClass), converted);
            log("Set via setter %s = %s", String.join(".", path), converted);
            return;
        }

        // Final attempt — static field in the root class
        Field staticCandidate = findFieldInClassHierarchy(settingsClass, last);
        if (staticCandidate != null && Modifier.isStatic(staticCandidate.getModifiers())) {
            staticCandidate.setAccessible(true);
            Object converted = convertValueToFieldType(value, staticCandidate.getType());
            staticCandidate.set(null, converted);
            log("Set static %s = %s", last, converted);
            return;
        }

        throw new NoSuchFieldException("Field/setter '" + last + "' not found in " + currentClass.getName());
    }

    // ---------------------- serialization ----------------------

    private void saveSettingsJsonToFileWithGson(Path out) throws IOException {
        Object toSerialize = buildSerializableSettingsObject();
        if (toSerialize == null) throw new IllegalStateException("No settings object to serialize");

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(toSerialize);

        Path parent = (out.getParent() == null) ? Path.of(".") : out.getParent();
        Files.createDirectories(parent);
        Files.writeString(out, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log("JSON saved to %s", out.toAbsolutePath());
    }

    /**
     * Collect a Map from static fields (graphicsSettings -> graphics) and supplement them with instance fields if present.
     */
    private Object buildSerializableSettingsObject() {
        if (settingsClass == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();

        // static fields
        for (Field f : settingsClass.getDeclaredFields()) {
            try {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object val = f.get(null);
                if (val == null) continue;
                String name = f.getName();
                if (name.endsWith("Settings")) name = name.substring(0, name.length() - "Settings".length());
                result.put(name, val);
            } catch (Throwable t) {
                logErr("skip static field %s: %s", f.getName(), t.getMessage());
            }
        }

        // instance fields override/complete static ones
        Object toRead = mainMenuAppState.getSettingsInstance();
        if (toRead == null) {
            try {
                toRead = settingsClass.getDeclaredConstructor().newInstance();
            } catch (Exception ignored) {
                toRead = null;
            }
        }

        if (toRead != null) {
            Class<?> cur = toRead.getClass();
            while (cur != null && cur != Object.class) {
                for (Field f : cur.getDeclaredFields()) {
                    try {
                        if (Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        Object val = f.get(toRead);
                        if (val != null) result.putIfAbsent(f.getName(), val);
                    } catch (Throwable ignored) {
                    }
                }
                cur = cur.getSuperclass();
            }
        }

        if (result.isEmpty()) return (mainMenuAppState.getSettingsInstance() != null) ? mainMenuAppState.getSettingsInstance() : result;
        return result;
    }

    // ---------------------- small helpers ----------------------

    private void tryCreateInstance() {
        if (mainMenuAppState.getSettingsInstance() != null) return;
        try {
            mainMenuAppState.setSettingsInstance((Settings) settingsClass.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            mainMenuAppState.setSettingsInstance(null);
            logErr("Failed to create instance: %s", e.getMessage());
        }
    }

    private Field findFieldInClassHierarchy(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(name);
                return f;
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private Field findFieldFlexible(Class<?> cls, String prop) {
        Field f = findFieldInClassHierarchy(cls, prop);
        if (f != null) return f;
        f = findFieldInClassHierarchy(cls, prop + "Settings");
        if (f != null) return f;
        return findFieldByTypeSimpleName(cls, prop);
    }

    private Field findFieldByTypeSimpleName(Class<?> cls, String simpleName) {
        Class<?> cur = cls;
        while (cur != null) {
            for (Field f : cur.getDeclaredFields()) {
                if (f.getType() != null && f.getType().getSimpleName().equalsIgnoreCase(simpleName)) return f;
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private Method findGetterFlexible(Class<?> cls, String prop) {
        String cap = capitalize(prop);
        Method m = findMethod(cls, "get" + cap);
        if (m != null) return m;
        m = findMethod(cls, "is" + cap);
        return m;
    }

    private Method findSetterFlexible(Class<?> cls, String prop) {
        String cap = capitalize(prop);
        // try to find any setX with one parameter
        for (Method m : cls.getMethods()) {
            if (m.getName().equals("set" + cap) && m.getParameterCount() == 1) return m;
        }
        Class<?> cur = cls;
        while (cur != null) {
            for (Method m : cur.getDeclaredMethods()) {
                if (m.getName().equals("set" + cap) && m.getParameterCount() == 1) return m;
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private Method findMethod(Class<?> cls, String name) {
        try {
            Method m = cls.getDeclaredMethod(name);
            return m;
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return cls.getMethod(name);
        } catch (NoSuchMethodException ignored) {
        }
        return null;
    }

    private Object invokeGetter(Method getter, Object instance) {
        try {
            if (Modifier.isStatic(getter.getModifiers())) return getter.invoke(null);
            if (instance == null) return getter.invoke(null);
            return getter.invoke(instance);
        } catch (Exception e) {
            return null;
        }
    }

    private Object createAndAssignNested(Object currentInstance, Class<?> currentClass, String prop, Class<?> nestedType, Method getter) {
        Object created = createInstance(nestedType, currentInstance);
        if (created == null) return null;
        // try setter
        Method setter = findSetterFlexible(currentClass, prop);
        try {
            if (setter != null) {
                if (Modifier.isStatic(setter.getModifiers())) setter.invoke(null, created);
                else {
                    Object inst = ensureInstance(currentInstance, currentClass);
                    setter.invoke(inst, created);
                }
                return created;
            }
        } catch (Exception ignored) {
        }
        // try to write into a matching field
        Field f = findFieldFlexible(currentClass, prop);
        if (f != null) {
            try {
                assignField(f, currentInstance, created, Modifier.isStatic(f.getModifiers()));
                return created;
            } catch (Exception ignored) {
            }
        }
        return created; // return even if we failed to write it — readers will still see it
    }

    private void assignField(Field f, Object onInstance, Object value, boolean isStatic) throws IllegalAccessException {
        f.setAccessible(true);
        if (isStatic) f.set(null, value);
        else {
            Object inst = ensureInstance(onInstance, f.getDeclaringClass());
            f.set(inst, value);
        }
    }

    private Object ensureInstanceAndGetField(Object currentInstance, Class<?> currentClass, Field f) throws IllegalAccessException {
        if (Modifier.isStatic(f.getModifiers())) return f.get(null);
        if (currentInstance == null) {
            currentInstance = createInstance(currentClass, null);
            if (currentInstance == null) throw new RuntimeException("Failed to create instance " + currentClass.getName());
        }
        return f.get(currentInstance);
    }

    private Object ensureInstance(Object inst, Class<?> cls) {
        if (inst != null) return inst;
        Object created = createInstance(cls, null);
        if (created == null) throw new RuntimeException("Unable to create instance for " + cls.getName());
        return created;
    }

    private void invokeSetter(Method setter, Object instance, Object arg) {
        try {
            if (Modifier.isStatic(setter.getModifiers())) setter.invoke(null, arg);
            else setter.invoke(instance, arg);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object createInstance(Class<?> clazz, Object outerInstance) {
        if (clazz == null) return null;
        try {
            Constructor<?> noArg = clazz.getDeclaredConstructor();
            noArg.setAccessible(true);
            return noArg.newInstance();
        } catch (NoSuchMethodException ignored) {
            // try single-arg constructor for inner classes
            if (outerInstance != null) {
                for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                    Class<?>[] params = ctor.getParameterTypes();
                    if (params.length == 1 && params[0].isAssignableFrom(outerInstance.getClass())) {
                        try {
                            ctor.setAccessible(true);
                            return ctor.newInstance(outerInstance);
                        } catch (Throwable ignored2) {
                        }
                    }
                }
            }
            // fallback: try any no-arg declared constructor
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                if (ctor.getParameterCount() == 0) {
                    try {
                        ctor.setAccessible(true);
                        return ctor.newInstance();
                    } catch (Throwable ignored2) {
                    }
                }
            }
            return null;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            logErr("Failed to create %s: %s", clazz.getName(), e.getMessage());
            return null;
        }
    }

    private Object convertValueToFieldType(Object value, Class<?> fieldType) {
        if (value == null) return null;
        if (fieldType.isInstance(value)) return value;

        if (fieldType.isEnum()) {
            @SuppressWarnings("unchecked") Class<Enum> ecl = (Class<Enum>) fieldType;
            return Enum.valueOf(ecl, value.toString());
        }

        if (fieldType == boolean.class || fieldType == Boolean.class) return Boolean.parseBoolean(value.toString());

        if (Number.class.isAssignableFrom(primitiveWrapperOf(fieldType)) || fieldType.isPrimitive()) {
            if (value instanceof Number n) return convertNumber(n, fieldType);
            String s = value.toString();
            try {
                if (fieldType == int.class || fieldType == Integer.class) return Integer.parseInt(s);
                if (fieldType == long.class || fieldType == Long.class) return Long.parseLong(s);
                if (fieldType == float.class || fieldType == Float.class) return Float.parseFloat(s);
                if (fieldType == double.class || fieldType == Double.class) return Double.parseDouble(s);
                if (fieldType == short.class || fieldType == Short.class) return Short.parseShort(s);
                if (fieldType == byte.class || fieldType == Byte.class) return Byte.parseByte(s);
                if (fieldType == char.class || fieldType == Character.class) return s.length() > 0 ? s.charAt(0) : '\0';
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Unable to convert '" + s + "' to " + fieldType.getSimpleName(), ex);
            }
        }

        if (fieldType == String.class) return value.toString();

        return value;
    }

    private Object convertNumber(Number n, Class<?> fieldType) {
        if (fieldType == int.class || fieldType == Integer.class) return n.intValue();
        if (fieldType == long.class || fieldType == Long.class) return n.longValue();
        if (fieldType == float.class || fieldType == Float.class) return n.floatValue();
        if (fieldType == double.class || fieldType == Double.class) return n.doubleValue();
        if (fieldType == short.class || fieldType == Short.class) return n.shortValue();
        if (fieldType == byte.class || fieldType == Byte.class) return n.byteValue();
        if (fieldType == char.class || fieldType == Character.class) return (char) n.intValue();
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

    private String capitalize(String s) { if (s == null || s.isEmpty()) return s; return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private void log(String fmt, Object... args) { System.out.println("[SAVE SETTINGS] " + String.format(fmt, args)); }
    private void logErr(String fmt, Object... args) { System.err.println("[SAVE SETTINGS] " + String.format(fmt, args)); }

}
