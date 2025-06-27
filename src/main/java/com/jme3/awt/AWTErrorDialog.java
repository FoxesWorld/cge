package com.jme3.awt;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.icons.FlatWindowCloseIcon;
import org.foxesworld.cge.ICOParser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;

import static org.foxesworld.cge.tools.SceneCGSCreator.SceneCgsCreatorFrame.setupTheme;

/**
 * AAA-style error dialog for the Calista Game Engine using FlatLaf.
 * Features:
 * - Full-screen dark overlay
 * - Smooth fade-in and fade-out transitions
 * - Draggable, rounded-corner dialog
 * - Prominent title bar with engine logo
 * - Stack trace view with copy & report buttons
 * - Keyboard shortcuts: Ctrl+C copy, Esc close
 * - Modern, glassy, material-inspired design with drop shadow and accent highlights
 */
public class AWTErrorDialog extends JDialog {
    private static final int CORNER_RADIUS = 18;
    private static final Color OVERLAY = new Color(0, 0, 0, 200);
    private static final Color BG_PANEL = new Color(36, 37, 41, 230);
    private static final Color ACCENT = new Color(255, 92, 92);
    private static final Color ACCENT_GRAD = new Color(255, 140, 115);
    private static final Color BORDER_COLOR = new Color(80, 80, 90, 120);

    private float opacity = 0f;
    private Timer fadeIn;
    private Timer fadeOut;

    private JTextArea stackArea;
    private JButton copyBtn;
    private Point dragOffset;

    public AWTErrorDialog(String title, String message) {
        super((Frame) null, true);
        setupTheme("assets/theme/calista.properties");;
        FlatAnimatedLafChange.showSnapshot();

        initDialog();
        buildUI(title, message);
        applyDrag();
        startFade();
    }

    private void initDialog() {
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(1000, 600);
        setLocationRelativeTo(null);
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Drop shadow
        getRootPane().setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        getRootPane().setOpaque(false);
    }

    private void buildUI(String title, String message) {
        JPanel overlay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setComposite(AlphaComposite.SrcOver.derive(opacity));
                g2.setColor(OVERLAY);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        overlay.setLayout(null);
        overlay.setOpaque(false);
        add(overlay, BorderLayout.CENTER);

        JPanel panel = buildPanel(title, message);
        overlay.add(panel);

        setupShortcuts(panel);
    }

    private JPanel buildPanel(String title, String message) {
        JPanel glassPanel = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Glassy background
                g2.setComposite(AlphaComposite.SrcOver.derive(0.98f));
                g2.setPaint(new GradientPaint(0, 0, BG_PANEL, getWidth(), getHeight(), BG_PANEL.darker()));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER_RADIUS * 2, CORNER_RADIUS * 2);

