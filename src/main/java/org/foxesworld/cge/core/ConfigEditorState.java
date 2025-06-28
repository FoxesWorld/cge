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
import javax.swing.border.TitledBorder;
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
 * The UI is built using Swing with a modern theme, featuring improved input controls
 * like JSpinner for numbers and JComboBox for enums.
 *
 * @author CalistaF0X & Gemini
 * @version 2025.06.21
 */
public class ConfigEditorState extends AbstractAppState implements ActionListener {

    private static final String TOGGLE_EDITOR_ACTION = "ToggleConfigEditor";
    private static final String FRAME_TITLE = "CGE Config Editor | by CalistaF0X";

    private final ConfigService configService;

    private JFrame editorFrame;
    private JList<String> fileListBox;
    private JPanel editorPanel;
    private JLabel statusLabel;
    private JScrollPane editorScrollPane;

    private String selectedFile;
    private Object currentConfigObject;
    private boolean isDirty = false; // Отслеживание несохраненных изменений

    /**
     * Внутренний класс для централизованного управления стилями UI.
     */
    private static class GtaTheme {
        // Colors
        static final Color BG_DARK = new Color(0x1A1A1D);
        static final Color PANEL_LIGHT = new Color(0x242428);
        static final Color ACCENT_PINK = new Color(0xE83F6F);
        static final Color ACCENT_BLUE = new Color(0x3f7de8);
        static final Color TEXT_LIGHT = new Color(0xE1E1E1);
        static final Color TEXT_MUTED = new Color(0x8A8A8A);
        static final Color SUCCESS_GREEN = new Color(0x57F287);
        static final Color ERROR_RED = new Color(0xED4245);

        // Fonts
        static final Font UI_FONT_BOLD = new Font("Bahnschrift", Font.BOLD, 14);
        static final Font UI_FONT_PLAIN = new Font("Bahnschrift", Font.PLAIN, 14);
        static final Font UI_FONT_SMALL = new Font("Bahnschrift", Font.PLAIN, 12);

        // Borders
        static final Border PADDING_BORDER = new EmptyBorder(10, 10, 10, 10);
        static final Border PINK_TOP_BORDER = new MatteBorder(2, 0, 0, 0, ACCENT_PINK);
        static final Border FIELD_BORDER_DEFAULT = new CompoundBorder(new MatteBorder(0, 0, 1, 0, TEXT_MUTED), new EmptyBorder(5, 5, 5, 5));
        static final Border FIELD_BORDER_FOCUSED = new CompoundBorder(new MatteBorder(0, 0, 2, 0, ACCENT_PINK), new EmptyBorder(5, 5, 5, 5));
        static final Border FIELD_BORDER_ERROR = new CompoundBorder(new MatteBorder(0, 0, 2, 0, ERROR_RED), new EmptyBorder(5, 5, 5, 5));
    }

