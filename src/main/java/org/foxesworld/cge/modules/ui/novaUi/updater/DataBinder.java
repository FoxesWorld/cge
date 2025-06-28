package org.foxesworld.cge.modules.ui.novaUi.updater;

import org.foxesworld.cge.modules.ui.novaUi.elements.UIElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.progress.ProgressElement;
import org.foxesworld.cge.modules.ui.novaUi.elements.text.TextElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages data binding between fields in a target object (e.g., a controller)
 * and properties of UI elements.
 */
public class DataBinder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataBinder.class);

    private final Object target;
    private final Map<String, Field> fields = new HashMap<>();
    private final Map<String, Object> lastKnownValues = new HashMap<>();
    private final Collection<UIElement> elements;

    public DataBinder(Object target, Collection<UIElement> elements) {
        this.target = target;
        this.elements = elements;
        scanFields();
        bindInitialValues();
    }

    private void scanFields() {
        if (target == null) return;
        for (Field field : target.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            fields.put(field.getName(), field);
        }
        LOGGER.info("DataBinder scanned {} fields from target '{}'.", fields.size(), target.getClass().getSimpleName());
    }

    private void bindInitialValues() {
        if (target == null) return;
        for (UIElement element : elements) {
            Field field = fields.get(element.getId());
            if (field != null) {
                updateElement(element, field);
            }
        }
    }

    /**
     * Updates all bound UI elements if their corresponding data fields have changed.
     */
    public void update() {
        if (target == null) return;
        // This is a simplified polling-based approach.
        // A more advanced system would use listeners/observers for better performance.
        for (UIElement element : elements) {
            Field field = fields.get(element.getId());
            if (field != null) {
                updateElement(element, field);
            }
        }
    }

    private void updateElement(UIElement element, Field field) {
        try {
            Object newValue = field.get(target);
            Object oldValue = lastKnownValues.get(element.getId());

            if (newValue != null && !newValue.equals(oldValue)) {
                if (element instanceof TextElement) {
                    element.setProperty("text", newValue.toString());
                } else if (element instanceof ProgressElement && newValue instanceof Number) {
                    float progress = ((Number) newValue).floatValue();
                    element.setProperty("progress", String.valueOf(progress));
                }
                lastKnownValues.put(element.getId(), newValue);
            }
        } catch (IllegalAccessException e) {
            LOGGER.error("Failed to access field '{}' for data binding.", field.getName(), e);
            // To prevent repeated errors, we can remove the field from our map
            fields.remove(element.getId());
        }
    }
}