                // Border
                g2.setColor(BORDER_COLOR);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, CORNER_RADIUS * 2, CORNER_RADIUS * 2);

                g2.dispose();
            }
        };
        glassPanel.setBackground(new Color(0,0,0,0));
        glassPanel.setBorder(new EmptyBorder(32, 32, 32, 32));
        glassPanel.setBounds(140, 80, 720, 440);
        glassPanel.setOpaque(false);

        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout(12, 0));
        titleBar.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI Semibold", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);

        JLabel iconLabel = iconLabel();
        if (iconLabel != null) titleBar.add(iconLabel, BorderLayout.WEST);

        titleBar.add(titleLabel, BorderLayout.CENTER);

        JButton closeBtn = new JButton(new FlatWindowCloseIcon());
        closeBtn.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        closeBtn.setToolTipText("Close (Esc)");
        closeBtn.setFocusable(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> fadeOut.start());
        titleBar.add(closeBtn, BorderLayout.EAST);

        glassPanel.add(titleBar, BorderLayout.NORTH);

        // Message and stacktrace area
        JTextArea msgArea = new JTextArea(message);
        msgArea.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        msgArea.setForeground(Color.WHITE);
        msgArea.setOpaque(false);
        msgArea.setEditable(false);
        msgArea.setLineWrap(true);
        msgArea.setWrapStyleWord(true);
        msgArea.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14)); // <-- ОТСТУПЫ

        stackArea = new JTextArea(message);
        stackArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        stackArea.setForeground(new Color(220, 230, 245));
        stackArea.setBackground(new Color(18, 20, 25, 180));
        stackArea.setCaretPosition(0);
        stackArea.setEditable(false);
        stackArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60,70,80,120), 1, true),
                BorderFactory.createEmptyBorder(8, 14, 8, 14) // <-- ОТСТУПЫ
        ));

        JScrollPane msgScroll = new JScrollPane(msgArea);
        msgScroll.setBorder(null);
        msgScroll.setOpaque(false);
        msgScroll.getViewport().setOpaque(false);

        JScrollPane stackScroll = new JScrollPane(stackArea);
        stackScroll.setBorder(null);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, msgScroll, stackScroll);
        split.setResizeWeight(0.25);
        split.setDividerSize(6);
        split.setBorder(null);
        glassPanel.add(split, BorderLayout.CENTER);

        // Actions row
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 14));
        actions.setOpaque(false);
        copyBtn = styledButton("Copy", e -> copyAll());
        JButton reportBtn = styledButton("Report", e -> openReport());
        reportBtn.setForeground(ACCENT);
        reportBtn.setEnabled(false);
        reportBtn.setBorder(BorderFactory.createLineBorder(ACCENT, 2, true));
        JButton closeBtn2 = styledButton("Close", e -> fadeOut.start());
        actions.add(copyBtn);
        actions.add(reportBtn);
        actions.add(closeBtn2);
        glassPanel.add(actions, BorderLayout.SOUTH);

        // Subtle glass shadow (optional, for extra modern look)
        glassPanel.setBorder(BorderFactory.createCompoundBorder(
                glassPanel.getBorder(),
                BorderFactory.createMatteBorder(0,0,12,0,new Color(0,0,0,24))
        ));

        return glassPanel;
    }

    private JLabel iconLabel() {
        try {
            var parser = new ICOParser();
            var icons = parser.parse(Objects.requireNonNull(AWTErrorDialog.class.getClassLoader()
                    .getResourceAsStream("assets/theme/icon/engineLogo.ico")));
            if (icons != null && icons.size() > 2) {
                Image scaled = icons.get(2).getScaledInstance(38, 38, Image.SCALE_SMOOTH);
                JLabel iconLabel = new JLabel(new ImageIcon(scaled));
                iconLabel.setBorder(new EmptyBorder(0, 0, 0, 8));
                return iconLabel;
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Icon load failed: " + e.getMessage());
        }
        return null;
    }

    private JButton styledButton(String text, ActionListener action) {
        var b = new JButton(text);
        b.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBackground(new Color(40,40,45,180));
        b.setBorder(BorderFactory.createEmptyBorder(9, 20, 9, 20));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(action);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(true);
        return b;
    }

    private void applyDrag() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                var loc = getLocation();
                setLocation(loc.x + e.getX() - dragOffset.x, loc.y + e.getY() - dragOffset.y);
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    private void setupShortcuts(JComponent panel) {
        var im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var am = panel.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        am.put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { fadeOut.start(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        am.put("copy", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { copyAll(); }
        });
    }

    private void startFade() {
        fadeIn = new Timer(15, e -> {
            opacity = Math.min(1f, opacity + 0.06f);
            repaint();
            if (opacity >= 1f) fadeIn.stop();
        });
        fadeOut = new Timer(15, e -> {
            opacity = Math.max(0f, opacity - 0.06f);
            repaint();
            if (opacity <= 0f) { fadeOut.stop(); dispose(); }
        });
        setVisible(true);
        fadeIn.start();
    }

    private void copyAll() {
        var text = stackArea.getText();
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);

        String origText = copyBtn.getText();
        copyBtn.setText("✔");
        Timer chk = new Timer(1100, e -> copyBtn.setText(origText));
        chk.setRepeats(false);
        chk.start();
    }

    private void openReport() {
        try {
            Desktop.getDesktop().browse(new URI("https://tracker.calista/gameerror"));
        } catch (Exception ignored) {}
    }

    public static void showDialog(String msg) {
        SwingUtilities.invokeLater(() -> new AWTErrorDialog("Calista Engine Error", msg));
    }

    public static void main(String[] args) {
        boolean dry = args.length > 0 && "--dryrun".equalsIgnoreCase(args[0]);
        if (dry) System.out.println("[DryRun] Building dialog (no show)");
        SwingUtilities.invokeLater(() -> {
            AWTErrorDialog dlg = new AWTErrorDialog(
                    dry ? "DryRun Error" : "Calista Engine Error",
                    dry ? "UI build only" : "Test exception\n\nStackTrace here\nat com.test.Main(x:42)"
            );
            if (dry) dlg.dispose();
        });
    }
}