package org.foxesworld.cge.core;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Objects;

/**
 * An AppState that provides a graphical UI for editing configuration files
 * managed by ConfigService. The editor is toggled by pressing F12.
 * The UI is built using Swing with a modern "GTA 6" inspired theme powered by FlatLaf.
 *
 * @author CalistaF0X & Gemini
 * @version 2025.06.20
 */
public class ConfigEditorState extends AbstractAppState implements ActionListener {

    private static final String TOGGLE_EDITOR_ACTION = "ToggleConfigEditor";
    private final ConfigService configService;

    private JFrame editorFrame;
    private JList<String> fileListBox;
    private JPanel editorPanel;
    private JLabel statusLabel;
    private JScrollPane editorScrollPane;

    private String selectedFile;
    private Object currentConfigObject;

    private static class GtaTheme {
        static final Color BG_DARK = new Color(0x1A1A1D);
        static final Color PANEL_LIGHT = new Color(0x242428);
        static final Color ACCENT_PINK = new Color(0xE83F6F);
        static final Color ACCENT_BLUE = new Color(0x3f7de8);
        static final Color TEXT_LIGHT = new Color(0xE1E1E1);
        static final Color TEXT_MUTED = new Color(0x8A8A8A);
        static final Color SUCCESS_GREEN = new Color(0x57F287);
        static final Color ERROR_RED = new Color(0xED4245);
        static final Font UI_FONT_BOLD = new Font("Bahnschrift", Font.BOLD, 14);
        static final Font UI_FONT_PLAIN = new Font("Bahnschrift", Font.PLAIN, 14);
        static final Font UI_FONT_SMALL = new Font("Bahnschrift", Font.PLAIN, 12);
        static final Border PADDING_BORDER = new EmptyBorder(10, 10, 10, 10);
        static final Border PINK_TOP_BORDER = new MatteBorder(2, 0, 0, 0, ACCENT_PINK);
    }

