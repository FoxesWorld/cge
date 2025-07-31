package com.jme3.awt;

import com.jme3.asset.AssetNotFoundException;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeSystem;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;

/**
 * Улучшенный диалог настроек в стиле "RAGE" v2.
 * Особенности:
 * - Продвинутая 2-колоночная компоновка с разделителями.
 * - Окно без рамок, которое можно перетаскивать мышью.
 * - Улучшенная стилизация и рефакторинг кода.
 */
public final class AWTSettingsDialog extends JFrame {
    private static final Logger logger = Logger.getLogger(AWTSettingsDialog.class.getName());
    private static final long serialVersionUID = 2L; // Версия изменена
    public static final int NO_SELECTION = 0;
    public static final int APPROVE_SELECTION = 1;
    public static final int CANCEL_SELECTION = 2;

    // --- RAGE Style Palette ---
    private static final Color RAGE_BACKGROUND = new Color(0x1A1A1A);
    private static final Color RAGE_PANEL_BG = new Color(0x2C2C2C);
    private static final Color RAGE_COMPONENT_BG = new Color(0x383838);
    private static final Color RAGE_FOREGROUND = new Color(0xE0E0E0);
    private static final Color RAGE_ACCENT = new Color(0xD32F2F);
    private static final Color RAGE_ACCENT_HOVER = new Color(0xF44336);
    private static final Color RAGE_BORDER_COLOR = new Color(0x4A4A4A);
    private static final Color RAGE_SEPARATOR_COLOR = new Color(0x454545);

