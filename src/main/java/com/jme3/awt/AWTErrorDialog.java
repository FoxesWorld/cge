package com.jme3.awt;

import com.formdev.flatlaf.FlatDarkLaf;
import org.foxesworld.cge.ICOParser;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AAA-style error dialog using modern Swing rendering techniques.
 * Features a unified custom-painted panel for performance, and reusable components.
 */
public class AWTErrorDialog extends JDialog {

    private final ModernDialogPanel mainPanel;
    private final JTextArea stackArea;
    private final AtomicReference<ModernButton> copyBtnRef = new AtomicReference<>();

    public AWTErrorDialog(Frame owner, String title, String message, String stackTrace) {
        super(owner, true);
        FlatDarkLaf.setup(); // Ensure FlatLaf is set up

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0)); // Transparent dialog
        setSize(800, 550);
        setLocationRelativeTo(owner);

        mainPanel = new ModernDialogPanel();
        mainPanel.setLayout(new BorderLayout());
        setContentPane(mainPanel);

        // --- Build UI Components ---
        ImageIcon icon = loadIcon();
        ModernTitleBar titleBar = new ModernTitleBar(title, icon, e -> mainPanel.fadeOut());

        JPanel contentPanel = new JPanel(new BorderLayout(0, 15));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // Message Area
        JTextArea msgArea = new JTextArea(message);
        msgArea.setFont(Theme.FONT_REGULAR);
        msgArea.setForeground(Theme.TEXT_COLOR);
        msgArea.setOpaque(false);
        msgArea.setEditable(false);
        msgArea.setLineWrap(true);
        msgArea.setWrapStyleWord(true);

        // Stack Trace Area
        stackArea = new JTextArea(stackTrace);
        stackArea.setFont(Theme.FONT_MONO);
        stackArea.setForeground(Theme.TEXT_MUTED_COLOR);
        stackArea.setBackground(Theme.TEXT_AREA_BG);
        stackArea.setCaretPosition(0);
        stackArea.setEditable(false);
        stackArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_COLOR_LIGHT),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JScrollPane stackScroll = new JScrollPane(stackArea);
        stackScroll.setBorder(null);
        stackScroll.setOpaque(false);
        stackScroll.getViewport().setOpaque(false);

        // Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, msgArea, stackScroll);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        splitPane.setDividerSize(8);
        splitPane.setDividerLocation(100);
        splitPane.setResizeWeight(0.15);

        // Bottom Actions
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        actionsPanel.setOpaque(false);

        ModernButton copyButton = new ModernButton("Copy Trace", ModernButton.ButtonStyle.SECONDARY);
        copyButton.addActionListener(e -> copyStackTrace());
        copyBtnRef.set(copyButton);

        ModernButton reportButton = new ModernButton("Report Issue", ModernButton.ButtonStyle.SECONDARY);
        reportButton.addActionListener(e -> openReportURI());

        ModernButton closeButton = new ModernButton("Close", ModernButton.ButtonStyle.PRIMARY);
        closeButton.addActionListener(e -> mainPanel.fadeOut());

        actionsPanel.add(copyButton);
        //actionsPanel.add(reportButton); // Optional
        actionsPanel.add(closeButton);

        contentPanel.add(splitPane, BorderLayout.CENTER);
        contentPanel.add(actionsPanel, BorderLayout.SOUTH);

        mainPanel.add(titleBar, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        setupShortcuts();

        // Start fade-in animation
        mainPanel.fadeIn();
    }

    private ImageIcon loadIcon() {
        try {
            var parser = new ICOParser();
            var icons = parser.parse(Objects.requireNonNull(
                    AWTErrorDialog.class.getResourceAsStream("/assets/theme/icon/engineLogo.ico")));
            if (icons != null && icons.size() > 2) {
                return new ImageIcon(icons.get(2).getScaledInstance(24, 24, Image.SCALE_SMOOTH));
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Icon load failed: " + e.getMessage());
        }
        return null;
    }

    private void copyStackTrace() {
        ModernButton copyButton = copyBtnRef.get();
        if (copyButton == null) return;

        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(stackArea.getText()), null);

        String originalText = copyButton.getText();
        copyButton.setText("✔ Copied!");
        Timer timer = new Timer(1500, e -> copyButton.setText(originalText));
        timer.setRepeats(false);
        timer.start();
    }

    private void openReportURI() {
        try {
            Desktop.getDesktop().browse(new URI("https://github.com/your/repo/issues"));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open browser.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setupShortcuts() {
        Action closeAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                mainPanel.fadeOut();
            }
        };
        Action copyAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                copyStackTrace();
            }
        };

        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        root.getActionMap().put("close", closeAction);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        root.getActionMap().put("copy", copyAction);
    }

    public static void showDialog(String message) {
        SwingUtilities.invokeLater(() -> {
            AWTErrorDialog dialog = new AWTErrorDialog(null, "Error", "We are sorry =(", message);
            dialog.setVisible(true);
        });
    }

    public static void main(String[] args) {
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        java.util.logging.LogManager.getLogManager().reset();
        try {
            int x = 5 / 0;
        } catch (Exception e) {
            showDialog(e.getMessage());
        }
    }
}