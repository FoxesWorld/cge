package org.foxesworld.cge.tools.SceneCreator;

import org.foxesworld.cge.core.cgs.ChunkType;
import org.foxesworld.cge.tools.AttributeFieldFactory;
import org.foxesworld.cge.core.cgs.writer.CGSFileWriter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChunkControlsPanel extends JPanel {
    private CGSFileWriter writer;
    private final JComboBox<ChunkType> typeCombo = new JComboBox<>(ChunkType.values());
    private final JPanel attributesPanel = new JPanel(new GridBagLayout());
    private final Map<String, JComponent> attributeFields = new LinkedHashMap<>();
    private final ChunkListPanel listPanel;
    private final JButton addBtn = new JButton("Add Chunk");
    private final JButton saveBtn = new JButton("Save .cgs");

    public ChunkControlsPanel(ChunkListPanel listPanel) {
        this.listPanel = listPanel;
        setLayout(new BorderLayout());
        add(buildControls(), BorderLayout.CENTER);

        addBtn.setEnabled(false);
        saveBtn.setEnabled(false);

        typeCombo.addActionListener(e -> updateAttributeFields((ChunkType) typeCombo.getSelectedItem()));
        updateAttributeFields((ChunkType) typeCombo.getSelectedItem());
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Chunk Type:"), gbc);
        gbc.gridx = 1;
        panel.add(typeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        panel.add(new JLabel("Attributes:"), gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        panel.add(attributesPanel, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(addBtn);
        btnPanel.add(saveBtn);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(btnPanel, gbc);

        addBtn.addActionListener(e -> addChunk((ChunkType) typeCombo.getSelectedItem()));
        saveBtn.addActionListener(e -> saveToFile());

        return panel;
    }

    private void updateAttributeFields(ChunkType type) {
        attributeFields.clear();
        attributesPanel.removeAll();
        Map<String, String> attrs = type.getAttributes();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;

        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            gbc.gridx = 0;
            attributesPanel.add(new JLabel(entry.getKey() + ":"), gbc);
            JComponent field = AttributeFieldFactory.createField(entry.getValue());
            gbc.gridx = 1;
            attributesPanel.add(field, gbc);
            attributeFields.put(entry.getKey(), field);
            gbc.gridy++;
        }
        attributesPanel.revalidate();
        attributesPanel.repaint();
    }

    private void addChunk(ChunkType type) {
        if (writer == null) {
            JOptionPane.showMessageDialog(this,
                    "Please save scene first to select output file",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!validateAttributes(type)) {
            return;
        }
        byte[] data = serializeAttributes(type);
        int id = listPanel.getChunkListModel().getSize();
        writer.addChunk(id, type, data);
        listPanel.getChunkListModel().addElement(
                String.format("[%d] %s (%d bytes)", id, type.name(), data.length)
        );
        listPanel.getChunkList().setSelectedIndex(id);
    }

    private byte[] serializeAttributes(ChunkType type) {
        Map<String, String> attrs = type.getAttributes();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            String attrType = entry.getValue();
            JComponent field = attributeFields.get(entry.getKey());

            switch (attrType) {
                case "int" -> buffer.putInt(Integer.parseInt(getFieldValue(field)));
                case "float" -> buffer.putFloat(Float.parseFloat(getFieldValue(field)));
                case "byte" -> buffer.put(Byte.parseByte(getFieldValue(field)));
                case "string" -> {
                    byte[] strBytes = getFieldValue(field).getBytes(StandardCharsets.UTF_8);
                    buffer.putInt(strBytes.length);
                    buffer.put(strBytes);
                }
                case "bool" -> {
                    boolean selected = field instanceof JCheckBox ?
                            ((JCheckBox) field).isSelected() :
                            Boolean.parseBoolean(getFieldValue(field));
                    buffer.put((byte) (selected ? 1 : 0));
                }

                case "float4", "vec4", "color" -> {
                    if (!(field instanceof JPanel panel)) {
                        throw new IllegalArgumentException("Expected JPanel for color");
                    }
                    Color c = panel.getBackground();
                    buffer.putFloat(c.getRed() / 255f);
                    buffer.putFloat(c.getGreen() / 255f);
                    buffer.putFloat(c.getBlue() / 255f);
                    buffer.putFloat(c.getAlpha() / 255f);
                }

                default -> {
                    if (attrType.startsWith("enum:")) {
                        byte[] enumBytes = getFieldValue(field).getBytes(StandardCharsets.UTF_8);
                        buffer.putInt(enumBytes.length);
                        buffer.put(enumBytes);
                    } else {
                        throw new IllegalArgumentException("Unknown attribute type: " + attrType);
                    }
                }
            }
        }

        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }

    private boolean validateAttributes(ChunkType type) {
        Map<String, String> attrs = type.getAttributes();

        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            String key = entry.getKey();
            String attrType = entry.getValue();
            JComponent field = attributeFields.get(key);
            String value = getFieldValue(field).trim();

            // Проверка на пустые поля, кроме типа bool и color
            if (value.isEmpty() && !attrType.equals("bool") && !attrType.equals("color") &&
                    !attrType.equals("vec4") && !attrType.equals("float4")) {
                JOptionPane.showMessageDialog(this,
                        "Attribute \"" + key + "\" is required.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return false;
            }

            try {
                switch (attrType) {
                    case "int" -> Integer.parseInt(value);
                    case "float" -> Float.parseFloat(value);
                    case "byte" -> Byte.parseByte(value);
                    case "bool" -> {} // skip
                    case "string" -> {} // skip
                    case "float4", "vec4", "color" -> {
                        // no validation needed, handled through UI
                    }
                    default -> {
                        if (attrType.startsWith("enum:")) {
                            // you may optionally check that value is among allowed enum values
                            if (value.isEmpty()) throw new IllegalArgumentException();
                        } else {
                            throw new IllegalArgumentException("Unknown attribute type: " + attrType);
                        }
                    }
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Invalid value for attribute \"" + key + "\"",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }

        return true;
    }


    private String getFieldValue(JComponent field) {
        if (field instanceof JTextField textField) {
            return textField.getText();
        } else if (field instanceof JSpinner spinner) {
            return spinner.getValue().toString();
        } else if (field instanceof JComboBox<?> comboBox) {
            return comboBox.getSelectedItem().toString();
        } else if (field instanceof JPanel panel) {
            Color c = panel.getBackground();
            return String.format("%.2f,%.2f,%.2f,%.2f",
                    c.getRed() / 255f,
                    c.getGreen() / 255f,
                    c.getBlue() / 255f,
                    c.getAlpha() / 255f);
        }
        return "";
    }

    private void saveToFile() {
        if (writer == null) {
            JOptionPane.showMessageDialog(this,
                    "Please save scene first to select output file",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save CGS Scene");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                writer.writeToFile();
                JOptionPane.showMessageDialog(this,
                        "Scene saved to " + writer.getFile().getName(),
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error saving file: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    public void setWriter(CGSFileWriter writer) {
        this.writer = writer;
        addBtn.setEnabled(true);
        saveBtn.setEnabled(true);
    }

    public void setSaveAction(ActionListener listener) {
        for (ActionListener al : saveBtn.getActionListeners()) {
            saveBtn.removeActionListener(al);
        }
        saveBtn.addActionListener(listener);
    }
}