package org.foxesworld.cge.tools.SceneCreator;

import org.foxesworld.cge.core.cgs.ChunkType;
import org.foxesworld.cge.tools.AttributeFieldFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChunkControlsPanel extends JPanel {
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
        byte[] data = serializeAttributes(type);
        int id = listPanel.getChunkListModel().getSize();
        listPanel.getWriter().addChunk(id, type, data);
        listPanel.getChunkListModel().addElement(String.format("[%d] %s (%d bytes)", id, type.name(), data.length));
        listPanel.getChunkList().setSelectedIndex(id);
    }

    private byte[] serializeAttributes(ChunkType type) {
        Map<String, String> attrs = type.getAttributes();
        ByteBuffer buffer = ByteBuffer.allocate(1024); // увеличь при необходимости

        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            String attrType = entry.getValue();
            JComponent field = attributeFields.get(entry.getKey());

            switch (attrType) {
                case "int":
                    buffer.putInt(Integer.parseInt(getFieldValue(field)));
                    break;
                case "float":
                    buffer.putFloat(Float.parseFloat(getFieldValue(field)));
                    break;
                case "byte":
                    buffer.put(Byte.parseByte(getFieldValue(field)));
                    break;
                case "string":
                    byte[] strBytes = getFieldValue(field).getBytes(StandardCharsets.UTF_8);
                    buffer.putInt(strBytes.length);
                    buffer.put(strBytes);
                    break;
                case "bool":
                    boolean selected = false;
                    if (field instanceof JCheckBox) {
                        selected = ((JCheckBox) field).isSelected();
                    } else {
                        selected = Boolean.parseBoolean(getFieldValue(field));
                    }
                    buffer.put((byte) (selected ? 1 : 0));
                    break;
                default:
                    if (attrType.startsWith("enum:")) {
                        byte[] enumBytes = getFieldValue(field).getBytes(StandardCharsets.UTF_8);
                        buffer.putInt(enumBytes.length);
                        buffer.put(enumBytes);
                    } else {
                        throw new IllegalArgumentException("Unknown attribute type: " + attrType);
                    }
            }
        }

        buffer.flip();
        byte[] result = new byte[buffer.limit()];
        buffer.get(result);
        return result;
    }



    private String getFieldValue(JComponent field) {
        if (field instanceof JTextField textField) {
            return textField.getText();
        } else if (field instanceof JSpinner spinner) {
            return spinner.getValue().toString();
        } else if (field instanceof JComboBox<?> comboBox) {
            return comboBox.getSelectedItem().toString();
        }
        return "";
    }

    private void saveToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save CGS Scene");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            java.io.File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".cgs")) {
                file = new java.io.File(file.getParentFile(), file.getName() + ".cgs");
            }
            try {
                listPanel.getWriter().writeToFile(file);
                JOptionPane.showMessageDialog(this, "Scene saved to " + file.getName(), "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (java.io.IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    /**
     * Позволяет установить собственный ActionListener для кнопки Save.
     * Существующий слушатель будет удалён.
     */
    public void setSaveAction(ActionListener listener) {
        for (ActionListener al : saveBtn.getActionListeners()) {
            saveBtn.removeActionListener(al);
        }
        saveBtn.addActionListener(listener);
    }
}