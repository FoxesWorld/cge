package com.jme3.awt;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import org.foxesworld.cge.ICOParser;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

/**
 * AAA-style error dialog for the Calista Game Engine using FlatLaf.
 * Features:
 * - Full-screen dark overlay
 * - Smooth fade-in and fade-out transitions
 * - Draggable, rounded-corner dialog
 * - Prominent title bar with engine logo
 * - Stack trace view with copy & report buttons
 * - Keyboard shortcuts: Ctrl+C copy, Esc close
 */
public class AWTErrorDialog extends JDialog {
    private static final int CORNER_RADIUS = 16;
    private static final Color OVERLAY = new Color(0, 0, 0, 200);
    private static final Color BG_PANEL = new Color(45, 45, 48);
    private static final Color ACCENT = new Color(220, 80, 80);

    private Timer fadeIn;
    private Timer fadeOut;
    private float opacity = 0f;

    private JLabel iconLabel;
    private JTextArea stackArea;
    private JButton copyBtn;
    private Point dragOffset;

    public AWTErrorDialog(String title, String message, Throwable t) {
        super((Frame) null, true);
        FlatDarkLaf.setup();
        FlatAnimatedLafChange.showSnapshot();

        initDialog();
        buildUI(title, message, t);
        applyDrag();
        startFade();
    }

    private void initDialog() {
        setUndecorated(true);
        setBackground(new Color(0,0,0,0));
        setSize(1000, 600);  // widened dialog
        setLocationRelativeTo(null);
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
        getContentPane().setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void buildUI(String title, String message, Throwable t) {
        JPanel overlay = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.SrcOver.derive(opacity));
                //g2.setColor(OVERLAY);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        overlay.setLayout(null);
        add(overlay, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(16, 16));
        //panel.setBackground(BG_PANEL);
        panel.setBorder(new EmptyBorder(24, 24, 24, 24));
        panel.setBounds(150, 100, 700, 400);  // adjust bounds for new width
        overlay.add(panel);

        JPanel titleBar = new JPanel(new BorderLayout(8,0));
        titleBar.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI Semibold", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        ICOParser parser = new ICOParser();
        java.util.List<BufferedImage> iconsList = null;
        try {
            iconsList = parser.parse(AWTErrorDialog.class.getClassLoader().getResourceAsStream("assets/theme/icon/engineLogo.ico"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        iconLabel = new JLabel(new ImageIcon(iconsList.get(2)));
        titleBar.add(iconLabel, BorderLayout.WEST);
        titleBar.add(titleLabel, BorderLayout.CENTER);
        panel.add(titleBar, BorderLayout.NORTH);

        JTextArea msgArea = new JTextArea(message);
        msgArea.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        msgArea.setForeground(Color.WHITE);
        msgArea.setOpaque(false);
        msgArea.setEditable(false);
        msgArea.setLineWrap(true);
        msgArea.setWrapStyleWord(true);

        stackArea = new JTextArea();
        stackArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        stackArea.setForeground(Color.LIGHT_GRAY);
        stackArea.setEditable(false);
        stackArea.setText(getStackTrace(t));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(msgArea), new JScrollPane(stackArea));
        split.setResizeWeight(0.3);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT,12,12));
        actions.setOpaque(false);
        copyBtn = styledButton("Copy"); copyBtn.addActionListener(e -> copyAll());
        JButton reportBtn = styledButton("Report"); reportBtn.addActionListener(e -> openReport());
        reportBtn.setEnabled(false);
        JButton closeBtn = styledButton("Close"); closeBtn.addActionListener(e -> fadeOut.start());
        actions.add(copyBtn); actions.add(reportBtn); actions.add(closeBtn);
        panel.add(actions, BorderLayout.SOUTH);

        InputMap im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = panel.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        am.put("close", new AbstractAction(){ public void actionPerformed(ActionEvent e){ fadeOut.start(); }});
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        am.put("copy", new AbstractAction(){ public void actionPerformed(ActionEvent e){ copyAll(); }});
    }

    private void applyDrag() {
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragOffset = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                Point loc = getLocation();
                setLocation(loc.x + e.getX() - dragOffset.x, loc.y + e.getY() - dragOffset.y);
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    private void startFade() {
        fadeIn = new Timer(15, e -> {
            opacity = Math.min(1f, opacity + 0.05f);
            repaint();
            if (opacity >= 1f) fadeIn.stop();
        });
        fadeOut = new Timer(15, e -> {
            opacity = Math.max(0f, opacity - 0.05f);
            repaint();
            if (opacity <= 0f) { fadeOut.stop(); dispose(); }
        });
        setVisible(true);
        fadeIn.start();
    }

    private JButton styledButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
        b.setForeground(Color.WHITE);
        //b.setBackground(ACCENT);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(8,16,8,16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
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

    private String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement el : t.getStackTrace()) sb.append(el).append("\n");
        return sb.toString();
    }

    private void copyAll() {
        String text = stackArea.getText();
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        // Flash the field
        Color orig = stackArea.getBackground();
        //stackArea.setBackground(ACCENT.brighter().brighter());
        Timer flash = new Timer(300, e -> stackArea.setBackground(orig));
        flash.setRepeats(false);
        flash.start();
        // Show checkmark on copy button
        String origText = copyBtn.getText();
        copyBtn.setText("✔");
        Timer chk = new Timer(1000, e -> copyBtn.setText(origText));
        chk.setRepeats(false);
        chk.start();
    }

    private void openReport() {
        try { Desktop.getDesktop().browse(new java.net.URI("https://tracker.calista/gameerror")); }
        catch (Exception ignored) {}
    }

    public static void showError(String msg, Throwable t) {
        SwingUtilities.invokeLater(() -> new AWTErrorDialog("Calista Engine Error", msg, t));
    }

    public static void main(String[] args) {
        boolean dry = args.length>0 && "--dryrun".equalsIgnoreCase(args[0]);
        if (dry) System.out.println("[DryRun] Building dialog (no show)");
        SwingUtilities.invokeLater(() -> {
            AWTErrorDialog dlg = new AWTErrorDialog(
                    dry?"DryRun Error":"Calista Engine Error",
                    dry?"UI build only":"Test exception",
                    new Exception("Test Throwable")
            );
            if (dry) dlg.dispose();
        });
    }
}
