package org.foxesworld.cge.tools.utils;

import javax.swing.*;
import java.awt.*;

public class FieldUtils {
    public static String getFieldValue(JComponent field) {
        if (field instanceof JTextField textField) {
            return textField.getText();
        } else if (field instanceof JComboBox<?> comboBox) {
            return comboBox.getSelectedItem().toString();
        } else if (field instanceof JPanel panel) {
            return parseColorValue(panel.getBackground());
        }
        return "";
    }

    private static String parseColorValue(Color color) {
        return String.format("%.2f,%.2f,%.2f,%.2f",
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                color.getAlpha() / 255f);
    }
}