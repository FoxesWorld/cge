package com.jme3.awt;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeSystem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;

public final class AWTSettingsDialog extends JFrame {
    private static final Logger logger = Logger.getLogger(AWTSettingsDialog.class.getName());
    private static final long serialVersionUID = 1L;
    public static final int NO_SELECTION = 0;
    public static final int APPROVE_SELECTION = 1;
    public static final int CANCEL_SELECTION = 2;

    private ResourceBundle resourceBundle;
    private final AppSettings source;
    private URL imageFile;
    private DisplayMode[] modes;
    private static final DisplayMode[] windowDefaults = {
            new DisplayMode(1024, 768, 24, 60),
            new DisplayMode(1280, 720, 24, 60),
            new DisplayMode(1280, 1024, 24, 60),
            new DisplayMode(1440, 900, 24, 60),
            new DisplayMode(1680, 1050, 24, 60)
    };
    private DisplayMode[] windowModes;

    // Graphics controls
    private JCheckBox vsyncBox;
    private JCheckBox gammaBox;
    private JCheckBox fullscreenBox;
    private JComboBox<String> displayResCombo;
    private JComboBox<String> colorDepthCombo;
    private JComboBox<String> displayFreqCombo;
    private JComboBox<String> antialiasCombo;

    // Modules controls (example)
    private JCheckBox audioModuleBox;
    private JCheckBox physicsModuleBox;
    private JCheckBox aiModuleBox;

    private JLabel icon;
    private int selection;
    private SelectionListener selectionListener;
    private int minWidth;
    private int minHeight;

