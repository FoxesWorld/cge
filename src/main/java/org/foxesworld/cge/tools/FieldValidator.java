package org.foxesworld.cge.tools;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class FieldValidator {
    public static boolean validateFields(Map<String, String> fieldTypes,
                                         Map<String, JComponent> fields,
                                         Component parent) {

        for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue();
            JComponent field = fields.get(fieldName);

            if (!validateField(field, fieldType)) {
                showValidationError(parent, fieldName);
                return false;
            }
        }
        return true;
    }

    private static boolean validateField(JComponent field, String type) {
        String value = FieldUtils.getFieldValue(field).trim();

        if (value.isEmpty() && !type.equals("bool") && !type.startsWith("color")) {
            return false;
        }

        try {
            switch (type) {
                case "int" -> Integer.parseInt(value);
                case "float" -> Float.parseFloat(value);
                case "byte" -> Byte.parseByte(value);
                case "enum" -> {
                    if (field instanceof JComboBox && ((JComboBox<?>) field).getSelectedItem() == null) {
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private static void showValidationError(Component parent, String fieldName) {
        JOptionPane.showMessageDialog(parent,
                "Invalid value for field: " + fieldName,
                "Validation Error", JOptionPane.WARNING_MESSAGE);
    }
}