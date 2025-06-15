package org.foxesworld.cge.modules.ui.novaUi.elements.panel;

import com.jme3.math.ColorRGBA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

import static java.lang.Float.parseFloat;

/**
 * PanelProperties — «модуль», который регистрирует Map<propertyName, handler>,
 * и потом применяется из PanelElement.setProperty(key, value).
 *
 * Это позволяет не держать в PanelElement огромный switch/case, а
 * вынести логику разбора атрибутов в отдельный класс.
 */
public class PanelProperties {
    private static final Logger logger = LoggerFactory.getLogger(PanelProperties.class);

    private final PanelElement panel;
    private final Map<String, Consumer<String>> handlers = new LinkedHashMap<>();

    public PanelProperties(PanelElement panel) {
        this.panel = panel;
        initHandlers();
    }

    private void initHandlers() {
        handlers.put("bgColor", v -> {
            ColorRGBA color = parseColorOrDefault(v, panel.getBgColor());
            panel.setBgColor(color);
        });
        handlers.put("width", v -> {
            if ("auto".equalsIgnoreCase(v)) {
                panel.setAutoWidth();
            } else {
                panel.setFixedWidth(parseFloatOrDefault(v, panel.getFixedWidth()));
            }
            panel.recalcAndRepositionSelfAndAncestors();
        });
        handlers.put("height", v -> {
            if ("auto".equalsIgnoreCase(v)) {
                panel.setAutoHeight();
            } else {
                panel.setFixedHeight(parseFloatOrDefault(v, panel.getFixedHeight()));
            }
            panel.recalcAndRepositionSelfAndAncestors();
        });
        handlers.put("margin", v -> {
            panel.setMargin(parseFloatOrDefault(v, panel.getMargin()));
            panel.recalcAndRepositionSelfAndAncestors();
        });
        handlers.put("padding", v -> {
            panel.setPadding(parseFloatOrDefault(v, panel.getPadding()));
            panel.recalcAndRepositionSelfAndAncestors();
        });
        handlers.put("align", v -> {
            panel.setAlign(v.trim().toLowerCase());
            panel.recalcAndRepositionSelfAndAncestors();
        });
        handlers.put("layout", v -> {
            String val = v.trim().toLowerCase();
            if (Set.of("none", "vertical", "horizontal").contains(val)) {
                panel.setLayout(val);
            } else {
                logger.warn("Panel '{}' invalid layout '{}', defaulting to 'none'", panel.getId(), v);
                panel.setLayout("none");
            }
            panel.recalcAndRepositionSelfAndAncestors();
        });
        handlers.put("spacing", v -> {
            panel.setSpacing(parseFloatOrDefault(v, panel.getSpacing()));
            panel.recalcAndRepositionSelfAndAncestors();
        });
    }

    public void apply(String key, String value) {
        Consumer<String> h = handlers.get(key);
        if (h != null) {
            h.accept(value);
        } else {
            logger.warn("Panel '{}' unknown property '{}'", panel.getId(), key);
        }
    }

    private ColorRGBA parseColorOrDefault(String s, ColorRGBA def) {
        if (s == null || s.isEmpty()) return def.clone();
        String[] parts = s.split(",");
        if (parts.length != 4) {
            logger.warn("Panel '{}' invalid color '{}'", panel.getId(), s);
            return def.clone();
        }
        try {
            return new ColorRGBA(
                    parseFloat(parts[0].trim()),
                    parseFloat(parts[1].trim()),
                    parseFloat(parts[2].trim()),
                    parseFloat(parts[3].trim())
            );
        } catch (NumberFormatException e) {
            logger.warn("Panel '{}' failed to parse color '{}'", panel.getId(), s);
            return def.clone();
        }
    }

    private float parseFloatOrDefault(String s, float def) {
        if (s == null || s.isEmpty()) return def;
        try {
            return parseFloat(s);
        } catch (NumberFormatException e) {
            logger.warn("Panel '{}' cannot parse '{}' as float, using {}", panel.getId(), s, def);
            return def;
        }
    }
}
