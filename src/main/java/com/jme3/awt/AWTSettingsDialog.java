package com.jme3.awt;

import com.jme3.asset.AssetNotFoundException;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeSystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
import java.util.stream.Collectors;

/**
 * Modernized and optimized settings dialog with AAA-style polish.
 * - Clean layout (two-column rows)
 * - Frameless window with custom title bar and drag-to-move
 * - Improved controls styling and consistent palette
 */
public final class AWTSettingsDialog extends JFrame {

    private static final Logger logger = Logger.getLogger(AWTSettingsDialog.class.getName());
    private static final long serialVersionUID = 2L;

    public static final int NO_SELECTION = 0;
    public static final int APPROVE_SELECTION = 1;
    public static final int CANCEL_SELECTION = 2;

    // ---------- Design tokens / palette ----------
    private static final Color RAGE_BACKGROUND = new Color(0x1A1A1A);
    private static final Color RAGE_PANEL_BG = new Color(0x212125);
    private static final Color RAGE_COMPONENT_BG = new Color(0x2D2F33);
    private static final Color RAGE_FOREGROUND = new Color(0xE6E6E6);
    private static final Color RAGE_ACCENT = new Color(0xD32F2F);
    private static final Color RAGE_ACCENT_HOVER = new Color(0xF44336);
    private static final Color RAGE_BORDER_COLOR = new Color(0x3A3B3F);
    private static final Color RAGE_SEPARATOR_COLOR = new Color(0x343436);
    private static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);

    // ---------- State ----------
    private ResourceBundle resourceBundle;
    private final AppSettings source;
    private final URL imageFile;
    private DisplayMode[] modes;
    private DisplayMode[] windowModes;
    private static final DisplayMode[] WINDOW_DEFAULTS = {
            new DisplayMode(1024, 768, 24, 60),
            new DisplayMode(1280, 720, 24, 60),
            new DisplayMode(1280, 1024, 24, 60),
            new DisplayMode(1440, 900, 24, 60),
            new DisplayMode(1680, 1050, 24, 60)
    };

    // Controls
    private JCheckBox vsyncBox, gammaBox, fullscreenBox;
    private JComboBox<String> displayResCombo, colorDepthCombo, displayFreqCombo, antialiasCombo;
    private JCheckBox audioModuleBox, physicsModuleBox, aiModuleBox;
    private JLabel iconLabel;

    // Selection tracking
    private int selection = NO_SELECTION;
    private SelectionListener selectionListener;

    // Dragging
    private Point dragOffset;

    // Minimum supported sizes from AppSettings
    private final int minWidth;
    private final int minHeight;

    // ---------- Public API same as before ----------

    public static boolean showDialog(AppSettings sourceSettings) {
        return showDialog(sourceSettings, true);
    }

    public static boolean showDialog(AppSettings sourceSettings, boolean loadSettings) {
        String iconPath = sourceSettings.getSettingsDialogImage();
        URL iconUrl = JmeSystem.class.getResource(iconPath.startsWith("/") ? iconPath : "/" + iconPath);
        if (iconUrl == null) {
            throw new AssetNotFoundException(sourceSettings.getSettingsDialogImage());
        }
        return showDialog(sourceSettings, iconUrl, loadSettings);
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
            return result.get() == APPROVE_SELECTION;
        }
    }

    // ---------- Constructor & setup ----------

    protected AWTSettingsDialog(AppSettings source, URL imageFile, boolean loadSettings) {
        super();
        this.resourceBundle = safeBundle("com.jme3.app.SettingsDialog");
        this.source = Objects.requireNonNull(source, "Settings source cannot be null");
        this.imageFile = imageFile;

        setUndecorated(true);
        setAlwaysOnTop(true);
        setResizable(false);
        setBackground(new Color(0, 0, 0, 0)); // allow rounded corners if used

        // Load registry settings if requested
        AppSettings registrySettings = new AppSettings(true);
        try {
            registrySettings.load(source.getTitle() != null ? source.getTitle() : registrySettings.getTitle());
        } catch (BackingStoreException ex) {
            logger.log(Level.FINE, "Could not load registry settings", ex);
        }

        if (loadSettings) {
            source.copyFrom(registrySettings);
        } else if (!registrySettings.isEmpty()) {
            source.mergeFrom(registrySettings);
        }

        this.minWidth = Math.max(640, source.getMinWidth());
        this.minHeight = Math.max(480, source.getMinHeight());

        // build display mode list
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        this.modes = device.getDisplayModes();
        Arrays.sort(this.modes, Comparator.comparingInt(DisplayMode::getWidth)
                .thenComparingInt(DisplayMode::getHeight)
                .thenComparingInt(DisplayMode::getBitDepth)
                .thenComparingInt(DisplayMode::getRefreshRate));
        this.windowModes = mergeModes(modes, WINDOW_DEFAULTS);

        createUI();
        pack();
        setLocationRelativeTo(null);
    }

    // ---------- UI Construction (cleaned, modular) ----------

    private void createUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(RAGE_BACKGROUND);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RAGE_BORDER_COLOR, 1),
                new EmptyBorder(16, 18, 16, 18)
        ));
        setContentPane(root);

        // Title / header
        root.add(createTitleBar(), BorderLayout.NORTH);

        // Tabs center
        JTabbedPane tabs = new JTabbedPane();
        styleTabbedPane(tabs);
        tabs.addTab("GRAPHICS", createGraphicsPanel());
        tabs.addTab("MODULES", createModulesPanel());
        root.add(tabs, BorderLayout.CENTER);

        // Footer
        root.add(createButtonBar(), BorderLayout.SOUTH);

        // initialize values (after construction to avoid transient events)
        SwingUtilities.invokeLater(() -> {
            initValues();
            pack(); // recalc after populating combos
        });

        // Esc/Enter handling
        setupGlobalKeyBindings(root);
    }

    private JPanel createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout(8, 0));
        titleBar.setOpaque(false);
        titleBar.setBorder(new EmptyBorder(6, 6, 10, 6));

        // Icon + title
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        if (imageFile != null) {
            iconLabel = new JLabel(new ImageIcon(imageFile));
            left.add(iconLabel);
        } else {
            iconLabel = new JLabel();
        }
        JLabel title = new JLabel(Optional.ofNullable(source.getTitle()).orElse("Settings").toUpperCase());
        title.setFont(FONT_TITLE);
        title.setForeground(RAGE_FOREGROUND);
        left.add(title);

        // Close button on right
        JButton closeBtn = new JButton("\u2716"); // cross symbol
        closeBtn.setFocusable(false);
        closeBtn.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        closeBtn.setBackground(RAGE_COMPONENT_BG);
        closeBtn.setForeground(RAGE_FOREGROUND);
        closeBtn.setFont(FONT_BOLD);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> {
            setUserSelection(CANCEL_SELECTION);
            dispose();
        });
        closeBtn.addMouseListener(hoverBrightener(closeBtn, RAGE_COMPONENT_BG, RAGE_ACCENT_HOVER));

        titleBar.add(left, BorderLayout.WEST);
        titleBar.add(closeBtn, BorderLayout.EAST);

        // Drag-to-move support
        addDraggableListener(titleBar);

        return titleBar;
    }

    private JPanel createGraphicsPanel() {
        JPanel panel = createCardPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 6, 8, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;

        // Controls
        displayResCombo = createStyledComboBox();
        colorDepthCombo = createStyledComboBox();
        displayFreqCombo = createStyledComboBox();
        antialiasCombo = createStyledComboBox();

        vsyncBox = createStyledCheckBox(resource("checkbox.vsync", "VSync"));
        gammaBox = createStyledCheckBox(resource("checkbox.gamma", "Gamma"));
        fullscreenBox = createStyledCheckBox(resource("checkbox.fullscreen", "Fullscreen"));

        int row = 0;
        addRow(panel, gbc, row++, resource("label.resolutions", "Resolution"), displayResCombo);
        addRow(panel, gbc, row++, resource("label.colordepth", "Color Depth"), colorDepthCombo);
        addRow(panel, gbc, row++, resource("label.refresh", "Refresh Rate"), displayFreqCombo);

        // Separator
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 0, 12, 0);
        panel.add(createSeparator(), gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(8, 6, 8, 6);

        addRow(panel, gbc, row++, resource("label.antialias", "Anti-Aliasing"), antialiasCombo);
        addRow(panel, gbc, row++, "", fullscreenBox);
        addRow(panel, gbc, row++, "", vsyncBox);
        addRow(panel, gbc, row++, "", gammaBox);

        return panel;
    }

    private JPanel createModulesPanel() {
        JPanel panel = createCardPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        audioModuleBox = createStyledCheckBox("Audio Module");
        physicsModuleBox = createStyledCheckBox("Physics Module");
        aiModuleBox = createStyledCheckBox("AI Module");
        audioModuleBox.setSelected(true);
        physicsModuleBox.setSelected(true);
        aiModuleBox.setSelected(true);

        panel.add(audioModuleBox);
        panel.add(Box.createVerticalStrut(12));
        panel.add(physicsModuleBox);
        panel.add(Box.createVerticalStrut(12));
        panel.add(aiModuleBox);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel createButtonBar() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 12));
        footer.setOpaque(false);
        JButton ok = createPrimaryButton(resource("button.ok", "OK").toUpperCase());
        JButton cancel = createSecondaryButton(resource("button.cancel", "Cancel").toUpperCase());
        ok.addActionListener(e -> {
            if (verifyAndSaveCurrentSelection()) {
                setUserSelection(APPROVE_SELECTION);
                dispose();
            }
        });
        cancel.addActionListener(e -> {
            setUserSelection(CANCEL_SELECTION);
            dispose();
        });
        footer.add(ok);
        footer.add(cancel);
        return footer;
    }

    // ---------- Helpers (UI pieces) ----------

    private JPanel createCardPanel() {
        JPanel p = new JPanel();
        p.setOpaque(true);
        p.setBackground(RAGE_PANEL_BG);
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        return p;
    }

    private JSeparator createSeparator() {
        JSeparator s = new JSeparator();
        s.setForeground(RAGE_SEPARATOR_COLOR);
        s.setBackground(RAGE_SEPARATOR_COLOR);
        return s;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent comp) {
        gbc.gridy = row;
        if (label != null && !label.isEmpty()) {
            gbc.gridx = 0;
            gbc.weightx = 0.35;
            gbc.anchor = GridBagConstraints.EAST;
            panel.add(createLabel(label), gbc);
        } else {
            gbc.gridx = 0;
            gbc.weightx = 0.35;
            panel.add(new JLabel(""), gbc);
        }

        gbc.gridx = 1;
        gbc.weightx = 0.65;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(comp, gbc);
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text + ":");
        l.setForeground(RAGE_FOREGROUND);
        l.setFont(FONT_BOLD);
        return l;
    }

    private JComboBox<String> createStyledComboBox() {
        JComboBox<String> combo = new JComboBox<>();
        combo.setFont(FONT_REGULAR);
        combo.setBackground(RAGE_COMPONENT_BG);
        combo.setForeground(RAGE_FOREGROUND);
        combo.setBorder(BorderFactory.createLineBorder(RAGE_BORDER_COLOR));
        combo.setFocusable(false);
        // customize popup colors
        combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override
            protected ComboPopup createPopup() {
                BasicComboPopup popup = (BasicComboPopup) super.createPopup();
                popup.getList().setSelectionBackground(RAGE_ACCENT);
                popup.getList().setSelectionForeground(Color.WHITE);
                popup.getList().setBackground(RAGE_COMPONENT_BG);
                popup.getList().setForeground(RAGE_FOREGROUND);
                popup.setBorder(BorderFactory.createLineBorder(RAGE_BORDER_COLOR));
                return popup;
            }
        });
        return combo;
    }

    private JCheckBox createStyledCheckBox(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setFont(FONT_REGULAR);
        cb.setForeground(RAGE_FOREGROUND);
        cb.setOpaque(false);
        cb.setFocusable(false);
        return cb;
    }

    private JButton createPrimaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_BOLD);
        b.setForeground(Color.WHITE);
        b.setBackground(RAGE_ACCENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        b.addMouseListener(hoverBrightener(b, RAGE_ACCENT, RAGE_ACCENT_HOVER));
        return b;
    }

    private JButton createSecondaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(FONT_BOLD);
        b.setForeground(RAGE_FOREGROUND);
        b.setBackground(RAGE_COMPONENT_BG);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        b.addMouseListener(hoverBrightener(b, RAGE_COMPONENT_BG, RAGE_COMPONENT_BG.brighter()));
        return b;
    }

    private MouseAdapter hoverBrightener(AbstractButton btn, Color base, Color hover) {
        return new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(hover);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(base);
            }
        };
    }

    private void styleTabbedPane(JTabbedPane tabs) {
        tabs.setFont(FONT_BOLD);
        tabs.setForeground(RAGE_FOREGROUND);
        tabs.setBackground(RAGE_BACKGROUND);
        tabs.setOpaque(false);
        tabs.setBorder(new EmptyBorder(6, 0, 12, 0));
        UIManager.put("TabbedPane.contentAreaColor", RAGE_PANEL_BG);
        UIManager.put("TabbedPane.selected", RAGE_ACCENT);
    }

    // ---------- Initialization & event wiring ----------

    private void initValues() {
        updateResolutionChoices();
        if (source.getWidth() != 0 && source.getHeight() != 0) {
            displayResCombo.setSelectedItem(source.getWidth() + " x " + source.getHeight());
        } else if (displayResCombo.getItemCount() > 0) {
            displayResCombo.setSelectedIndex(displayResCombo.getItemCount() - 1);
        }
        updateAntialiasChoices();
        colorDepthCombo.setSelectedItem(source.getBitsPerPixel() + " bpp");

        fullscreenBox.setSelected(source.isFullscreen());
        vsyncBox.setSelected(source.isVSync());
        gammaBox.setSelected(source.isGammaCorrection());

        fullscreenBox.addActionListener(e -> updateResolutionChoices());
    }

    private void setupGlobalKeyBindings(JComponent root) {
        KeyAdapter key = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // click OK if present
                    for (Component c : ((Container) getContentPane()).getComponents()) {
                        // footer is last component, find buttons
                    }
                    // fallback to default button
                    JButton defaultBtn = getRootPane().getDefaultButton();
                    if (defaultBtn != null) defaultBtn.doClick();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    setUserSelection(CANCEL_SELECTION);
                    dispose();
                }
            }
        };
        addKeyListenerToAll(root, key);
    }

    private void addDraggableListener(JComponent comp) {
        comp.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }
        });
        comp.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Point loc = getLocation();
                int x = loc.x + e.getX() - dragOffset.x;
                int y = loc.y + e.getY() - dragOffset.y;
                setLocation(x, y);
            }
        });
    }

    private void addKeyListenerToAll(Component comp, KeyListener listener) {
        comp.addKeyListener(listener);
        if (comp instanceof Container) {
            for (Component c : ((Container) comp).getComponents()) {
                addKeyListenerToAll(c, listener);
            }
        }
    }

    // ---------- Resolution / Antialias / Helpers ----------

    private void updateResolutionChoices() {
        if (displayResCombo == null) return;
        if (!fullscreenBox.isSelected()) {
            displayResCombo.setModel(new DefaultComboBoxModel<>(getWindowedResolutions(windowModes)));
            colorDepthCombo.setModel(new DefaultComboBoxModel<>(new String[]{"24 bpp", "16 bpp"}));
            displayFreqCombo.setModel(new DefaultComboBoxModel<>(new String[]{resource("refresh.na", "N/A")}));
            displayFreqCombo.setEnabled(false);
        } else {
            displayResCombo.setModel(new DefaultComboBoxModel<>(getResolutions(modes)));
            displayFreqCombo.setEnabled(true);
        }
        updateDisplayChoices();
    }

    private void updateDisplayChoices() {
        if (displayResCombo.getSelectedItem() == null) return;
        String res = (String) displayResCombo.getSelectedItem();
        colorDepthCombo.setModel(new DefaultComboBoxModel<>(getDepths(res, modes)));
        displayFreqCombo.setModel(new DefaultComboBoxModel<>(getFrequencies(res, modes)));
        String depth = source.getBitsPerPixel() + " bpp";
        colorDepthCombo.setSelectedItem(depth);
        String bestFreq = getBestFrequency(res, modes);
        if (bestFreq != null) displayFreqCombo.setSelectedItem(bestFreq);
    }

    private void updateAntialiasChoices() {
        String[] choices = {resource("antialias.disabled", "Disabled"), "2x", "4x", "6x", "8x", "16x"};
        antialiasCombo.setModel(new DefaultComboBoxModel<>(choices));
        int samples = source.getSamples();
        String selection = switch (samples) {
            case 0 -> choices[0];
            default -> {
                if (samples >= 16) yield "16x";
                else if (samples >= 8) yield "8x";
                else if (samples >= 6) yield "6x";
                else if (samples >= 4) yield "4x";
                else yield "2x";
            }
        };
        antialiasCombo.setSelectedItem(selection);
    }

    private String[] getResolutions(DisplayMode[] dmodes) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (DisplayMode m : dmodes) {
            if (m.getWidth() >= minWidth && m.getHeight() >= minHeight) {
                set.add(m.getWidth() + " x " + m.getHeight());
            }
        }
        return set.toArray(new String[0]);
    }

    private String[] getWindowedResolutions(DisplayMode[] dmodes) {
        return getResolutions(dmodes);
    }

    private static String getBestFrequency(String resolution, DisplayMode[] modes) {
        int target = 60;
        int best = -1;
        for (DisplayMode m : modes) {
            if ((m.getWidth() + " x " + m.getHeight()).equals(resolution)) {
                if (best == -1 || Math.abs(m.getRefreshRate() - target) < Math.abs(best - target)) {
                    best = m.getRefreshRate();
                }
            }
        }
        return best == -1 ? null : best + " Hz";
    }

    private boolean verifyAndSaveCurrentSelection() {
        try {
            String display = Objects.requireNonNull((String) displayResCombo.getSelectedItem());
            boolean fullscreen = fullscreenBox.isSelected();
            boolean vsync = vsyncBox.isSelected();
            boolean gamma = gammaBox.isSelected();

            int width = Integer.parseInt(display.substring(0, display.indexOf(" x ")));
            int height = Integer.parseInt(display.substring(display.indexOf(" x ") + 3));
            String depthString = Objects.requireNonNull((String) colorDepthCombo.getSelectedItem());
            int depth = depthString.equals("???") ? 0 : Integer.parseInt(depthString.substring(0, depthString.indexOf(' ')));
            String freqString = (String) displayFreqCombo.getSelectedItem();
            int freq = fullscreen ? (freqString.equals("???") ? 0 : Integer.parseInt(freqString.substring(0, freqString.indexOf(' ')))) : -1;
            String aaString = (String) antialiasCombo.getSelectedItem();
            int multisample = aaString.equals(resource("antialias.disabled", "Disabled")) ? 0 : Integer.parseInt(aaString.substring(0, aaString.indexOf('x')));

            boolean valid = !fullscreen || GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().isFullScreenSupported();
            if (!valid) {
                showError(this, resource("error.unsupportedmode", "Unsupported mode"));
                return false;
            }

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
                logger.log(Level.WARNING, "Failed to save settings", ex);
            }
            return true;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error parsing settings values", ex);
            showError(this, resource("error.invalid", "Invalid settings"));
            return false;
        }
    }

    // ---------- Utilities & helpers ----------

    private static URL getURL(String file) {
        try {
            return new URL("file:" + file);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private static void showError(Component parent, String message) {
        UIManager.put("OptionPane.background", RAGE_BACKGROUND);
        UIManager.put("Panel.background", RAGE_PANEL_BG);
        UIManager.put("OptionPane.messageForeground", RAGE_FOREGROUND);
        UIManager.put("Button.background", RAGE_COMPONENT_BG);
        UIManager.put("Button.foreground", RAGE_FOREGROUND);
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void safeSetIconImages(List<? extends Image> icons) {
        try {
            Method m = this.getClass().getMethod("setIconImages", List.class);
            m.invoke(this, icons);
        } catch (Exception e) {
            logger.log(Level.FINE, "setIconImages not available on this JRE", e);
        }
    }

    private static DisplayMode[] mergeModes(DisplayMode[] deviceModes, DisplayMode[] defaults) {
        // merge keeping unique width x height, ordered
        LinkedHashMap<String, DisplayMode> map = new LinkedHashMap<>();
        for (DisplayMode d : deviceModes) {
            String key = d.getWidth() + "x" + d.getHeight();
            map.putIfAbsent(key, d);
        }
        for (DisplayMode d : defaults) {
            String key = d.getWidth() + "x" + d.getHeight();
            map.putIfAbsent(key, d);
        }
        return map.values().toArray(new DisplayMode[0]);
    }

    private String resource(String key, String fallback) {
        if (resourceBundle == null) return fallback;
        try {
            return resourceBundle.getString(key);
        } catch (MissingResourceException ex) {
            return fallback;
        }
    }

    private ResourceBundle safeBundle(String baseName) {
        try {
            return ResourceBundle.getBundle(baseName);
        } catch (Exception ex) {
            logger.log(Level.FINER, "Resource bundle not found: " + baseName, ex);
            return null;
        }
    }

    // ---------- Public getters / selection mechanics ----------

    public void setSelectionListener(SelectionListener sl) {
        this.selectionListener = sl;
    }

    public int getUserSelection() {
        return selection;
    }

    private void setUserSelection(int selection) {
        this.selection = selection;
        if (this.selectionListener != null) this.selectionListener.onSelection(selection);
    }

    public void showDialog() {
        SwingUtilities.invokeLater(() -> {
            this.setLocationRelativeTo(null);
            this.setVisible(true);
            this.toFront();
            // choose default button
            getRootPane().setDefaultButton(findDefaultOKButton());
        });
    }

    private JButton findDefaultOKButton() {
        Container c = (Container) getContentPane();
        for (Component comp : c.getComponents()) {
            if (comp instanceof JPanel) {
                for (Component inner : ((JPanel) comp).getComponents()) {
                    if (inner instanceof JButton && ((JButton) inner).getText() != null && ((JButton) inner).getText().equalsIgnoreCase(resource("button.ok", "OK"))) {
                        return (JButton) inner;
                    }
                }
            }
        }
        return null;
    }

    // ---------- Misc helpers (kept for parity) ----------

    private static String[] getDepths(String resolution, DisplayMode[] modes) {
        List<String> depths = new ArrayList<>();
        for (DisplayMode mode : modes) {
            if ((mode.getWidth() + " x " + mode.getHeight()).equals(resolution) && mode.getBitDepth() >= 16) {
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

    // ---------- Simple interfaces ----------

    public interface SelectionListener {
        void onSelection(int selection);
    }
}