    static {
        
    }

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
                    } catch (InterruptedException ignored) {}
                }
            }
            sourceSettings.copyFrom(settings);
            return result.get() == 1;
        }
    }

    protected AWTSettingsDialog(AppSettings source, String imageFile, boolean loadSettings) {
        this(source, getURL(imageFile), loadSettings);
    }

    protected AWTSettingsDialog(AppSettings source, URL imageFile, boolean loadSettings) {
        this.resourceBundle = ResourceBundle.getBundle("com.jme3.app/SettingsDialog");
        this.source = Objects.requireNonNull(source, "Settings source cannot be null");
        this.imageFile = imageFile;
        this.setAlwaysOnTop(true);
        this.setResizable(false);

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

        // Merge window defaults
        DisplayMode[] merged = new DisplayMode[this.modes.length + windowDefaults.length];
        int wdIndex = 0, dmIndex = 0, mergedIndex;
        for (mergedIndex = 0; mergedIndex < merged.length && (wdIndex < windowDefaults.length || dmIndex < this.modes.length); ++mergedIndex) {
            if (dmIndex >= this.modes.length) merged[mergedIndex] = windowDefaults[wdIndex++];
            else if (wdIndex >= windowDefaults.length) merged[mergedIndex] = this.modes[dmIndex++];
            else if (this.modes[dmIndex].getWidth() < windowDefaults[wdIndex].getWidth()) merged[mergedIndex] = this.modes[dmIndex++];
            else if (this.modes[dmIndex].getWidth() == windowDefaults[wdIndex].getWidth()) {
                if (this.modes[dmIndex].getHeight() < windowDefaults[wdIndex].getHeight()) merged[mergedIndex] = this.modes[dmIndex++];
                else if (this.modes[dmIndex].getHeight() == windowDefaults[wdIndex].getHeight()) {
                    merged[mergedIndex] = this.modes[dmIndex++]; ++wdIndex;
                } else merged[mergedIndex] = windowDefaults[wdIndex++];
            } else merged[mergedIndex] = windowDefaults[wdIndex++];
        }
        this.windowModes = merged.length == mergedIndex
                ? merged
                : Arrays.copyOfRange(merged, 0, mergedIndex);

        this.createUI();
    }

    public void setSelectionListener(SelectionListener sl) {
        this.selectionListener = sl;
    }

    public int getUserSelection() {
        return this.selection;
    }

    private void setUserSelection(int selection) {
        this.selection = selection;
        if (this.selectionListener != null)
            this.selectionListener.onSelection(selection);
    }

    public int getMinWidth() { return this.minWidth; }
    public void setMinWidth(int minWidth) { this.minWidth = minWidth; }
    public int getMinHeight() { return this.minHeight; }
    public void setMinHeight(int minHeight) { this.minHeight = minHeight; }

    public void setImage(String image) {
        try {
            URL file = new URL("file:" + image);
            setImage(file);
        } catch (MalformedURLException e) {
            logger.log(Level.WARNING, "Couldn’t read from file '" + image + "'", e);
        }
    }

    public void setImage(URL image) {
        this.icon.setIcon(new ImageIcon(image));
        this.pack();
        this.setLocationRelativeTo(null);
    }

    public void showDialog() {
        this.setLocationRelativeTo(null);
        this.setVisible(true);
        this.toFront();
    }

    /**
     * Использует BorderLayout для AAA-структуры, две вкладки: Graphics и Modules.
     */
    private void createUI() {
        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // --- Логотип и заголовок ---
        JPanel logoPanel = new JPanel();
        logoPanel.setOpaque(false);
        logoPanel.setLayout(new BoxLayout(logoPanel, BoxLayout.Y_AXIS));
        icon = new JLabel(imageFile != null ? new ImageIcon(imageFile) : null);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        icon.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        JLabel title = new JLabel(this.source.getTitle() != null ? this.source.getTitle() : "Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        logoPanel.add(icon);
        logoPanel.add(title);
        rootPanel.add(logoPanel, BorderLayout.NORTH);

        // --- TabbedPane ---
        JTabbedPane tabbedPane = new JTabbedPane();

        // === Graphics Tab ===
        JPanel graphicsPanel = new JPanel();
        graphicsPanel.setLayout(new BorderLayout(10,10));
        graphicsPanel.setOpaque(false);

        JPanel graphicsMain = new JPanel();
        graphicsMain.setOpaque(false);
        graphicsMain.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        gbc.gridx = 0; gbc.gridy = row;
        graphicsMain.add(new JLabel(resourceBundle.getString("label.resolutions")), gbc);
        displayResCombo = setUpResolutionChooser();
        gbc.gridx = 1;
        graphicsMain.add(displayResCombo, gbc);

        gbc.gridx = 2;
        graphicsMain.add(new JLabel(resourceBundle.getString("label.colordepth")), gbc);
        colorDepthCombo = new JComboBox<>();
        gbc.gridx = 3;
        graphicsMain.add(colorDepthCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        graphicsMain.add(new JLabel(resourceBundle.getString("label.refresh")), gbc);
        displayFreqCombo = new JComboBox<>();
        gbc.gridx = 1;
        graphicsMain.add(displayFreqCombo, gbc);

        gbc.gridx = 2;
        graphicsMain.add(new JLabel(resourceBundle.getString("label.antialias")), gbc);
        antialiasCombo = new JComboBox<>();
        gbc.gridx = 3;
        graphicsMain.add(antialiasCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        fullscreenBox = new JCheckBox(resourceBundle.getString("checkbox.fullscreen"));
        graphicsMain.add(fullscreenBox, gbc);
        gbc.gridx = 1;
        vsyncBox = new JCheckBox(resourceBundle.getString("checkbox.vsync"));
        graphicsMain.add(vsyncBox, gbc);
        gbc.gridx = 2;
        gammaBox = new JCheckBox(resourceBundle.getString("checkbox.gamma"));
        graphicsMain.add(gammaBox, gbc);

        graphicsPanel.add(graphicsMain, BorderLayout.CENTER);
        tabbedPane.addTab("Graphics", graphicsPanel);

        // === Modules Tab ===
        JPanel modulesPanel = new JPanel();
        modulesPanel.setLayout(new BorderLayout(10, 10));
        modulesPanel.setOpaque(false);

        JPanel modulesMain = new JPanel();
        modulesMain.setOpaque(false);
        modulesMain.setLayout(new BoxLayout(modulesMain, BoxLayout.Y_AXIS));
        modulesMain.setBorder(BorderFactory.createEmptyBorder(16, 32, 16, 32));

        audioModuleBox = new JCheckBox("Audio Module");
        audioModuleBox.setSelected(true);
        physicsModuleBox = new JCheckBox("Physics Module");
        physicsModuleBox.setSelected(true);
        aiModuleBox = new JCheckBox("AI Module");
        aiModuleBox.setSelected(true);

        for (JCheckBox box : Arrays.asList(audioModuleBox, physicsModuleBox, aiModuleBox)) {
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 16f));
            modulesMain.add(box);
            modulesMain.add(Box.createRigidArea(new Dimension(0, 14)));
        }

        modulesPanel.add(modulesMain, BorderLayout.NORTH);
        tabbedPane.addTab("Modules", modulesPanel);

        rootPanel.add(tabbedPane, BorderLayout.CENTER);

        // --- Кнопки ---
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setOpaque(false);
        JPanel buttonBox = new JPanel();
        buttonBox.setOpaque(false);
        buttonBox.setLayout(new BoxLayout(buttonBox, BoxLayout.X_AXIS));
        JButton ok = new JButton(resourceBundle.getString("button.ok"));
        JButton cancel = new JButton(resourceBundle.getString("button.cancel"));
        ok.setPreferredSize(new Dimension(120, 36));
        cancel.setPreferredSize(new Dimension(120, 36));
        ok.setFont(ok.getFont().deriveFont(Font.BOLD, 16f));
        cancel.setFont(cancel.getFont().deriveFont(Font.BOLD, 16f));
        ok.setFocusable(false);
        cancel.setFocusable(false);
        buttonBox.add(Box.createHorizontalGlue());
        buttonBox.add(ok);
        buttonBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonBox.add(cancel);
        buttonBox.add(Box.createHorizontalGlue());
        buttonPanel.add(buttonBox, BorderLayout.CENTER);
        rootPanel.add(buttonPanel, BorderLayout.SOUTH);

        // --- FlatLaf/AAA стилизация ---
        Font modernFont = UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 16f);
        for (JComponent comp : Arrays.asList(fullscreenBox, vsyncBox, gammaBox,
                displayResCombo, colorDepthCombo, displayFreqCombo, antialiasCombo,
                ok, cancel, icon, title, audioModuleBox, physicsModuleBox, aiModuleBox)) {
            if (comp != null) comp.setFont(modernFont);
        }
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        fullscreenBox.setIconTextGap(8);
        vsyncBox.setIconTextGap(8);
        gammaBox.setIconTextGap(8);

        KeyListener aListener = new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (verifyAndSaveCurrentSelection()) {
                        setUserSelection(APPROVE_SELECTION);
                        dispose();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    setUserSelection(CANCEL_SELECTION);
                    dispose();
                }
            }
        };

        for (JComponent comp : Arrays.asList(displayResCombo, colorDepthCombo, displayFreqCombo, antialiasCombo)) {
            comp.addKeyListener(aListener);
        }

        fullscreenBox.setSelected(source.isFullscreen());
        fullscreenBox.addActionListener(e -> updateResolutionChoices());
        vsyncBox.setSelected(source.isVSync());
        gammaBox.setSelected(source.isGammaCorrection());

        ok.addActionListener(e -> {
            if (verifyAndSaveCurrentSelection()) {
                setUserSelection(APPROVE_SELECTION);
                dispose();
                System.gc();
            }
        });
        cancel.addActionListener(e -> {
            setUserSelection(CANCEL_SELECTION);
            dispose();
        });

        this.getContentPane().add(rootPanel);
        this.pack();
        rootPanel.getRootPane().setDefaultButton(ok);

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

        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                setUserSelection(CANCEL_SELECTION);
                dispose();
            }
        });
        if (this.source.getIcons() != null) {
            this.safeSetIconImages(Arrays.asList((BufferedImage[]) this.source.getIcons()));
        }
        this.setTitle(MessageFormat.format(this.resourceBundle.getString("frame.title"), this.source.getTitle()));
    }

    private void safeSetIconImages(List<? extends Image> icons) {
        try {
            Window owner = this.getOwner();
            if (owner != null) {
                Method setIconImages = owner.getClass().getMethod("setIconImages", List.class);
                setIconImages.invoke(owner, icons);
                return;
            }
            Method setIconImages = this.getClass().getMethod("setIconImages", List.class);
            setIconImages.invoke(this, icons);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error setting icon images", e);
        }
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
        int freq = -1;
        if (fullscreen) {
            freq = freqString.equals("???") ? 0 : Integer.parseInt(freqString.substring(0, freqString.indexOf(' ')));
        }

        String aaString = (String) antialiasCombo.getSelectedItem();
        int multisample = aaString.equals(resourceBundle.getString("antialias.disabled")) ? 0 : Integer.parseInt(aaString.substring(0, aaString.indexOf('x')));

        // Example: you can handle saving module settings here too
        boolean audioEnabled = audioModuleBox.isSelected();
        boolean physicsEnabled = physicsModuleBox.isSelected();
        boolean aiEnabled = aiModuleBox.isSelected();
        // TODO: Save modules enabled/disabled state to config if needed

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
            String appTitle = source.getTitle();

            try {
                source.save(appTitle);
            } catch (BackingStoreException ex) {
                logger.log(Level.WARNING, "Failed to save setting changes", ex);
            }
        } else {
            showError(this, resourceBundle.getString("error.unsupportedmode"));
        }
        return valid;
    }

    private JComboBox<String> setUpResolutionChooser() {
        JComboBox<String> resolutionBox = new JComboBox<>();
        resolutionBox.addActionListener(e -> updateDisplayChoices());
        return resolutionBox;
    }

    private void updateDisplayChoices() {
        if (fullscreenBox.isSelected()) {
            String resolution = (String) displayResCombo.getSelectedItem();
            String colorDepth = (String) colorDepthCombo.getSelectedItem();
            if (colorDepth == null) colorDepth = source.getBitsPerPixel() + " bpp";
            String displayFreq = (String) displayFreqCombo.getSelectedItem();
            if (displayFreq == null) displayFreq = source.getFrequency() + " Hz";
            String[] depths = getDepths(resolution, modes);
            colorDepthCombo.setModel(new DefaultComboBoxModel<>(depths));
            colorDepthCombo.setSelectedItem(colorDepth);
            String[] freqs = getFrequencies(resolution, modes);
            displayFreqCombo.setModel(new DefaultComboBoxModel<>(freqs));
            displayFreqCombo.setSelectedItem(displayFreq);
            if (!displayFreqCombo.getSelectedItem().equals(displayFreq)) {
                displayFreqCombo.setSelectedItem(getBestFrequency(resolution, modes));
            }
        }
    }

    private void updateResolutionChoices() {
        if (!fullscreenBox.isSelected()) {
            displayResCombo.setModel(new DefaultComboBoxModel<>(getWindowedResolutions(windowModes)));
            if (displayResCombo.getItemCount() > 0) {
                displayResCombo.setSelectedIndex(displayResCombo.getItemCount() - 1);
            }
            colorDepthCombo.setModel(new DefaultComboBoxModel<>(new String[]{"24 bpp", "16 bpp"}));
            displayFreqCombo.setModel(new DefaultComboBoxModel<>(new String[]{resourceBundle.getString("refresh.na")}));
            displayFreqCombo.setEnabled(false);
        } else {
            displayResCombo.setModel(new DefaultComboBoxModel<>(getResolutions(modes, Integer.MAX_VALUE, Integer.MAX_VALUE)));
            if (displayResCombo.getItemCount() > 0) {
                displayResCombo.setSelectedIndex(displayResCombo.getItemCount() - 1);
            }
            displayFreqCombo.setEnabled(true);
            updateDisplayChoices();
        }
    }

    private void updateAntialiasChoices() {
        String[] choices = {resourceBundle.getString("antialias.disabled"), "2x", "4x", "6x", "8x", "16x"};
        antialiasCombo.setModel(new DefaultComboBoxModel<>(choices));
        int idx = source.getSamples() <= 0 ? 0 : Math.min(source.getSamples() / 2, choices.length - 1);
        antialiasCombo.setSelectedIndex(idx);
    }

    private static URL getURL(String file) {
        try {
            return new URL("file:" + file);
        } catch (MalformedURLException e) {
            logger.log(Level.WARNING, "Invalid file name '" + file + "'", e);
            return null;
        }
    }

    private static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private String[] getResolutions(DisplayMode[] modes, int heightLimit, int widthLimit) {
        Insets insets = getInsets();
        heightLimit -= insets.top + insets.bottom;
        widthLimit -= insets.left + insets.right;
        Set<String> resolutions = new LinkedHashSet<>(modes.length);

        for (DisplayMode mode : modes) {
            int height = mode.getHeight();
            int width = mode.getWidth();
            if (width >= minWidth && height >= minHeight) {
                if (height >= heightLimit) height = heightLimit;
                if (width >= widthLimit) width = widthLimit;
                String res = width + " x " + height;
                resolutions.add(res);
            }
        }
        return resolutions.toArray(new String[0]);
    }

    private String[] getWindowedResolutions(DisplayMode[] modes) {
        int maxHeight = 0, maxWidth = 0;
        for (DisplayMode mode : modes) {
            if (maxHeight < mode.getHeight()) maxHeight = mode.getHeight();
            if (maxWidth < mode.getWidth()) maxWidth = mode.getWidth();
        }
        return getResolutions(modes, maxHeight, maxWidth);
    }

    private static String[] getDepths(String resolution, DisplayMode[] modes) {
        List<String> depths = new ArrayList<>(4);
        for (DisplayMode mode : modes) {
            int bitDepth = mode.getBitDepth();
            if (bitDepth != -1 && (bitDepth >= 16 || bitDepth <= 0)) {
                String res = mode.getWidth() + " x " + mode.getHeight();
                if (res.equals(resolution)) {
                    String depth = bitDepth + " bpp";
                    if (!depths.contains(depth)) {
                        depths.add(depth);
                    }
                }
            }
        }
        if (depths.isEmpty()) depths.add("24 bpp");
        return depths.toArray(new String[0]);
    }

    private static String[] getFrequencies(String resolution, DisplayMode[] modes) {
        List<String> freqs = new ArrayList<>(4);
        for (DisplayMode mode : modes) {
            String res = mode.getWidth() + " x " + mode.getHeight();
            String freq = mode.getRefreshRate() == 0 ? "???" : mode.getRefreshRate() + " Hz";
            if (res.equals(resolution) && !freqs.contains(freq)) {
                freqs.add(freq);
            }
        }
        return freqs.toArray(new String[0]);
    }

    private static String getBestFrequency(String resolution, DisplayMode[] modes) {
        int closest = Integer.MAX_VALUE;
        int desired = 60;
        for (DisplayMode mode : modes) {
            String res = mode.getWidth() + " x " + mode.getHeight();
            int freq = mode.getRefreshRate();
            if (freq != 0 && res.equals(resolution) && Math.abs(freq - desired) < Math.abs(closest - desired)) {
                closest = mode.getRefreshRate();
            }
        }
        return closest != Integer.MAX_VALUE ? closest + " Hz" : null;
    }

    private static class DisplayModeSorter implements Comparator<DisplayMode> {
        public int compare(DisplayMode a, DisplayMode b) {
            if (a.getWidth() != b.getWidth()) return Integer.compare(a.getWidth(), b.getWidth());
            if (a.getHeight() != b.getHeight()) return Integer.compare(a.getHeight(), b.getHeight());
            if (a.getBitDepth() != b.getBitDepth()) return Integer.compare(a.getBitDepth(), b.getBitDepth());
            if (a.getRefreshRate() != b.getRefreshRate()) return Integer.compare(a.getRefreshRate(), b.getRefreshRate());
            return 0;
        }
    }

    public interface SelectionListener {
        void onSelection(int selection);
    }
}