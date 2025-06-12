package com.jme3.awt;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.net.URL;

/**
 * AAA-style error dialog for the Calista Game Engine using FlatLaf.
 * Modern high-end engine UI with smooth visuals, rounded corners,
 * intuitive interactions with dedicated drag region,
 * and animated exclamation icon for visual flair.
 *
 * Supports dry-run mode for UI validation without displaying.
 */
public class AWTErrorDialog extends JDialog {

    private static final String DEFAULT_TITLE = "Calista Engine Error";
    private static final int WIDTH = 620;
    private static final int HEIGHT = 360;
    private static final int PADDING = 24;
    private static final int CORNER_RADIUS = 16;
    private static final int ICON_ANIMATION_DELAY = 500; // ms

    // Dry-run flag
    private static boolean dryRun = false;

    // Color scheme
    private static final Color ACCENT    = new Color(0, 120, 215);
    private static final Color BG_PANEL  = new Color(38, 38, 40);
    private static final Color SEPARATOR = new Color(72, 72, 74);

    // Fonts
    private static final Font FONT_HEADER = new Font("Segoe UI Semibold", Font.PLAIN, 18);
    private static final Font FONT_BODY   = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BUTTON = new Font("Segoe UI Semibold", Font.PLAIN, 14);

    // Drag support
    private Point dragOffset;

    // Animated exclamation icon label
    private JLabel alertLabel;
    private Timer iconTimer;
    private Icon[] alertIcons;
    private int iconIndex = 0;

    /**
     * Enable or disable dry-run mode. In dry-run, dialogs are built but not shown.
     */
    public static void setDryRun(boolean enable) {
        dryRun = enable;
    }

    /**
     * Show the dialog with default title (or simulate in dry-run).
     */
    public static void showDialog(String message) {
        SwingUtilities.invokeLater(() -> {
            AWTErrorDialog dialog = new AWTErrorDialog(message, DEFAULT_TITLE);
            if (!dryRun) {
                dialog.setVisible(true);
            } else {
                System.out.println("[DryRun] Built dialog with default title. Message: " + message);
                dialog.dispose();
            }
        });
    }

    /**
     * Show the dialog with custom title (or simulate in dry-run).
     */
    public static void showDialog(String message, String title) {
        SwingUtilities.invokeLater(() -> {
            AWTErrorDialog dialog = new AWTErrorDialog(message, title);
            if (!dryRun) {
                dialog.setVisible(true);
            } else {
                System.out.println("[DryRun] Built dialog with title: " + title + ". Message: " + message);
                dialog.dispose();
            }
        });
    }

    private AWTErrorDialog(String message, String title) {
        super((Frame) null);
        setTitle(title);
        FlatDarkLaf.setup();
        FlatAnimatedLafChange.showSnapshot();

        initDialog();
        buildUI(message);
        applyRoundedCorners();
        startIconAnimation();
    }

    private void initDialog() {
        setUndecorated(true);
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(480, 240));
        setModal(true);
        setBackground(new Color(0, 0, 0, 0));