    public ConfigEditorState(ConfigService configService) {
        this.configService = Objects.requireNonNull(configService, "ConfigService cannot be null");
    }

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);
        app.getInputManager().addMapping(TOGGLE_EDITOR_ACTION, new KeyTrigger(KeyInput.KEY_F12));
        app.getInputManager().addListener(this, TOGGLE_EDITOR_ACTION);
        SwingUtilities.invokeLater(this::createAndShowGUI);
    }

    private void createAndShowGUI() {
        FlatDarkLaf.setup();
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 15);
        UIManager.put("TextField.arc", 10);
        UIManager.put("Spinner.arc", 10);
        UIManager.put("ComboBox.arc", 10);
        UIManager.put("CheckBox.arc", 15);
        UIManager.put("SplitPane.dividerSize", 5);
        UIManager.put("SplitPane.background", GtaTheme.BG_DARK);
        UIManager.put("SplitPaneDivider.background", GtaTheme.BG_DARK);


        editorFrame = new JFrame(FRAME_TITLE);
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
        contentPane.setLayout(new BorderLayout(10, 10));
        ((JComponent) contentPane).setBorder(new EmptyBorder(10, 10, 10, 10));

        // --- Верхняя панель статуса ---
        statusLabel = new JLabel("SELECT A CONFIG FILE", SwingConstants.CENTER);
        statusLabel.setFont(GtaTheme.UI_FONT_BOLD);
        statusLabel.setForeground(GtaTheme.TEXT_MUTED);
        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        contentPane.add(statusLabel, BorderLayout.NORTH);

        // --- Панель с кнопками (Юг) ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(GtaTheme.BG_DARK);
        buttonPanel.add(ComponentFactory.createStyledButton("Reset", GtaTheme.TEXT_MUTED, e -> reloadConfig()));
        buttonPanel.add(ComponentFactory.createStyledButton("Save", GtaTheme.TEXT_MUTED, e -> saveConfig()));
        buttonPanel.add(ComponentFactory.createStyledButton("APPLY", GtaTheme.ACCENT_PINK, e -> applyChanges()));
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        // --- Основная рабочая область с разделением ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        splitPane.setResizeWeight(0.25); // Список файлов занимает 25% ширины
        contentPane.add(splitPane, BorderLayout.CENTER);

        // --- Левая панель: Список файлов ---
        DefaultListModel<String> fileListModel = new DefaultListModel<>();
        configService.getRegisteredConfigFiles().stream()
                .filter(configService::isExports)
                .forEach(fileListModel::addElement);
        fileListBox = new JList<>(fileListModel);
        fileListBox.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ComponentFactory.styleJList(fileListBox);
        fileListBox.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectionChanged();
            }
        });
        JScrollPane listScrollPane = new JScrollPane(fileListBox);
        listScrollPane.setBorder(BorderFactory.createLineBorder(GtaTheme.PANEL_LIGHT));
        splitPane.setLeftComponent(listScrollPane);

        // --- Правая панель: Редактор полей ---
        editorPanel = new JPanel(new GridBagLayout());
        editorPanel.setBackground(GtaTheme.PANEL_LIGHT);
        editorPanel.setBorder(GtaTheme.PADDING_BORDER);
        editorScrollPane = new JScrollPane(editorPanel);
        editorScrollPane.setBorder(null);
        editorScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        splitPane.setRightComponent(editorScrollPane);
    }

    private void selectionChanged() {
        // TODO: Добавить диалог подтверждения, если есть несохраненные изменения (isDirty)
        this.selectedFile = fileListBox.getSelectedValue();
        if (this.selectedFile != null) {
            reloadConfig();
        }
    }

    private void markDirty() {
        if (!isDirty) {
            isDirty = true;
            updateTitle();
        }
    }

    private void clearDirty() {
        if (isDirty) {
            isDirty = false;
            updateTitle();
        }
    }

    private void updateTitle() {
        SwingUtilities.invokeLater(() -> {
            String title = FRAME_TITLE;
            if (isDirty) {
                title = "* " + title;
            }
            editorFrame.setTitle(title);
        });
    }

    private boolean saveConfig() {
        if (selectedFile == null || currentConfigObject == null) {
            showStatus("NO FILE SELECTED TO SAVE", GtaTheme.TEXT_MUTED);
            return false;
        }
        try {
            configService.saveConfig(selectedFile, currentConfigObject);
            showStatus("SAVED: " + selectedFile.toUpperCase(), GtaTheme.SUCCESS_GREEN);
            clearDirty();
            return true;
        } catch (IOException e) {
            showStatus("ERROR SAVING " + selectedFile.toUpperCase(), GtaTheme.ERROR_RED);
            e.printStackTrace();
            return false;
        }
    }



    private void applyChanges() {
        if (saveConfig()) {
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
            clearDirty();
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
            buildPanelForObject(editorPanel, currentConfigObject, 0);
        }
        editorPanel.revalidate();
        editorPanel.repaint();
        SwingUtilities.invokeLater(() -> editorScrollPane.getVerticalScrollBar().setValue(0));
    }

    private void buildPanelForObject(JPanel panel, Object object, int depth) {
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

                // Label
                JLabel label = ComponentFactory.createEditorLabel(field.getName());
                gbc.gridx = 0;
                gbc.weightx = 0.3;
                panel.add(label, gbc);

                // Input Component
                gbc.gridx = 1;
                gbc.weightx = 0.7;

                if (type == boolean.class || type == Boolean.class) {
                    JCheckBox cb = ComponentFactory.createEditorCheckBox((Boolean) value);
                    cb.addItemListener(e -> {
                        try {
                            field.set(object, cb.isSelected());
                            markDirty();
                        } catch (IllegalAccessException ex) {
                            ex.printStackTrace();
                        }
                    });
                    panel.add(cb, gbc);

                } else if (type.isEnum()) {
                    JComboBox<Object> comboBox = ComponentFactory.createEditorComboBox(type.getEnumConstants(), value);
                    comboBox.addActionListener(e -> {
                        try {
                            field.set(object, comboBox.getSelectedItem());
                            markDirty();
                        } catch (IllegalAccessException ex) {
                            ex.printStackTrace();
                        }
                    });
                    panel.add(comboBox, gbc);

                } else if (type.isPrimitive() || Number.class.isAssignableFrom(type)) {
                    JSpinner spinner = ComponentFactory.createEditorSpinner(type, (Number) value);
                    spinner.addChangeListener(e -> {
                        try {
                            Object spinnerValue = spinner.getValue();
                            // JSpinner может возвращать Double для float, нужно преобразовать
                            if (type == float.class || type == Float.class) {
                                field.set(object, ((Number) spinnerValue).floatValue());
                            } else {
                                field.set(object, spinnerValue);
                            }
                            markDirty();
                        } catch (IllegalAccessException ex) {
                            ex.printStackTrace();
                        }
                    });
                    panel.add(spinner, gbc);

                } else if (type == String.class) {
                    JTextField tf = ComponentFactory.createEditorTextField((String) value);
                    tf.addActionListener(e -> { // Обновление по Enter
                        try {
                            field.set(object, tf.getText());
                            markDirty();
                        } catch (IllegalAccessException ex) {
                            ex.printStackTrace();
                        }
                    });
                    tf.addFocusListener(new FocusAdapter() { // Обновление при потере фокуса
                        @Override
                        public void focusLost(FocusEvent e) {
                            try {
                                field.set(object, tf.getText());
                                markDirty();
                            } catch (IllegalAccessException ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                    panel.add(tf, gbc);

                } else if (value != null) { // Вложенный объект
                    JPanel nestedPanel = new JPanel(new GridBagLayout());
                    nestedPanel.setOpaque(false);
                    // Визуально отделяем вложенный объект
                    TitledBorder titledBorder = BorderFactory.createTitledBorder(
                            new MatteBorder(1, 1, 1, 1, GtaTheme.TEXT_MUTED),
                            field.getName().toUpperCase()
                    );
                    titledBorder.setTitleFont(GtaTheme.UI_FONT_SMALL);
                    titledBorder.setTitleColor(GtaTheme.TEXT_MUTED);
                    nestedPanel.setBorder(new CompoundBorder(titledBorder, GtaTheme.PADDING_BORDER));

                    buildPanelForObject(nestedPanel, value, depth + 1);

                    gbc.gridx = 0;
                    gbc.gridwidth = 2;
                    gbc.insets = new Insets(15, 0, 15, 0);
                    panel.add(nestedPanel, gbc);
                    gbc.insets = new Insets(8, 5, 8, 5); // Сброс отступов
                }

                gbc.gridy++;

            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        // Заполнитель, чтобы все элементы прижимались к верху
        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        panel.add(filler, gbc);
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


    /**
     * Фабрика для создания и стилизации Swing компонентов в едином стиле.
     */
    private static class ComponentFactory {

        static JButton createStyledButton(String text, Color bgColor, java.awt.event.ActionListener listener) {
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

        static void styleJList(JList<String> list) {
            list.setBackground(GtaTheme.PANEL_LIGHT);
            list.setForeground(GtaTheme.TEXT_LIGHT);
            list.setFont(GtaTheme.UI_FONT_PLAIN);
            list.setSelectionBackground(GtaTheme.ACCENT_PINK);
            list.setSelectionForeground(Color.WHITE);
            list.setFixedCellHeight(35);
            list.setBorder(new EmptyBorder(10, 10, 10, 10));
            list.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    label.setBorder(new EmptyBorder(5, 15, 5, 15));
                    if (!isSelected) {
                        label.setBackground(GtaTheme.PANEL_LIGHT);
                    }
                    return label;
                }
            });
        }

        static JLabel createEditorLabel(String text) {
            JLabel label = new JLabel(text.toUpperCase() + ":");
            label.setFont(GtaTheme.UI_FONT_SMALL);
            label.setForeground(GtaTheme.TEXT_MUTED);
            return label;
        }

        static JCheckBox createEditorCheckBox(boolean isSelected) {
            JCheckBox cb = new JCheckBox();
            cb.setSelected(isSelected);
            cb.setOpaque(false);
            return cb;
        }

        static JTextField createEditorTextField(String text) {
            JTextField tf = new JTextField(text);
            styleComponent(tf);
            return tf;
        }

        static JSpinner createEditorSpinner(Class<?> type, Number value) {
            SpinnerNumberModel model;
            double stepSize = 1.0;
            if (type == float.class || type == Float.class || type == double.class || type == Double.class) {
                stepSize = 0.1;
            }

            if (Number.class.isAssignableFrom(type)) {
                model = new SpinnerNumberModel(value, null, null, stepSize);
            } else { // для примитивов
                model = new SpinnerNumberModel(value.doubleValue(), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, stepSize);
            }

            JSpinner spinner = new JSpinner(model);
            styleComponent(spinner.getEditor().getComponent(0)); // Стилизуем внутренний JTextField

            // Убираем рамку самого спиннера, оставляя только рамку поля ввода
            spinner.setBorder(null);

            // Стилизация кнопок
            for(Component c : spinner.getComponents()) {
                if(c instanceof JButton) {
                    ((JButton) c).setBackground(GtaTheme.BG_DARK);
                    ((JButton) c).setBorder(new MatteBorder(1,1,1,1, GtaTheme.TEXT_MUTED));
                }
            }
            return spinner;
        }

        static JComboBox<Object> createEditorComboBox(Object[] items, Object selected) {
            JComboBox<Object> cb = new JComboBox<>(items);
            cb.setSelectedItem(selected);
            cb.setFont(GtaTheme.UI_FONT_PLAIN);
            cb.setBackground(GtaTheme.BG_DARK);
            cb.setForeground(GtaTheme.TEXT_LIGHT);
            cb.setBorder(GtaTheme.FIELD_BORDER_DEFAULT);
            return cb;
        }

        private static void styleComponent(Component c) {
            if (c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                jc.setFont(GtaTheme.UI_FONT_PLAIN);
                jc.setForeground(GtaTheme.TEXT_LIGHT);
                jc.setBackground(GtaTheme.BG_DARK);
                jc.setBorder(GtaTheme.FIELD_BORDER_DEFAULT);
                if (c instanceof JTextField) {
                    ((JTextField)c).setCaretColor(GtaTheme.ACCENT_PINK);
                }
                jc.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        jc.setBorder(GtaTheme.FIELD_BORDER_FOCUSED);
                    }
                    @Override
                    public void focusLost(FocusEvent e) {
                        jc.setBorder(GtaTheme.FIELD_BORDER_DEFAULT);
                    }
                });
            }
        }
    }
}