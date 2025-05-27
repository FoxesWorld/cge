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
        FIELD_CREATORS.put("BOOLEAN", s -> new JCheckBox());
        FIELD_CREATORS.put("INT", s -> createNumberField(s, Integer::parseInt));
        FIELD_CREATORS.put("FLOAT", s -> createNumberField(s, Float::parseFloat));
        FIELD_CREATORS.put("STRING", s -> new JTextField(DEFAULT_COLUMNS));
        FIELD_CREATORS.put("VECTOR3F", s -> createVectorField(3));
        FIELD_CREATORS.put("VEC4", s -> createColorPanel(Color.WHITE, true));
        FIELD_CREATORS.put("COLOR", s -> createColorPanel(Color.WHITE, false));
        FIELD_CREATORS.put("FLOAT4", s -> createColorPanel(Color.WHITE, false));
    }

    public static Object getValue(JComponent field, String type) {
        logger.info("Getting value of {} type", type);
        String typeKey = type.contains(":") ? type.split(":")[0].toUpperCase() : type.toUpperCase();

        switch (typeKey) {
            case "BOOLEAN" -> {
                return ((JCheckBox) field).isSelected();
            }
            case "INT" -> {
                return ((JFormattedTextField) field).getValue();
            }
            case "FLOAT" -> {
                return ((JFormattedTextField) field).getValue();
            }
            case "STRING" -> {
                return ((JTextField) field).getText();
            }
            case "VECTOR3F", "FLOAT3" -> {
                return extractFloatStringFromPanel(field, 3);
            }
            case "VEC4", "FLOAT4" -> {
                return extractFloatStringFromPanel(field, 4);
            }
            case "COLOR" -> {
                return ((ColorPanel) field).getColorAsCommaString(); // возвращаем java.awt.Color
            }
            case "ENUM" -> {
                return ((JComboBox<?>) field).getSelectedItem();
            }
            default -> {
                logger.warn("Unknown or unsupported type in getValue: {}", type);
                return "undefined";
            }
        }
    }

    /**
     * Извлекает из панели векторных спиннеров массив значений и возвращает их
     * в виде строки с разделителем запятая.
     *
     * @param panel        контейнер, в котором для каждого компонента вектора есть вложенный JPanel с JSpinner
     * @param expectedSize ожидаемое количество элементов в векторе
     * @return строка вида "x1,x2, ..., xn"
     */
    private static String extractFloatStringFromPanel(JComponent panel, int expectedSize) {
        Component[] components = panel.getComponents();
        StringBuilder sb = new StringBuilder();
        int count = 0;

        for (Component comp : components) {
            if (comp instanceof JPanel vecPanel && count < expectedSize) {
                // Предполагаем, что спиннер — второй компонент внутри vecPanel
                Component spinnerComp = vecPanel.getComponentCount() > 1 ? vecPanel.getComponent(1) : null;
                if (spinnerComp instanceof JSpinner spinner) {
                    Object val = spinner.getValue();
                    float f = (val instanceof Number) ? ((Number) val).floatValue() : 0f;
                    if (count > 0) {
                        sb.append(',');
                    }
                    sb.append(f);
                    count++;
                }
            }
        }

        // Если не набралось нужного числа, дополняем нулями
        while (count < expectedSize) {
            sb.append(',').append(0f);
            count++;
        }

        return sb.toString();
    }





    public static JComponent createField(String type) {
        String typeKey = type.contains(":") ? type.split(":")[0] : type;
        logger.debug("Creating field for type: {}", typeKey);  // Логируем создание поля
        return FIELD_CREATORS.getOrDefault(typeKey.toUpperCase(),
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
        logger.debug("Creating styled vector field with {} components", components);
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));

        String[] labels = {"X", "Y", "Z", "W"};
        SpinnerNumberModel[] models = new SpinnerNumberModel[components];

        for (int i = 0; i < components; i++) {
            models[i] = new SpinnerNumberModel(0.0f, -Float.MAX_VALUE, Float.MAX_VALUE, 0.1f);
            JSpinner spinner = new JSpinner(models[i]);
            spinner.setPreferredSize(new Dimension(60, 24));
            spinner.setFont(new Font("Monospaced", Font.PLAIN, 12));
            JComponent editor = spinner.getEditor();
            if (editor instanceof JSpinner.DefaultEditor defEditor) {
                defEditor.getTextField().setHorizontalAlignment(JTextField.RIGHT);
            }

            JPanel componentPanel = new JPanel();
            componentPanel.setLayout(new BorderLayout(3, 0));
            if (i < labels.length) {
                JLabel label = new JLabel(labels[i]);
                label.setFont(new Font("SansSerif", Font.BOLD, 11));
                label.setPreferredSize(new Dimension(15, 24));
                componentPanel.add(label, BorderLayout.WEST);
            }
            componentPanel.add(spinner, BorderLayout.CENTER);

            panel.add(componentPanel);
        }

        panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        return panel;
    }



    private static JPanel createColorPanel(Color initialColor, boolean withAlpha) {
        logger.debug("Creating color panel with initial color: {} and alpha: {}", initialColor, withAlpha);  // Логируем создание цветовой панели
        ColorPanel colorPanel = new ColorPanel(initialColor, withAlpha);
        colorPanel.setPreferredSize(DEFAULT_SIZE);
        colorPanel.addMouseListener(new ColorPickerListener(colorPanel, withAlpha));
        return colorPanel;
    }

    public static class ColorPanel extends JPanel {
        private final boolean withAlpha;

        public ColorPanel(Color color, boolean withAlpha) {
            this.withAlpha = withAlpha;
            setBackground(color);
            setBorder(BorderFactory.createLineBorder(Color.BLACK));
        }

        /** Возвращает текущий цвет (без учёта/с учётом альфа) */
        public Color getColorWithAlpha() {
            Color c = getBackground();
            return withAlpha ? c : new Color(c.getRed(), c.getGreen(), c.getBlue());
        }

        /** Старый метод, при необходимости оставляем */
        public float[] getColorAsFloatArray() {
            Color c = getColorWithAlpha();
            float r = c.getRed() / 255f;
            float g = c.getGreen() / 255f;
            float b = c.getBlue() / 255f;

            if (withAlpha) {
                float a = c.getAlpha() / 255f;
                return new float[]{r, g, b, a};
            } else {
                return new float[]{r, g, b};
            }
        }

        /**
         * Новый метод: возвращает строку "R,G,B" или "R,G,B,A"
         */
        public String getColorAsCommaString() {
            Color c = getColorWithAlpha();
            StringBuilder sb = new StringBuilder()
                    .append(c.getRed())
                    .append(',')
                    .append(c.getGreen())
                    .append(',')
                    .append(c.getBlue());

            if (withAlpha) {
                sb.append(',').append(c.getAlpha());
            }

            return sb.toString();
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