package org.foxesworld.cge.tools.SceneCGSCreator.util;

import org.foxesworld.cge.core.file.extensions.cgs.ChunkType;
import org.foxesworld.cge.core.file.extensions.cgs.writer.CGSFileWriter;
import org.foxesworld.cge.core.file.extensions.cgs.ChunkFieldTypeConfigLoader;
import org.foxesworld.cge.tools.utils.AttributeFieldFactory;
import org.foxesworld.cge.tools.SceneCGSCreator.SceneCgsCreatorFrame;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Упрощенная панель управления чанками без хардкода.
 */
public class ChunkControlsPanel extends JPanel {
    private final ChunkListPanel listPanel;
    private final JComboBox<ChunkType> typeCombo;
    private final JComboBox<String> subtypeCombo;
    private final JPanel attributesPanel = new JPanel(new GridBagLayout());
    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private final JButton addBtn = new JButton("Add Chunk");
    private final JButton saveBtn = new JButton("Save .cgs");

    private CGSFileWriter writer;
    private final ChunkFieldTypeConfigLoader config;

    public ChunkControlsPanel(ChunkListPanel listPanel) {
        this.listPanel = listPanel;
        setLayout(new BorderLayout(10, 10));

        // Загружаем конфиг единожды
        try {
            config = new ChunkFieldTypeConfigLoader(
                    getClass().getClassLoader().getResourceAsStream("chunkArguments.json")
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        typeCombo = new JComboBox<>(ChunkType.values());
        subtypeCombo = new JComboBox<>();

        add(buildControlPanel(), BorderLayout.CENTER);
        bindActions();
        refreshSubtypes();
    }

    private JPanel buildControlPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        int y = 0;
        // Тип чанка
        addRow(p, c, y++, "Type:", typeCombo);
        // Подтип чанка
        addRow(p, c, y++, "Subtype:", subtypeCombo);
        // Атрибуты
        c.gridy = y++; c.gridx = 0; c.gridwidth = 2;
        p.add(new JLabel("Attributes:"), c);
        c.gridy = y++; c.weightx = 1; c.weighty = 1;
        p.add(new JScrollPane(attributesPanel), c);
        c.weightx = c.weighty = 0; c.gridwidth = 2;
        // Кнопки
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(addBtn);
        btns.add(saveBtn);
        c.gridy = y; p.add(btns, c);

        return p;
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent comp) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1;
        panel.add(new JLabel(label), c);
        c.gridx = 1; panel.add(comp, c);
    }

    private void bindActions() {
        typeCombo.addActionListener(e -> refreshSubtypes());
        subtypeCombo.addActionListener(e -> refreshAttributes());
        addBtn.addActionListener(e -> addChunk());
        saveBtn.addActionListener(e -> save());
        addBtn.setEnabled(false);
        saveBtn.setEnabled(false);
    }

    private void refreshSubtypes() {
        subtypeCombo.setModel(new DefaultComboBoxModel<>(
                config.getChildTypes(typeCombo.getSelectedItem().toString())
                        .toArray(new String[0])
        ));
        refreshAttributes();
    }

    private void refreshAttributes() {
        fields.clear();
        attributesPanel.removeAll();

        String type = typeCombo.getSelectedItem().toString();
        String subtype = (String) subtypeCombo.getSelectedItem();
        if (subtype == null) return;

        Map<String, String> attrs = config.getAttributes(type, subtype);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.fill = GridBagConstraints.HORIZONTAL;
        int y = 0;
        for (Map.Entry<String, String> en : attrs.entrySet()) {
            addRow(attributesPanel, c, y, en.getKey() + ":",
                    fields.computeIfAbsent(en.getKey(), k ->
                            AttributeFieldFactory.createField(en.getValue())));
            y++;
        }
        revalidate(); repaint();
    }

    private void addChunk() {
        if (writer == null) {
            JOptionPane.showMessageDialog(this, "Writer not set", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String type = typeCombo.getSelectedItem().toString();
        String subtype = (String) subtypeCombo.getSelectedItem();
        Map<String, Object> data = new LinkedHashMap<>();
        fields.forEach((k, comp) -> data.put(k,
                AttributeFieldFactory.getValue(comp,
                        config.getAttributes(type, subtype).get(k))));

        try {
            byte[] serialized = ChunkSerializer.serialize(subtype, data, config.getAttributes(type, subtype));
            writer.addChunk((int)(System.nanoTime() & 0x0FFFFFFF), ChunkType.valueOf(type), serialized, data);
            listPanel.addChunkVisual(ChunkType.valueOf(type), subtype, data);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Serialization failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save() {
        writer.setSceneName(SceneCgsCreatorFrame.sceneNameField.getText());
        //writer.writeFile();
        JOptionPane.showMessageDialog(this, "Saved successfully", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    public void setWriter(CGSFileWriter w) {
        this.writer = w;
        addBtn.setEnabled(true);
        saveBtn.setEnabled(true);
    }
}