    private static final Font FONT_REGULAR = new Font("SansSerif", Font.PLAIN, 15);
    private static final Font FONT_BOLD = new Font("SansSerif", Font.BOLD, 15);
    private static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 26);

    private ResourceBundle resourceBundle;
    private final AppSettings source;
    private URL imageFile;
    private DisplayMode[] modes;
    private static final DisplayMode[] windowDefaults = {
            new DisplayMode(1024, 768, 24, 60), new DisplayMode(1280, 720, 24, 60),
            new DisplayMode(1280, 1024, 24, 60), new DisplayMode(1440, 900, 24, 60),
            new DisplayMode(1680, 1050, 24, 60)
    };
    private DisplayMode[] windowModes;

    private JCheckBox vsyncBox, gammaBox, fullscreenBox;
    private JComboBox<String> displayResCombo, colorDepthCombo, displayFreqCombo, antialiasCombo;
    private JCheckBox audioModuleBox, physicsModuleBox, aiModuleBox;
    private JLabel icon;
    private int selection;
    private SelectionListener selectionListener;
    private int minWidth, minHeight;
    private Point initialClick; // Для перетаскивания окна

    // ... (статические методы showDialog и конструкторы без изменений) ...

    public static boolean showDialog(AppSettings sourceSettings) {
        return showDialog(sourceSettings, true);
    }

    public static boolean showDialog(AppSettings sourceSettings, boolean loadSettings) {
        String iconPath = sourceSettings.getSettingsDialogImage();
        URL iconUrl = JmeSystem.class.getResource(iconPath.startsWith("/") ? iconPath : "/" + iconPath);
        if (iconUrl == null) {
            throw new AssetNotFoundException(sourceSettings.getSettingsDialogImage());
        } else {
            return showDialog(sourceSettings, iconUrl, loadSettings);
        }
    }

    public static boolean showDialog(AppSettings sourceSettings, String imageFile, boolean loadSettings) {
        return showDialog(sourceSettings, getURL(imageFile), loadSettings);
    }

    public static boolean showDialog(AppSettings sourceSettings, final URL imageFile, final boolean loadSettings) {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Cannot run from EDT");
        } else if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Cannot show dialog in headless environment");
        } else {
            final AppSettings settings = new AppSettings(false);
            settings.copyFrom(sourceSettings);
            final Object lock = new Object();
            final AtomicBoolean done = new AtomicBoolean();
            final AtomicInteger result = new AtomicInteger();
            SwingUtilities.invokeLater(() -> {
                SelectionListener selectionListener = selection -> {
                    synchronized (lock) {
                        done.set(true);
                        result.set(selection);
                        lock.notifyAll();
                    }
                };
                AWTSettingsDialog dialog = new AWTSettingsDialog(settings, imageFile, loadSettings);
                dialog.setSelectionListener(selectionListener);
                dialog.showDialog();
            });
            synchronized (lock) {
                while (!done.get()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            sourceSettings.copyFrom(settings);
            return result.get() == 1;
        }
    }

    protected AWTSettingsDialog(AppSettings source, URL imageFile, boolean loadSettings) {
        this.resourceBundle = ResourceBundle.getBundle("com.jme3.app/SettingsDialog");
        this.source = Objects.requireNonNull(source, "Settings source cannot be null");
        this.imageFile = imageFile;
        this.setAlwaysOnTop(true);
        this.setResizable(false);
        this.setUndecorated(true); // Включаем окно без рамок

        AppSettings registrySettings = new AppSettings(true);
        String appTitle = source.getTitle() != null ? source.getTitle() : registrySettings.getTitle();

        this.minWidth = source.getMinWidth();
        this.minHeight = source.getMinHeight();

        try {
            registrySettings.load(appTitle);
        } catch (BackingStoreException ex) {
            logger.log(Level.WARNING, "Failed to load settings", ex);
        }

        if (loadSettings) {
            source.copyFrom(registrySettings);
        } else if (!registrySettings.isEmpty()) {
            source.mergeFrom(registrySettings);
        }

        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        this.modes = device.getDisplayModes();
        Arrays.sort(this.modes, new DisplayModeSorter());

        DisplayMode[] merged = new DisplayMode[this.modes.length + windowDefaults.length];
        int wdIndex = 0, dmIndex = 0, mergedIndex;
        for (mergedIndex = 0; mergedIndex < merged.length && (wdIndex < windowDefaults.length || dmIndex < this.modes.length); ++mergedIndex) {
            if (dmIndex >= this.modes.length) merged[mergedIndex] = windowDefaults[wdIndex++];
            else if (wdIndex >= windowDefaults.length) merged[mergedIndex] = this.modes[dmIndex++];
            else if (this.modes[dmIndex].getWidth() < windowDefaults[wdIndex].getWidth())
                merged[mergedIndex] = this.modes[dmIndex++];
            else if (this.modes[dmIndex].getWidth() == windowDefaults[wdIndex].getWidth()) {
                if (this.modes[dmIndex].getHeight() < windowDefaults[wdIndex].getHeight())
                    merged[mergedIndex] = this.modes[dmIndex++];
                else if (this.modes[dmIndex].getHeight() == windowDefaults[wdIndex].getHeight()) {
                    merged[mergedIndex] = this.modes[dmIndex++];
                    ++wdIndex;
                } else merged[mergedIndex] = windowDefaults[wdIndex++];
            } else merged[mergedIndex] = windowDefaults[wdIndex++];
        }
        this.windowModes = merged.length == mergedIndex
                ? merged
                : Arrays.copyOfRange(merged, 0, mergedIndex);

        createUI_RageStyleV2();
    }

    /**
     * Главный метод, собирающий UI из стилизованных компонентов.
     */
    private void createUI_RageStyleV2() {
        // --- Главная панель с рамкой ---
        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(RAGE_BACKGROUND);
        rootPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RAGE_BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));
        this.setContentPane(rootPanel);
        addDraggableListener(rootPanel);

        // --- Шапка (Логотип и заголовок) ---
        rootPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        // --- Вкладки ---
        JTabbedPane tabbedPane = new JTabbedPane();
        styleTabbedPane(tabbedPane);

        // Добавляем вкладки, созданные в отдельных методах
        tabbedPane.addTab(" GRAPHICS ", createGraphicsPanel());
        tabbedPane.addTab(" MODULES ", createModulesPanel());
        // Иконки для вкладок (опционально, но красиво)
        // ImageIcon graphicsIcon = new ImageIcon(getClass().getResource("/path/to/graphics_icon.png"));
        // tabbedPane.setIconAt(0, graphicsIcon);

        rootPanel.add(tabbedPane, BorderLayout.CENTER);

        // --- Подвал (Кнопки) ---
        rootPanel.add(createButtonPanel(), BorderLayout.SOUTH);

        // --- Инициализация и обработчики ---
        setupActionHandlers();

        this.pack();
        rootPanel.getRootPane().setDefaultButton((JButton) ((JPanel) rootPanel.getComponent(2)).getComponent(0)); // Get OK button

        // Инициализация значений после того, как все компоненты созданы
        SwingUtilities.invokeLater(() -> {
            updateResolutionChoices();
            if (source.getWidth() != 0 && source.getHeight() != 0) {
                displayResCombo.setSelectedItem(source.getWidth() + " x " + source.getHeight());
            } else if (displayResCombo.getItemCount() > 0) {
                displayResCombo.setSelectedIndex(displayResCombo.getItemCount() - 1);
            }
            updateAntialiasChoices();
            colorDepthCombo.setSelectedItem(source.getBitsPerPixel() + " bpp");
        });

        if (this.source.getIcons() != null) {
            this.safeSetIconImages(Arrays.asList((BufferedImage[]) this.source.getIcons()));
        }
    }

    /**
     * Создает панель шапки с логотипом и заголовком.
     */
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        icon = new JLabel(imageFile != null ? new ImageIcon(imageFile) : null);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        icon.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        String titleText = this.source.getTitle() != null ? this.source.getTitle().toUpperCase() : "SETTINGS";
        JLabel title = new JLabel(titleText);
        title.setFont(FONT_TITLE);
        title.setForeground(RAGE_FOREGROUND);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(icon);
        headerPanel.add(title);
        return headerPanel;
    }

    /**
     * Создает и компонует вкладку настроек графики.
     */
    private JPanel createGraphicsPanel() {
        JPanel panel = createStyledPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Общие настройки для всех
        gbc.insets = new Insets(8, 5, 8, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int y = 0; // Текущая строка

        // --- Настройки дисплея ---
        displayResCombo = createStyledComboBox();
        colorDepthCombo = createStyledComboBox();
        displayFreqCombo = createStyledComboBox();

        addSettingRow(panel, gbc, y++, resourceBundle.getString("label.resolutions"), displayResCombo);
        addSettingRow(panel, gbc, y++, resourceBundle.getString("label.colordepth"), colorDepthCombo);
        addSettingRow(panel, gbc, y++, resourceBundle.getString("label.refresh"), displayFreqCombo);

        // --- Разделитель ---
        gbc.gridy = y++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 0, 10, 0);
        panel.add(createStyledSeparator(), gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(8, 5, 8, 5); // Сброс

        // --- Настройки качества ---
        antialiasCombo = createStyledComboBox();
        fullscreenBox = createStyledCheckBox(resourceBundle.getString("checkbox.fullscreen"));
        vsyncBox = createStyledCheckBox(resourceBundle.getString("checkbox.vsync"));
        gammaBox = createStyledCheckBox(resourceBundle.getString("checkbox.gamma"));

        addSettingRow(panel, gbc, y++, resourceBundle.getString("label.antialias"), antialiasCombo);
        addSettingRow(panel, gbc, y++, "", fullscreenBox);
        addSettingRow(panel, gbc, y++, "", vsyncBox);
        addSettingRow(panel, gbc, y++, "", gammaBox);

        return panel;
    }

    /**
     * Создает и компонует вкладку модулей.
     */
    private JPanel createModulesPanel() {
        JPanel panel = createStyledPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        audioModuleBox = createStyledCheckBox("Audio Module");
        physicsModuleBox = createStyledCheckBox("Physics Module");
        aiModuleBox = createStyledCheckBox("AI Module");

        audioModuleBox.setSelected(true);
        physicsModuleBox.setSelected(true);
        aiModuleBox.setSelected(true);

        panel.add(audioModuleBox);
        panel.add(Box.createRigidArea(new Dimension(0, 15)));
        panel.add(physicsModuleBox);
        panel.add(Box.createRigidArea(new Dimension(0, 15)));
        panel.add(aiModuleBox);
        panel.add(Box.createVerticalGlue()); // Занимает оставшееся место

        return panel;
    }

    /**
     * Создает панель с кнопками OK и Cancel.
     */
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        JButton ok = createStyledButton(resourceBundle.getString("button.ok").toUpperCase(), true);
        JButton cancel = createStyledButton(resourceBundle.getString("button.cancel").toUpperCase(), false);

        buttonPanel.add(ok);
        buttonPanel.add(cancel);
        return buttonPanel;
    }

    /**
     * Устанавливает все обработчики событий для компонентов.
     */
    private void setupActionHandlers() {
        // Кнопки OK и Cancel находятся на buttonPanel, который является 3-м компонентом (index 2) в rootPanel
        JPanel buttonPanel = (JPanel) getContentPane().getComponent(2);
        JButton okButton = (JButton) buttonPanel.getComponent(0);
        JButton cancelButton = (JButton) buttonPanel.getComponent(1);

        okButton.addActionListener(e -> {
            if (verifyAndSaveCurrentSelection()) {
                setUserSelection(APPROVE_SELECTION);
                dispose();
            }
        });
        cancelButton.addActionListener(e -> {
            setUserSelection(CANCEL_SELECTION);
            dispose();
        });

        fullscreenBox.setSelected(source.isFullscreen());
        fullscreenBox.addActionListener(e -> updateResolutionChoices());
        vsyncBox.setSelected(source.isVSync());
        gammaBox.setSelected(source.isGammaCorrection());

        KeyListener keyListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) okButton.doClick();
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) cancelButton.doClick();
            }
        };
        addKeyListenerToAll(this, keyListener);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setUserSelection(CANCEL_SELECTION);
                dispose();
            }
        });
    }

    // --- Вспомогательные методы для стилизации и компоновки ---

    private void addSettingRow(JPanel panel, GridBagConstraints gbc, int y, String labelText, JComponent component) {
        gbc.gridy = y;

        if (!labelText.isEmpty()) {
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.EAST;
            gbc.weightx = 0.3;
            panel.add(createStyledLabel(labelText), gbc);
        }

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0.7;
        panel.add(component, gbc);
    }

    private JSeparator createStyledSeparator() {
        JSeparator separator = new JSeparator();
        separator.setForeground(RAGE_SEPARATOR_COLOR);
        separator.setBackground(RAGE_SEPARATOR_COLOR);
        return separator;
    }

    private JPanel createStyledPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(RAGE_PANEL_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        return panel;
    }

    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text + ":");
        label.setFont(FONT_BOLD);
        label.setForeground(RAGE_FOREGROUND);
        return label;
    }

    private JComboBox<String> createStyledComboBox() {
        JComboBox<String> combo = new JComboBox<>();
        combo.setFont(FONT_REGULAR);
        combo.setBackground(RAGE_COMPONENT_BG);
        combo.setForeground(RAGE_FOREGROUND);
        combo.setBorder(BorderFactory.createLineBorder(RAGE_BORDER_COLOR));
        combo.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton button = new JButton("▼");
                button.setFont(FONT_REGULAR.deriveFont(10f));
                button.setBackground(RAGE_COMPONENT_BG);
                button.setForeground(RAGE_FOREGROUND);
                button.setBorder(BorderFactory.createEmptyBorder());
                return button;
            }

            @Override
            protected ComboPopup createPopup() {
                BasicComboPopup popup = (BasicComboPopup) super.createPopup();
                popup.getList().setSelectionBackground(RAGE_ACCENT);
                popup.getList().setSelectionForeground(Color.WHITE);
                popup.getList().setBackground(RAGE_COMPONENT_BG);
                popup.getList().setForeground(RAGE_FOREGROUND);
                popup.setBorder(BorderFactory.createLineBorder(RAGE_BORDER_COLOR, 1));
                return popup;
            }
        });
        return combo;
    }

    private JCheckBox createStyledCheckBox(String text) {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.setFont(FONT_REGULAR);
        checkBox.setForeground(RAGE_FOREGROUND);
        checkBox.setOpaque(false);
        checkBox.setFocusable(false);
        checkBox.setIconTextGap(10);
        return checkBox;
    }

    private JButton createStyledButton(String text, boolean isPrimary) {
        JButton button = new JButton(text);
        button.setFont(FONT_BOLD);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 35, 10, 35));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        if (isPrimary) {
            button.setBackground(RAGE_ACCENT);
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(RAGE_COMPONENT_BG);
            button.setForeground(RAGE_FOREGROUND);
        }

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(isPrimary ? RAGE_ACCENT_HOVER : RAGE_COMPONENT_BG.brighter());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(isPrimary ? RAGE_ACCENT : RAGE_COMPONENT_BG);
            }
        });
        return button;
    }

    private void styleTabbedPane(JTabbedPane tabbedPane) {
        tabbedPane.setFont(FONT_BOLD);
        tabbedPane.setBackground(RAGE_BACKGROUND);
        tabbedPane.setForeground(RAGE_FOREGROUND);
        tabbedPane.setOpaque(false);
        tabbedPane.setFocusable(false);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));

        UIManager.put("TabbedPane.contentAreaColor", RAGE_PANEL_BG);
        UIManager.put("TabbedPane.selected", RAGE_ACCENT);
        UIManager.put("TabbedPane.background", RAGE_BACKGROUND);
        UIManager.put("TabbedPane.foreground", RAGE_FOREGROUND.darker());
        UIManager.put("TabbedPane.unselectedTabForeground", RAGE_FOREGROUND.darker());
        UIManager.put("TabbedPane.borderHightlightColor", RAGE_BORDER_COLOR);
        UIManager.put("TabbedPane.darkShadow", RAGE_BACKGROUND);
        UIManager.put("TabbedPane.light", RAGE_BACKGROUND);
        UIManager.put("TabbedPane.selectHighlight", RAGE_PANEL_BG); // Цвет выделения активной вкладки
        UIManager.put("TabbedPane.tabAreaInsets", new Insets(4, 4, 4, 4));
        UIManager.put("TabbedPane.tabInsets", new Insets(8, 20, 8, 20));
        UIManager.put("TabbedPane.focus", RAGE_BACKGROUND); // Убираем рамку фокуса
    }

    /**
     * Добавляет слушателей для перетаскивания окна.
     */
    private void addDraggableListener(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
                component.getComponentAt(initialClick);
            }
        });

        component.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int thisX = getLocation().x;
                int thisY = getLocation().y;
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                int X = thisX + xMoved;
                int Y = thisY + yMoved;
                setLocation(X, Y);
            }
        });
    }

    /**
     * Рекурсивно добавляет KeyListener ко всем компонентам.
     */
    private void addKeyListenerToAll(Component comp, KeyListener listener) {
        comp.addKeyListener(listener);
        if (comp instanceof Container) {
            for (Component c : ((Container) comp).getComponents()) {
                addKeyListenerToAll(c, listener);
            }
        }
    }


    // --- Оригинальные методы класса (логика осталась прежней) ---

    public void setSelectionListener(SelectionListener sl) {
        this.selectionListener = sl;
    }

    public int getUserSelection() {
        return this.selection;
    }

    private void setUserSelection(int selection) {
        this.selection = selection;
        if (this.selectionListener != null) this.selectionListener.onSelection(selection);
    }

    public void showDialog() {
        this.setLocationRelativeTo(null);
        this.setVisible(true);
        this.toFront();
    }

    private boolean verifyAndSaveCurrentSelection() {
        String display = (String) displayResCombo.getSelectedItem();
        boolean fullscreen = fullscreenBox.isSelected();
        boolean vsync = vsyncBox.isSelected();
        boolean gamma = gammaBox.isSelected();
        int width = Integer.parseInt(display.substring(0, display.indexOf(" x ")));
        int height = Integer.parseInt(display.substring(display.indexOf(" x ") + 3));
        String depthString = (String) colorDepthCombo.getSelectedItem();
        int depth = depthString.equals("???") ? 0 : Integer.parseInt(depthString.substring(0, depthString.indexOf(' ')));
        String freqString = (String) displayFreqCombo.getSelectedItem();
        int freq = fullscreen ? (freqString.equals("???") ? 0 : Integer.parseInt(freqString.substring(0, freqString.indexOf(' ')))) : -1;
        String aaString = (String) antialiasCombo.getSelectedItem();
        int multisample = aaString.equals(resourceBundle.getString("antialias.disabled")) ? 0 : Integer.parseInt(aaString.substring(0, aaString.indexOf('x')));

        boolean valid = !fullscreen || GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().isFullScreenSupported();
        if (valid) {
            source.setWidth(width);
            source.setHeight(height);
            source.setBitsPerPixel(depth);
            source.setFrequency(freq);
            source.setFullscreen(fullscreen);
            source.setVSync(vsync);
            source.setGammaCorrection(gamma);
            source.setSamples(multisample);
            try {
                source.save(source.getTitle());
            } catch (BackingStoreException ex) {
                logger.log(Level.WARNING, "Failed to save setting changes", ex);
            }
        } else {
            showError(this, resourceBundle.getString("error.unsupportedmode"));
        }
        return valid;
    }

    private void updateDisplayChoices() {
        if (fullscreenBox.isSelected() && displayResCombo.getSelectedItem() != null) {
            String resolution = (String) displayResCombo.getSelectedItem();
            String colorDepth = (String) colorDepthCombo.getSelectedItem();
            if (colorDepth == null) colorDepth = source.getBitsPerPixel() + " bpp";
            String displayFreq = (String) displayFreqCombo.getSelectedItem();
            if (displayFreq == null) displayFreq = source.getFrequency() + " Hz";

            colorDepthCombo.setModel(new DefaultComboBoxModel<>(getDepths(resolution, modes)));
            colorDepthCombo.setSelectedItem(colorDepth);

            displayFreqCombo.setModel(new DefaultComboBoxModel<>(getFrequencies(resolution, modes)));
            displayFreqCombo.setSelectedItem(displayFreq);
            if (displayFreqCombo.getSelectedIndex() == -1) { // Если такого значения нет
                displayFreqCombo.setSelectedItem(getBestFrequency(resolution, modes));
            }
        }
    }

    private void updateResolutionChoices() {
        if (!fullscreenBox.isSelected()) {
            displayResCombo.setModel(new DefaultComboBoxModel<>(getWindowedResolutions(windowModes)));
            colorDepthCombo.setModel(new DefaultComboBoxModel<>(new String[]{"24 bpp", "16 bpp"}));
            displayFreqCombo.setModel(new DefaultComboBoxModel<>(new String[]{resourceBundle.getString("refresh.na")}));
            displayFreqCombo.setEnabled(false);
        } else {
            displayResCombo.setModel(new DefaultComboBoxModel<>(getResolutions(modes)));
            displayFreqCombo.setEnabled(true);
        }
        updateDisplayChoices(); // Обновляем зависимые комбо-боксы
    }

    private void updateAntialiasChoices() {
        String[] choices = {resourceBundle.getString("antialias.disabled"), "2x", "4x", "6x", "8x", "16x"};
        antialiasCombo.setModel(new DefaultComboBoxModel<>(choices));
        int samples = source.getSamples();
        String selection = "2x";
        if (samples <= 0) selection = choices[0];
        else if (samples >= 16) selection = "16x";
        else if (samples >= 8) selection = "8x";
        else if (samples >= 6) selection = "6x";
        else if (samples >= 4) selection = "4x";
        antialiasCombo.setSelectedItem(selection);
    }

    private String[] getResolutions(DisplayMode[] dmodes) {
        Set<String> resolutions = new LinkedHashSet<>();
        for (DisplayMode mode : dmodes) {
            if (mode.getWidth() >= minWidth && mode.getHeight() >= minHeight) {
                resolutions.add(mode.getWidth() + " x " + mode.getHeight());
            }
        }
        return resolutions.toArray(new String[0]);
    }

    private String[] getWindowedResolutions(DisplayMode[] dmodes) {
        return getResolutions(dmodes);
    }

    // --- Неизмененные статические и служебные методы ---
    private static URL getURL(String file) {
        try {
            return new URL("file:" + file);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private void safeSetIconImages(List<? extends Image> icons) {
        try {
            Method setIconImages = this.getClass().getMethod("setIconImages", List.class);
            setIconImages.invoke(this, icons);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error setting icon images", e);
        }
    }

    private static void showError(Component parent, String message) {
        UIManager.put("OptionPane.background", RAGE_BACKGROUND);
        UIManager.put("Panel.background", RAGE_BACKGROUND);
        UIManager.put("OptionPane.messageForeground", RAGE_FOREGROUND);
        UIManager.put("Button.background", RAGE_COMPONENT_BG);
        UIManager.put("Button.foreground", RAGE_FOREGROUND);
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static String[] getDepths(String resolution, DisplayMode[] modes) {
        List<String> depths = new ArrayList<>();
        for (DisplayMode mode : modes) {
            if (mode.getBitDepth() >= 16 && (mode.getWidth() + " x " + mode.getHeight()).equals(resolution)) {
                String d = mode.getBitDepth() + " bpp";
                if (!depths.contains(d)) depths.add(d);
            }
        }
        if (depths.isEmpty()) depths.add("24 bpp");
        return depths.toArray(new String[0]);
    }

    private static String[] getFrequencies(String resolution, DisplayMode[] modes) {
        List<String> freqs = new ArrayList<>();
        for (DisplayMode mode : modes) {
            if ((mode.getWidth() + " x " + mode.getHeight()).equals(resolution)) {
                String f = mode.getRefreshRate() == 0 ? "???" : mode.getRefreshRate() + " Hz";
                if (!freqs.contains(f)) freqs.add(f);
            }
        }
        return freqs.toArray(new String[0]);
    }

    private static String getBestFrequency(String resolution, DisplayMode[] modes) {
        int best = 60, closest = Integer.MAX_VALUE;
        for (DisplayMode mode : modes) {
            if ((mode.getWidth() + " x " + mode.getHeight()).equals(resolution) && Math.abs(mode.getRefreshRate() - 60) < Math.abs(closest - 60))
                closest = mode.getRefreshRate();
        }
        return closest != Integer.MAX_VALUE ? closest + " Hz" : null;
    }

    private static class DisplayModeSorter implements Comparator<DisplayMode> {
        public int compare(DisplayMode a, DisplayMode b) {
            if (a.getWidth() != b.getWidth()) return Integer.compare(a.getWidth(), b.getWidth());
            if (a.getHeight() != b.getHeight()) return Integer.compare(a.getHeight(), b.getHeight());
            if (a.getBitDepth() != b.getBitDepth()) return Integer.compare(a.getBitDepth(), b.getBitDepth());
            return Integer.compare(a.getRefreshRate(), b.getRefreshRate());
        }
    }

    public interface SelectionListener {
        void onSelection(int selection);
    }
}