        getRootPane().setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 2, true));
        getContentPane().setBackground(UIManager.getColor("Panel.background"));
    }

    private void buildUI(String message) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(PADDING, PADDING, PADDING, PADDING));
        root.setBackground(UIManager.getColor("Panel.background"));

        root.add(createTitleBar(), BorderLayout.NORTH);

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setBackground(SEPARATOR);
        sep.setPreferredSize(new Dimension(0, 1));
        root.add(sep, BorderLayout.CENTER);

        root.add(createMessagePanel(message), BorderLayout.CENTER);
        root.add(createActionPanel(), BorderLayout.SOUTH);

        enableRootDrag(root);
        setContentPane(root);
    }

    private JPanel createTitleBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(BG_PANEL);
        bar.setBorder(new EmptyBorder(8, 16, 8, 16));

        JLabel grip = new JLabel("\u2630");
        grip.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        grip.setForeground(UIManager.getColor("Label.foreground"));
        grip.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        bar.add(grip, BorderLayout.WEST);

        // Animated alert icon label
        alertIcons = new Icon[] {
                loadIcon("Interface/Icons/error_icon_highlight.png", 24),
                loadIcon("Interface/Icons/bug.png", 32)
        };
        alertLabel = new JLabel(alertIcons[0]);
        alertLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        alertLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { dispose(); }
        });
        bar.add(alertLabel, BorderLayout.EAST);

        JLabel lbl = new JLabel(getTitle());
        lbl.setFont(FONT_HEADER);
        lbl.setForeground(UIManager.getColor("Label.foreground"));
        bar.add(lbl, BorderLayout.CENTER);

        MouseAdapter dragListener = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragOffset = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - dragOffset.x, loc.y + e.getY() - dragOffset.y);
            }
        };
        grip.addMouseListener(dragListener);
        grip.addMouseMotionListener(dragListener);
        return bar;
    }

    private void startIconAnimation() {
        iconTimer = new Timer(ICON_ANIMATION_DELAY, e -> {
            iconIndex = (iconIndex + 1) % alertIcons.length;
            alertLabel.setIcon(alertIcons[iconIndex]);
        });
        iconTimer.setRepeats(true);
        iconTimer.start();
    }

    private void enableRootDrag(JPanel root) {
        MouseAdapter adapter = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragOffset = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - dragOffset.x, loc.y + e.getY() - dragOffset.y);
            }
        };
        root.addMouseListener(adapter);
        root.addMouseMotionListener(adapter);
    }

    private void applyRoundedCorners() {
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
            }
        });
    }

    private JScrollPane createMessagePanel(String msg) {
        JTextArea area = new JTextArea(msg);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(FONT_BODY);
        area.setBackground(UIManager.getColor("TextArea.background"));
        area.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        area.setComponentPopupMenu(createContextMenu(area));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel createActionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        panel.setBackground(UIManager.getColor("Panel.background"));

        JButton copy = new JButton("Copy");
        styleButton(copy);
        copy.addActionListener(e -> copyToClipboard());

        JButton ok = new JButton("OK");
        styleButton(ok);
        ok.addActionListener(e -> dispose());

        panel.add(copy);
        panel.add(ok);
        return panel;
    }

    private void styleButton(JButton btn) {
        btn.setFont(FONT_BUTTON);
        btn.setBackground(ACCENT.brighter());
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 20, 8, 20));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(ACCENT.darker()); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(ACCENT.brighter()); }
        });
    }

    private Icon loadIcon(String path, int size) {
        URL url = getClass().getClassLoader().getResource(path);
        if (url != null) {
            try {
                Image img = ImageIO.read(url).getScaledInstance(size, size, Image.SCALE_SMOOTH);
                return new ImageIcon(img);
            } catch (IOException ignored) {}
        }
        return UIManager.getIcon("OptionPane.errorIcon");
    }

    private JPopupMenu createContextMenu(JTextArea area) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copy = new JMenuItem("Copy");
        copy.addActionListener(e -> {
            String text = area.getSelectedText();
            if (text == null) text = area.getText();
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        });
        menu.add(copy);
        return menu;
    }

    private void copyToClipboard() {
        JTextArea tmp = new JTextArea();
        tmp.setText(((JTextArea) ((JScrollPane) getContentPane().getComponent(2)).getViewport().getView()).getText());
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(tmp.getText()), null);
    }

    /**
     * Main method for testing AWTErrorDialog with dry-run and normal modes.
     * Usage:
     *   java com.jme3.awt.AWTErrorDialog           (shows dialog)
     *   java com.jme3.awt.AWTErrorDialog --dryrun  (simulates build only)
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("--dryrun")) {
            setDryRun(true);
            System.out.println("Dry-run mode enabled.");
        }
        showDialog("This is a test error message. Dry-run = " + dryRun);
    }
}