package com.jme3.awt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A stylized error dialog for displaying error messages within the Calista Game Engine.
 * Designed to emulate the modern AAA game engine style (like Unreal 5).
 */
public class AWTErrorDialog extends JDialog {

    // === Constants for Styling ===
    private static final String DEFAULT_TITLE = "Calista Engine Error";
    private static final int PADDING = 20;

    // Color palette: dark background with subtle contrast
    private static final Color COLOR_BACKGROUND = new Color(30, 30, 35);
    private static final Color COLOR_FOREGROUND = new Color(235, 235, 235);
    private static final Color COLOR_ACCENT = new Color(0, 122, 204);
    private static final Color COLOR_ACCENT_HOVER = new Color(28, 151, 234);
    private static final Color COLOR_BORDER = new Color(80, 80, 90);
    private static final Color COLOR_AREA_BG = new Color(40, 40, 45);

    // Font styling
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font FONT_TEXT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 14);

    /**
     * Displays the error dialog with the specified message.
     */
    public static void showDialog(String message) {
        SwingUtilities.invokeLater(() -> {
            AWTErrorDialog dialog = new AWTErrorDialog(message, DEFAULT_TITLE);
            dialog.setVisible(true);
        });
    }

    /**
     * Displays the error dialog with a custom title.
     */
    public static void showDialog(String message, String title) {
        SwingUtilities.invokeLater(() -> {
            AWTErrorDialog dialog = new AWTErrorDialog(message, title);
            dialog.setVisible(true);
        });
    }

    /**
     * Private constructor.
     */
    private AWTErrorDialog(String message, String title) {
        super((Frame) null, title, true);
        configureDialog();
        initUI(message, title);
    }

    /**
     * Configures general dialog properties.
     */
    private void configureDialog() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(640, 400);
        setMinimumSize(new Dimension(400, 200));
        setLocationRelativeTo(null);

        Image icon = loadIcon("/Interface/Icons/error_icon.png");
        if (icon != null) {
            setIconImage(icon);
        }

        // Unified background
        getContentPane().setBackground(COLOR_BACKGROUND);

        // Remove window decorations for a "flat" AAA look (optional!)
        // setUndecorated(true);
    }

    /**
     * Initializes the UI elements with unified AAA styling.
     */
    private void initUI(String message, String title) {
        Container container = getContentPane();
        container.setLayout(new BorderLayout(PADDING, PADDING));

        // Title label (like Unreal's header bar)
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(COLOR_FOREGROUND);
        titleLabel.setBorder(new EmptyBorder(10, 0, 0, 0));

        // Text area inside a smooth, rounded panel
        JTextArea textArea = createTextArea(message);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(6, 6, 6, 6)
        ));

        // Button panel
        JPanel buttonPanel = createButtonPanel();

        // Adding components
        container.add(titleLabel, BorderLayout.NORTH);
        container.add(scrollPane, BorderLayout.CENTER);
        container.add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Creates a stylized text area for displaying messages.
     */
    private JTextArea createTextArea(String message) {
        JTextArea textArea = new JTextArea(message);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(FONT_TEXT);
        textArea.setForeground(COLOR_FOREGROUND);
        textArea.setBackground(COLOR_AREA_BG);
        textArea.setCaretColor(COLOR_FOREGROUND);

        // AAA-style margins and border
        textArea.setMargin(new Insets(12, 12, 12, 12));
        textArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        // Context menu for copy
        textArea.setComponentPopupMenu(createContextMenu(textArea));

        return textArea;
    }

    /**
     * Creates the bottom button panel.
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        panel.setBackground(COLOR_BACKGROUND);

        JButton okButton = new JButton("OK");
        styleButton(okButton);
        okButton.addActionListener(e -> dispose());

        panel.add(okButton);
        return panel;
    }

    /**
     * Styles buttons with accent color and smooth hover effect.
     */
    private void styleButton(JButton button) {
        button.setFont(FONT_BUTTON);
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(COLOR_ACCENT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_ACCENT_HOVER, 1, true),
                BorderFactory.createEmptyBorder(8, 24, 8, 24)
        ));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent evt) {
                button.setBackground(COLOR_ACCENT_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent evt) {
                button.setBackground(COLOR_ACCENT);
            }
        });
    }

    /**
     * Creates a right-click context menu for copying text.
     */
    private JPopupMenu createContextMenu(JTextArea textArea) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> {
            String text = textArea.getSelectedText();
            if (text == null || text.isEmpty()) {
                text = textArea.getText();
            }
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        });
        menu.add(copyItem);
        return menu;
    }

    /**
     * Loads an icon from resources.
     */
    private Image loadIcon(String path) {
        try {
            return Toolkit.getDefaultToolkit().getImage(getClass().getResource(path));
        } catch (Exception e) {
            return null;
        }
    }
}
