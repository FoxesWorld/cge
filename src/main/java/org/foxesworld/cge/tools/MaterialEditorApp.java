package org.foxesworld.cge.tools;

import com.formdev.flatlaf.FlatLightLaf;
import org.foxesworld.cge.tools.utils.AttributeFieldFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

import static org.foxesworld.cge.tools.SceneCGSCreator.SceneCgsCreatorFrame.setupTheme;

/**
 * FlatLaf GUI to create .cgmat files that embed name, J3MD bytes, and named arguments.
 */
public class MaterialEditorApp extends JFrame {
    private final MaterialTableModel tableModel = new MaterialTableModel();
    private final JTable table = new JTable(tableModel);

    public MaterialEditorApp() {
        super("Material Editor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        table.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(table);

        JButton addButton = new JButton("Add Material");
        addButton.addActionListener(this::onAddMaterial);

        JButton removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                tableModel.removeRow(row);
            }
        });

        JButton saveButton = new JButton("Save .cgmat");
        saveButton.addActionListener(this::onSave);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(addButton);
        controls.add(removeButton);
        controls.add(saveButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(controls, BorderLayout.NORTH);
        getContentPane().add(scrollPane, BorderLayout.CENTER);
    }

    private void onAddMaterial(ActionEvent e) {
        JTextField nameField = new JTextField(20);
        JTextField j3mdPathField = new JTextField(30);
        j3mdPathField.setEditable(false);
        JButton browseJ3mdButton = new JButton("Browse J3MD");
        browseJ3mdButton.addActionListener(ae -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("J3MD Files", "j3md"));
            int ret = chooser.showOpenDialog(this);
            if (ret == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                j3mdPathField.setText(f.getAbsolutePath());
            }
        });

        DefaultListModel<AttributeRow> attrListModel = new DefaultListModel<>();
        JList<AttributeRow> attrList = new JList<>(attrListModel);
        attrList.setCellRenderer(new AttributeRowRenderer());
        JScrollPane attrScroll = new JScrollPane(attrList);
        attrScroll.setPreferredSize(new Dimension(400, 150));

        JButton addAttrButton = new JButton("Add Argument");
        addAttrButton.addActionListener(ae -> {
            AttributeRow row = promptForAttribute();
            if (row != null) {
                attrListModel.addElement(row);
            }
        });

        JButton removeAttrButton = new JButton("Remove Argument");
        removeAttrButton.addActionListener(ae -> {
            int idx = attrList.getSelectedIndex();
            if (idx >= 0) {
                attrListModel.remove(idx);
            }
        });

        JPanel attrControl = new JPanel(new FlowLayout(FlowLayout.LEFT));
        attrControl.add(addAttrButton);
        attrControl.add(removeAttrButton);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Material Name:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("J3MD File:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(j3mdPathField, gbc);
        gbc.gridx = 2;
        panel.add(browseJ3mdButton, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("Arguments:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(attrScroll, gbc);

        gbc.gridx = 1; gbc.gridy = 3; gbc.anchor = GridBagConstraints.WEST;
        panel.add(attrControl, gbc);

        int result = JOptionPane.showConfirmDialog(
                this, panel, "Add New Material", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String j3mdPath = j3mdPathField.getText().trim();
            if (name.isEmpty() || j3mdPath.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name and J3MD file are required.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            File j3mdFile = new File(j3mdPath);
            if (!j3mdFile.exists()) {
                JOptionPane.showMessageDialog(this, "Selected J3MD file does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            List<AttributeRow> attrs = Collections.list(attrListModel.elements());
            tableModel.addRow(new MaterialRow(name, j3mdFile, attrs));
        }
    }

    private AttributeRow promptForAttribute() {
        JTextField attrNameField = new JTextField(15);
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"FLOAT", "STRING", "BOOLEAN", "COLOR"});
        JPanel valuePanel = new JPanel(new BorderLayout());
        valuePanel.add(AttributeFieldFactory.createField("FLOAT"), BorderLayout.CENTER);

        typeCombo.addActionListener(e -> {
            valuePanel.removeAll();
            String typeKey = (String) typeCombo.getSelectedItem();
            switch (typeKey) {
                case "STRING" -> valuePanel.add(AttributeFieldFactory.createField("STRING"), BorderLayout.CENTER);
                case "BOOLEAN" -> valuePanel.add(AttributeFieldFactory.createField("BOOLEAN"), BorderLayout.CENTER);
                case "COLOR" -> valuePanel.add(AttributeFieldFactory.createField("COLOR"), BorderLayout.CENTER);
                default -> valuePanel.add(AttributeFieldFactory.createField("FLOAT"), BorderLayout.CENTER);
            }
            valuePanel.revalidate();
            valuePanel.repaint();
        });

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Argument Name:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(attrNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Type:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(typeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.EAST;
        panel.add(new JLabel("Value:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(valuePanel, gbc);

        int result = JOptionPane.showConfirmDialog(
                this, panel, "Add Argument", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );
        if (result == JOptionPane.OK_OPTION) {
            String attrName = attrNameField.getText().trim();
            if (attrName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Argument name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }
            String typeKey = (String) typeCombo.getSelectedItem();
            JComponent fieldComp = (JComponent) valuePanel.getComponent(0);
            return new AttributeRow(attrName, typeKey, fieldComp);
        }
        return null;
    }

    private void onSave(ActionEvent e) {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No materials to save.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save .cgmat File");
        int ret = chooser.showSaveDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File outFile = chooser.getSelectedFile();
            if (!outFile.getName().toLowerCase().endsWith(".cgmat")) {
                outFile = new File(outFile.getParentFile(), outFile.getName() + ".cgmat");
            }
            try {
                writeCgmat(outFile, tableModel.getEntries());
                JOptionPane.showMessageDialog(this, "Saved to " + outFile.getAbsolutePath(), "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void writeCgmat(File file, List<MaterialRow> rows) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(0);
            // Header: magic (4 bytes), version (int), materialCount(int), dataOffset(long), fileSize(long)
            raf.write("CGMT".getBytes(StandardCharsets.US_ASCII));
            raf.writeInt(1);
            raf.writeInt(rows.size());
            long dataOffsetPos = raf.getFilePointer();
            raf.writeLong(0L); // placeholder for dataOffset
            raf.writeLong(0L); // placeholder for fileSize

            long dataOffset = raf.getFilePointer();
            for (MaterialRow row : rows) {
                // nameLength + name
                byte[] nameBytes = row.name.getBytes(StandardCharsets.UTF_8);
                raf.writeInt(nameBytes.length);
                raf.write(nameBytes);
                // paramCount = number of arguments
                raf.writeInt(row.attributes.size());
                // Read J3MD into bytes
                byte[] j3mdBytes = readAllBytes(row.j3mdFile);
                raf.writeInt(j3mdBytes.length);
                raf.write(j3mdBytes);
                // Arguments: for each, write name, type, value as UTF-8
                for (AttributeRow attr : row.attributes) {
                    byte[] aname = attr.name.getBytes(StandardCharsets.UTF_8);
                    raf.writeInt(aname.length);
                    raf.write(aname);
                    byte[] atype = attr.type.getBytes(StandardCharsets.UTF_8);
                    raf.writeInt(atype.length);
                    raf.write(atype);
                    Object val = AttributeFieldFactory.getValue(attr.field, attr.type);
                    String valStr = val.toString();
                    byte[] vbytes = valStr.getBytes(StandardCharsets.UTF_8);
                    raf.writeInt(vbytes.length);
                    raf.write(vbytes);
                }
            }

            long fileSize = raf.length();
            raf.seek(dataOffsetPos);
            raf.writeLong(dataOffset);
            raf.writeLong(fileSize);
        }
    }

    private byte[] readAllBytes(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toByteArray();
        }
    }

    public static void main(String[] args) {
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        setupTheme("theme/calista.properties");
        SwingUtilities.invokeLater(() -> {
            MaterialEditorApp app = new MaterialEditorApp();
            app.setVisible(true);
        });
    }

    static class AttributeRow {
        final String name;
        final String type;
        final JComponent field;
        AttributeRow(String name, String type, JComponent field) {
            this.name = name;
            this.type = type;
            this.field = field;
        }
    }

    static class AttributeRowRenderer extends JLabel implements ListCellRenderer<AttributeRow> {
        @Override
        public Component getListCellRendererComponent(JList<? extends AttributeRow> list, AttributeRow value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            setText(value.name + " : " + value.type);
            setOpaque(true);
            setBackground(isSelected ? Color.LIGHT_GRAY : Color.WHITE);
            return this;
        }
    }

    static class MaterialRow {
        final String name;
        final File j3mdFile;
        final List<AttributeRow> attributes;
        MaterialRow(String name, File j3mdFile, List<AttributeRow> attributes) {
            this.name = name;
            this.j3mdFile = j3mdFile;
            this.attributes = attributes;
        }
    }

    static class MaterialTableModel extends AbstractTableModel {
        private final List<MaterialRow> rows = new ArrayList<>();
        private final String[] columns = {"Name", "J3MD File", "Arg Count"};

        public void addRow(MaterialRow row) {
            rows.add(row);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        public void removeRow(int index) {
            rows.remove(index);
            fireTableRowsDeleted(index, index);
        }

        public List<MaterialRow> getEntries() {
            return new ArrayList<>(rows);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            MaterialRow mr = rows.get(row);
            return switch (col) {
                case 0 -> mr.name;
                case 1 -> mr.j3mdFile.getName();
                case 2 -> mr.attributes.size();
                default -> "";
            };
        }
    }
}
