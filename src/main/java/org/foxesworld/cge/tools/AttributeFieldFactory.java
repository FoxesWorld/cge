package org.foxesworld.cge.tools;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class AttributeFieldFactory {
    private static final Logger logger = LogManager.getLogger(AttributeFieldFactory.class);
    private static final Map<String, Function<String, JComponent>> FIELD_CREATORS = new HashMap<>();
    private static final Dimension DEFAULT_SIZE = new Dimension(40, 20);
    private static final int DEFAULT_COLUMNS = 10;

    static {
        // Инициализация фабричных методов
        FIELD_CREATORS.put("enum", AttributeFieldFactory::createEnumField);
        FIELD_CREATORS.put("bool", s -> new JCheckBox());
        FIELD_CREATORS.put("int", s -> createNumberField(s, Integer::parseInt));
        FIELD_CREATORS.put("float", s -> createNumberField(s, Float::parseFloat));
        FIELD_CREATORS.put("string", s -> new JTextField(DEFAULT_COLUMNS));
        FIELD_CREATORS.put("vec3", s -> createVectorField(3));
        FIELD_CREATORS.put("vec4", s -> createColorPanel(Color.WHITE, true));
        FIELD_CREATORS.put("color", s -> createColorPanel(Color.WHITE, false));
        FIELD_CREATORS.put("float4", s -> createColorPanel(Color.WHITE, false));
    }

    public static JComponent createField(String type) {
        String typeKey = type.contains(":") ? type.split(":")[0] : type;
        logger.debug("Creating field for type: {}", typeKey);  // Логируем создание поля
        return FIELD_CREATORS.getOrDefault(typeKey.toLowerCase(),
                        t -> new JLabel("Unknown type: " + t))
                .apply(type);
    }

    private static JComboBox<String> createEnumField(String type) {
        String[] options = type.contains(":") ?
                type.split(":")[1].split("\\|") : new String[0];
        if (options.length == 0) {
            logger.warn("No options provided for enum type: {}", type);  // Логируем отсутствие опций
        }
        return new JComboBox<>(options);
    }

    private static JFormattedTextField createNumberField(String type, Function<String, Number> parser) {
        logger.debug("Creating number field of type: {}", type);  // Логируем создание числового поля
        NumberFormat format = NumberFormat.getInstance();
        JFormattedTextField field = new JFormattedTextField(format);
        field.setColumns(DEFAULT_COLUMNS);
        field.setValue(0);  // Default value
        return field;
    }

    private static JPanel createVectorField(int components) {
        logger.debug("Creating vector field with {} components", components);  // Логируем создание векторного поля
        JPanel panel = new JPanel(new GridLayout(1, components));
        for (int i = 0; i < components; i++) {
            panel.add(new JTextField(DEFAULT_COLUMNS));
        }
        return panel;
    }

    private static JPanel createColorPanel(Color initialColor, boolean withAlpha) {
        logger.debug("Creating color panel with initial color: {} and alpha: {}", initialColor, withAlpha);  // Логируем создание цветовой панели
        ColorPanel colorPanel = new ColorPanel(initialColor, withAlpha);
        colorPanel.setPreferredSize(DEFAULT_SIZE);
        colorPanel.addMouseListener(new ColorPickerListener(colorPanel, withAlpha));
        return colorPanel;
    }

    private static class ColorPanel extends JPanel {
        private boolean withAlpha;

        ColorPanel(Color color, boolean withAlpha) {
            this.withAlpha = withAlpha;
            setBackground(color);
            setBorder(BorderFactory.createLineBorder(Color.BLACK));
        }

        Color getColorWithAlpha() {
            Color c = getBackground();
            return withAlpha ? c : new Color(c.getRGB(), false);
        }
    }

    private static class ColorPickerListener extends MouseAdapter {
        private final ColorPanel colorPanel;
        private final boolean withAlpha;

        ColorPickerListener(ColorPanel panel, boolean withAlpha) {
            this.colorPanel = panel;
            this.withAlpha = withAlpha;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            logger.debug("Color picker clicked, current color: {}", colorPanel.getBackground());  // Логируем клик на панели
            Color chosenColor = JColorChooser.showDialog(
                    colorPanel,
                    "Choose Color",
                    colorPanel.getBackground()
            );

            if (chosenColor != null) {
                colorPanel.setBackground(withAlpha ?
                        chosenColor :
                        new Color(chosenColor.getRGB(), false));
                colorPanel.repaint();
                logger.debug("Color chosen: {}", chosenColor);  // Логируем выбранный цвет
            }
        }
    }
}