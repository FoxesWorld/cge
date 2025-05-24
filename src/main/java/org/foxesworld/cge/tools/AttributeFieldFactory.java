package org.foxesworld.cge.tools;

import javax.swing.*;
import java.util.*;

public class AttributeFieldFactory {

    public static JComponent createField(String type) {
        if (type.startsWith("enum:")) {
            String[] options = type.substring(5).split("\\|");
            return new JComboBox<>(options);
        }
        return switch (type) {
            case "bool" -> new JCheckBox();
            case "float", "int", "string" -> new JTextField(10);
            case "vec3", "vec4" -> new JTextField("0.0,0.0,0.0"); // можно улучшить
            default -> new JLabel("Unknown type: " + type);
        };
    }
}