    public ConfigEditorState(ConfigService configService) {
        this.configService = Objects.requireNonNull(configService, "ConfigService cannot be null");
    }

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);
        SwingUtilities.invokeLater(this::createAndShowGUI);
        app.getInputManager().addMapping(TOGGLE_EDITOR_ACTION, new KeyTrigger(KeyInput.KEY_F12));
        app.getInputManager().addListener(this, TOGGLE_EDITOR_ACTION);
    }

    private void createAndShowGUI() {
        FlatDarkLaf.setup();
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 15);
        UIManager.put("TextField.arc", 15);
        UIManager.put("CheckBox.arc", 15);

        editorFrame = new JFrame("CGE Config Editor | by CalistaF0X");
        editorFrame.setAlwaysOnTop(true);
        editorFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        editorFrame.setSize(900, 700);
        editorFrame.setLocationRelativeTo(null);

        try {
            URL iconURL = getClass().getResource("/org/foxesworld/cge/ui/editor/cge_icon.png");
            if (iconURL != null) editorFrame.setIconImage(new ImageIcon(iconURL).getImage());
        } catch (Exception e) {
            System.err.println("Could not load window icon: " + e.getMessage());
        }

        Container contentPane = editorFrame.getContentPane();
        contentPane.setBackground(GtaTheme.BG_DARK);
        contentPane.setLayout(new BorderLayout(15, 15));
        ((JComponent) contentPane).setBorder(new EmptyBorder(10, 10, 10, 10));

        statusLabel = new JLabel("SELECT A CONFIG FILE", SwingConstants.CENTER);
        statusLabel.setFont(GtaTheme.UI_FONT_BOLD);
        statusLabel.setForeground(GtaTheme.TEXT_MUTED);
        statusLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPane.add(statusLabel, BorderLayout.NORTH);

        DefaultListModel<String> fileListModel = new DefaultListModel<>();
        configService.getRegisteredConfigFiles().forEach(fileListModel::addElement);
        fileListBox = new JList<>(fileListModel);
        // Styling...
        fileListBox.setBackground(GtaTheme.PANEL_LIGHT);
        fileListBox.setForeground(GtaTheme.TEXT_LIGHT);
        fileListBox.setFont(GtaTheme.UI_FONT_PLAIN);
        fileListBox.setSelectionBackground(GtaTheme.ACCENT_PINK);
        fileListBox.setSelectionForeground(Color.WHITE);
        fileListBox.setFixedCellHeight(35);
        fileListBox.setBorder(new EmptyBorder(10, 10, 10, 10));
        fileListBox.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(new EmptyBorder(5, 15, 5, 15));
                if (!isSelected) label.setBackground(GtaTheme.PANEL_LIGHT);
                return label;
            }
        });
        fileListBox.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selectionChanged();
        });
        JScrollPane listScrollPane = new JScrollPane(fileListBox);
        listScrollPane.setBorder(BorderFactory.createLineBorder(GtaTheme.PANEL_LIGHT));
        listScrollPane.setPreferredSize(new Dimension(250, 0));
        contentPane.add(listScrollPane, BorderLayout.WEST);

        editorPanel = new JPanel();
        editorPanel.setBackground(GtaTheme.PANEL_LIGHT);
        editorPanel.setLayout(new GridBagLayout());
        editorPanel.setBorder(GtaTheme.PADDING_BORDER);
        editorScrollPane = new JScrollPane(editorPanel);
        editorScrollPane.setBorder(null);
        editorScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        contentPane.add(editorScrollPane, BorderLayout.CENTER);

        // --- ИЗМЕНЕНО: Добавлена кнопка APPLY ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(GtaTheme.BG_DARK);
        JButton reloadButton = createStyledButton("Reload", GtaTheme.TEXT_MUTED, e -> reloadConfig());
        JButton saveButton = createStyledButton("Save", GtaTheme.TEXT_MUTED, e -> saveConfig());
        JButton applyButton = createStyledButton("APPLY", GtaTheme.ACCENT_PINK, e -> applyChanges());
        buttonPanel.add(reloadButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(applyButton);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);
    }

    private JButton createStyledButton(String text, Color bgColor, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.setFont(GtaTheme.UI_FONT_BOLD);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(10, 25, 10, 25));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addActionListener(listener);
        return button;
    }

    private void selectionChanged() {
        this.selectedFile = fileListBox.getSelectedValue();
        if (this.selectedFile != null) reloadConfig();
    }

    /**
     * Saves the current configuration to disk.
     * @return true if save was successful, false otherwise.
     */
    private boolean saveConfig() {
        if (selectedFile == null || currentConfigObject == null) {
            showStatus("NO FILE SELECTED TO SAVE", GtaTheme.TEXT_MUTED);
            return false;
        }
        try {
            configService.saveConfig(selectedFile, currentConfigObject);
            showStatus("SAVED: " + selectedFile.toUpperCase(), GtaTheme.SUCCESS_GREEN);
            return true;
        } catch (IOException e) {
            showStatus("ERROR SAVING " + selectedFile.toUpperCase(), GtaTheme.ERROR_RED);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Saves the configuration and then triggers a module reload in the engine.
     */
    private void applyChanges() {
        if (saveConfig()) { // First, save the config
            configService.triggerModuleReload(selectedFile);
            showStatus("APPLIED: " + selectedFile.toUpperCase(), GtaTheme.ACCENT_BLUE);
        }
    }

    private void reloadConfig() {
        if (selectedFile == null) {
            showStatus("NO FILE SELECTED TO RELOAD", GtaTheme.TEXT_MUTED);
            return;
        }
        try {
            this.currentConfigObject = configService.reloadConfig(selectedFile);
            SwingUtilities.invokeLater(this::rebuildEditorPanel);
            showStatus("LOADED: " + selectedFile.toUpperCase(), GtaTheme.ACCENT_PINK);
        } catch (IOException e) {
            showStatus("ERROR LOADING " + selectedFile.toUpperCase(), GtaTheme.ERROR_RED);
            e.printStackTrace();
        }
    }

    private void rebuildEditorPanel() {
        editorPanel.removeAll();
        if (currentConfigObject != null) {
            buildPanelForObject(editorPanel, currentConfigObject);
        }
        editorPanel.revalidate();
        editorPanel.repaint();
        SwingUtilities.invokeLater(() -> editorScrollPane.getVerticalScrollBar().setValue(0));
    }

    private void buildPanelForObject(JPanel panel, Object object) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(8, 5, 8, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        for (Field field : object.getClass().getDeclaredFields()) {
            if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);

            try {
                Object value = field.get(object);
                Class<?> type = field.getType();
                gbc.gridwidth = 1;

                JLabel label = new JLabel(field.getName().toUpperCase() + ":");
                label.setFont(GtaTheme.UI_FONT_SMALL);
                label.setForeground(GtaTheme.TEXT_MUTED);
                gbc.gridx = 0;
                gbc.weightx = 0.3;
                panel.add(label, gbc);

                gbc.gridx = 1;
                gbc.weightx = 0.7;

                if (type == boolean.class || type == Boolean.class) {
                    JCheckBox cb = new JCheckBox();
                    cb.setSelected((Boolean) value);
                    cb.setOpaque(false);
                    cb.addItemListener(e -> {
                        try {
                            field.set(object, cb.isSelected());
                        } catch (IllegalAccessException ex) {
                            ex.printStackTrace();
                        }
                    });
                    panel.add(cb, gbc);
                } else if (type.isPrimitive() || Number.class.isAssignableFrom(type) || type == String.class) {
                    JTextField tf = new JTextField(String.valueOf(value));
                    tf.setFont(GtaTheme.UI_FONT_PLAIN);
                    tf.setForeground(GtaTheme.TEXT_LIGHT);
                    tf.setBackground(GtaTheme.BG_DARK);
                    tf.setCaretColor(GtaTheme.ACCENT_PINK);
                    tf.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, GtaTheme.TEXT_MUTED), new EmptyBorder(5, 5, 5, 5)));
                    tf.addFocusListener(new FocusAdapter() {
                        @Override
                        public void focusLost(FocusEvent e) { parseAndSetField(tf, field, object); }
                        @Override
                        public void focusGained(FocusEvent e) { tf.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, GtaTheme.ACCENT_PINK), new EmptyBorder(5, 5, 5, 5))); }
                    });
                    tf.addActionListener(e -> parseAndSetField(tf, field, object));
                    panel.add(tf, gbc);
                } else {
                    JPanel nestedPanel = new JPanel(new GridBagLayout());
                    nestedPanel.setOpaque(false);
                    nestedPanel.setBorder(new CompoundBorder(GtaTheme.PINK_TOP_BORDER, new EmptyBorder(15, 0, 15, 0)));
                    buildPanelForObject(nestedPanel, value);
                    gbc.gridwidth = 2;
                    gbc.insets = new Insets(15, 0, 15, 0);
                    panel.add(nestedPanel, gbc);
                    gbc.insets = new Insets(8, 5, 8, 5);
                }
                gbc.gridy++;

            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        panel.add(filler, gbc);
    }

    private void parseAndSetField(JTextField tf, Field field, Object object) {
        try {
            String text = tf.getText();
            Class<?> type = field.getType();
            Object parsedValue = null;

            if (type == String.class) parsedValue = text;
            else if (type == int.class || type == Integer.class) parsedValue = Integer.parseInt(text);
            else if (type == float.class || type == Float.class) parsedValue = Float.parseFloat(text);
            else if (type == double.class || type == Double.class) parsedValue = Double.parseDouble(text);
            else if (type == long.class || type == Long.class) parsedValue = Long.parseLong(text);
            else if (type == short.class || type == Short.class) parsedValue = Short.parseShort(text);
            else if (type == byte.class || type == Byte.class) parsedValue = Byte.parseByte(text);

            if (parsedValue != null) {
                field.set(object, parsedValue);
                tf.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, GtaTheme.TEXT_MUTED), new EmptyBorder(5, 5, 5, 5)));
            }
        } catch (Exception e) {
            tf.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, GtaTheme.ERROR_RED), new EmptyBorder(5, 5, 5, 5)));
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (TOGGLE_EDITOR_ACTION.equals(name) && isPressed) {
            toggleEditorVisibility();
        }
    }

    private void toggleEditorVisibility() {
        if (editorFrame != null) {
            SwingUtilities.invokeLater(() -> editorFrame.setVisible(!editorFrame.isVisible()));
        }
    }

    private void showStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }
}