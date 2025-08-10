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
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;

/**
 * Modernized settings dialog — removed manual styling so UI is driven by Look&Feel / UIManager.
 * Keeps behavior: titlebar controls, dragging, maximize/restore, keyboard shortcuts, persisted settings.
 */
public final class AWTSettingsDialog extends JFrame {

    private static final Logger logger = Logger.getLogger(AWTSettingsDialog.class.getName());
    private static final long serialVersionUID = 2L;

    public static final int NO_SELECTION = 0;
    public static final int APPROVE_SELECTION = 1;
    public static final int CANCEL_SELECTION = 2;

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

    // Buttons cached for O(1) access
    private JButton okButton;
    private JButton cancelButton;
    private JButton minimizeBtn;
    private JButton maximizeBtn;
    private JButton closeBtn;

    // Selection tracking
    private int selection = NO_SELECTION;
    private SelectionListener selectionListener;

    // Dragging
    private Point dragOffset;

    // Maximize/restore
    private boolean maximized = false;
    private Rectangle restoredBounds = null;

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
        setBackground(new Color(0, 0, 0, 0)); // allow rounded corners if used by platform LAF

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

    // ---------- UI Construction (LAF-driven, minimal manual styling) ----------

    private void createUI() {
        JPanel root = new JPanel(new BorderLayout());
        // do not force colors/fonts — allow Look&Feel to paint
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Panel.border") != null ? UIManager.getColor("Panel.border") : Color.GRAY, 1),
                new EmptyBorder(12, 14, 12, 14)
        ));
        setContentPane(root);

        // Title / header
        root.add(createTitleBar(), BorderLayout.NORTH);

        // Tabs center
        JTabbedPane tabs = new JTabbedPane();
        // keep tabs default LAF behavior
        tabs.addTab(resource("tab.graphics", "GRAPHICS"), createGraphicsPanel());
        tabs.addTab(resource("tab.modules", "MODULES"), createModulesPanel());
        root.add(tabs, BorderLayout.CENTER);

        // Footer
        root.add(createButtonBar(), BorderLayout.SOUTH);

        // initialize values (after construction to avoid transient events)
        SwingUtilities.invokeLater(() -> {
            initValues();
            pack(); // recalc after populating combos
        });

        // Esc/Enter handling using root pane input map
        setupGlobalKeyBindings();
    }

    private JPanel createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout(8, 0));
        titleBar.setOpaque(false);
        titleBar.setBorder(new EmptyBorder(6, 6, 8, 6));

        // Icon + title
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        if (imageFile != null) {
            ImageIcon ic = loadScaledIcon(imageFile, 36, 36);
            iconLabel = new JLabel(ic);
            iconLabel.setBorder(new EmptyBorder(2, 0, 0, 0));
            left.add(iconLabel);
        } else {
            iconLabel = new JLabel();
        }
        JLabel title = new JLabel(Optional.ofNullable(source.getTitle()).orElse("Settings"));
        // rely on LAF font and color
        left.add(title);

        // Center filler
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.setOpaque(false);

        // Close/minimize/maximize on right
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        minimizeBtn = createWindowControlButton("–", resource("tt.minimize", "Minimize (Alt+M)"));
        maximizeBtn = createWindowControlButton("□", resource("tt.maximize", "Maximize (Alt+X)"));
        closeBtn = createWindowControlButton("✖", resource("tt.close", "Close (Alt+C)"));

        // mnemonics and behavior
        minimizeBtn.setMnemonic(KeyEvent.VK_M);
        maximizeBtn.setMnemonic(KeyEvent.VK_X);
        closeBtn.setMnemonic(KeyEvent.VK_C);

        minimizeBtn.addActionListener(e -> setState(Frame.ICONIFIED));
        maximizeBtn.addActionListener(e -> toggleMaximizeRestore());
        closeBtn.addActionListener(e -> {
            setUserSelection(CANCEL_SELECTION);
            dispose();
        });

        right.add(minimizeBtn);
        right.add(maximizeBtn);
        right.add(closeBtn);

        titleBar.add(left, BorderLayout.WEST);
        titleBar.add(center, BorderLayout.CENTER);
        titleBar.add(right, BorderLayout.EAST);

        // Drag-to-move + double-click to maximize/restore
        MouseAdapter dragAdapter = new MouseAdapter() {
            private long lastClick = 0L;

            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
                if (maximized) {
                    // approximate restored drag offset
                    dragOffset = new Point(getWidth() / 2, 10);
                }

                long now = System.currentTimeMillis();
                if (now - lastClick < 400) {
                    toggleMaximizeRestore();
                }
                lastClick = now;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (maximized) {
                    restoreFromMaximizedAt(e.getLocationOnScreen());
                    return;
                }
                Point loc = getLocation();
                int x = loc.x + e.getX() - dragOffset.x;
                int y = loc.y + e.getY() - dragOffset.y;
                setLocation(x, y);
            }
        };

        titleBar.addMouseListener(dragAdapter);
        titleBar.addMouseMotionListener(dragAdapter);

        return titleBar;
    }

    private JButton createWindowControlButton(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.setToolTipText(tooltip);
        // do not set fonts/colors/borders — let LAF handle visual style
        b.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        return b;
    }

    private ImageIcon loadScaledIcon(URL url, int w, int h) {
        try {
            Image img = Toolkit.getDefaultToolkit().createImage(url);
            MediaTracker mt = new MediaTracker(new Container());
            mt.addImage(img, 0);
            mt.waitForAll();
            Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception ex) {
            logger.log(Level.FINE, "Failed to load icon: " + url, ex);
            return new ImageIcon();
        }
    }

    private void toggleMaximizeRestore() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle usable = new Rectangle(screen.x + insets.left, screen.y + insets.top,
                screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom);

        if (!maximized) {
            restoredBounds = getBounds();
            setBounds(usable);
            maximized = true;
            maximizeBtn.setText("❒");
        } else {
            if (restoredBounds != null) setBounds(restoredBounds);
            maximized = false;
            maximizeBtn.setText("□");
        }
    }

    private void restoreFromMaximizedAt(Point screenPoint) {
        Rectangle target = restoredBounds != null ? new Rectangle(restoredBounds) : new Rectangle(800, 600);
        int x = screenPoint.x - target.width / 2;
        int y = screenPoint.y - 10;
        setBounds(x, y, target.width, target.height);
        maximized = false;
        maximizeBtn.setText("\u25A1");
    }

    private JPanel createGraphicsPanel() {
        JPanel panel = createCardPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 6, 8, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;

        // Controls
        displayResCombo = createComboBox();
        colorDepthCombo = createComboBox();
        displayFreqCombo = createComboBox();
        antialiasCombo = createComboBox();

        vsyncBox = createCheckBox(resource("checkbox.vsync", "VSync"));
        gammaBox = createCheckBox(resource("checkbox.gamma", "Gamma"));
        fullscreenBox = createCheckBox(resource("checkbox.fullscreen", "Fullscreen"));

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
        audioModuleBox = createCheckBox("Audio Module");
        physicsModuleBox = createCheckBox("Physics Module");
        aiModuleBox = createCheckBox("AI Module");
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
        okButton = createPrimaryButton(resource("button.ok", "OK").toUpperCase());
        cancelButton = createSecondaryButton(resource("button.cancel", "Cancel").toUpperCase());
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
        footer.add(okButton);
        footer.add(cancelButton);
        return footer;
    }

    // ---------- Helpers (UI pieces) — minimal manual styling ----------

    private JPanel createCardPanel() {
        JPanel p = new JPanel();
        p.setOpaque(true);
        p.setBorder(new EmptyBorder(12, 12, 12, 12));
        return p;
    }

    private JSeparator createSeparator() {
        return new JSeparator(); // rely on LAF
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
        // use LAF defaults (do not override font/color)
        return l;
    }

    private JComboBox<String> createComboBox() {
        JComboBox<String> combo = new JComboBox<>();
        // use LAF defaults; avoid custom popup styling
        combo.setFocusable(true);
        return combo;
    }

    private JCheckBox createCheckBox(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setOpaque(false);
        cb.setFocusable(true);
        return cb;
    }

    private JButton createPrimaryButton(String text) {
        JButton b = new JButton(text);
        // minimal behavior only; visual styling left to LAF
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(true);
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        return b;
    }

    private JButton createSecondaryButton(String text) {
        JButton b = new JButton(text);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(true);
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        return b;
    }

    private void styleTabbedPane(JTabbedPane tabs) {
        // intentionally empty — let Look&Feel render tabs
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

    private void setupGlobalKeyBindings() {
        JRootPane rp = getRootPane();
        InputMap im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rp.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        am.put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setUserSelection(CANCEL_SELECTION);
                dispose();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "confirm");
        am.put("confirm", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (okButton != null) okButton.doClick();
            }
        });

        // Alt+M/X/C for window controls
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK), "minimize");
        am.put("minimize", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setState(Frame.ICONIFIED);
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK), "maximize");
        am.put("maximize", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleMaximizeRestore();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK), "close");
        am.put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setUserSelection(CANCEL_SELECTION);
                dispose();
            }
        });
    }

    private void addDraggableListener(JComponent comp) {
        // kept for backward compatibility; new titlebar handles dragging
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
        String selection = "Disabled";
        if (samples == 0) selection = choices[0];
        else if (samples >= 16) selection = "16x";
        else if (samples >= 8) selection = "8x";
        else if (samples >= 6) selection = "6x";
        else if (samples >= 4) selection = "4x";
        else selection = "2x";
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
        // Do not override LAF colors; use default JOptionPane
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
            getRootPane().setDefaultButton(okButton);
        });
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
