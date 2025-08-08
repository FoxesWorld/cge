// РЕКОМЕНДАЦИЯ: Избегайте использования пакетов чужих библиотек.
// Лучше использовать свой, например: org.foxesworld.cge.ui
package com.jme3.awt;


import com.formdev.flatlaf.FlatDarkLaf;
import org.foxesworld.cge.ICOParser;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * Продвинутое диалоговое окно для отображения ошибок, оптимизированное
 * для работы с полными, вложенными исключениями (например, из JMonkeyEngine).
 * Автоматически анализирует причину ошибки (root cause) и предоставляет
 * полный стектрейс для диагностики.
 *
 * @version 2.0
 */
public class AWTErrorDialog extends JDialog {

    private static final String DEFAULT_TITLE = "Calista Game Engine: Fatal Error";
    private static final String REPORT_ISSUE_URI = "https://github.com/your/repo/issues";
    private static final String ICON_PATH = "/assets/theme/icon/engineLogo.ico";
    private static final int ICON_SIZE = 24;

    private static final String COPY_BTN_TEXT = "Copy Full Trace";
    private static final String COPIED_BTN_TEXT = "✔ Copied!";
    private static final String CLOSE_BTN_TEXT = "Close";

    private final ModernDialogPanel mainPanel;
    private JTextArea stackTraceArea;

    /**
     * Приватный конструктор. Используйте статические методы showDialog() для создания.
     */
    private AWTErrorDialog(Frame owner, String title, String message, String stackTrace) {
        super(owner, true);
        FlatDarkLaf.setup();

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(850, 600);
        setLocationRelativeTo(owner);

        mainPanel = new ModernDialogPanel();
        mainPanel.setLayout(new BorderLayout());
        setContentPane(mainPanel);

        ImageIcon icon = loadDialogIcon();
        ModernTitleBar titleBar = new ModernTitleBar(title, icon, e -> mainPanel.hideAnimated());
        JPanel contentPanel = createContentPanel(message, stackTrace);

        mainPanel.add(titleBar, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        setupShortcuts();
        mainPanel.showAnimated();
    }

    private JPanel createContentPanel(String message, String stackTrace) {
        JPanel contentPanel = new JPanel(new BorderLayout(0, 15));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        JTextArea messageArea = createStyledTextArea(message, Theme.FONT_REGULAR, Theme.TEXT_COLOR);
        this.stackTraceArea = createStyledTextArea(stackTrace, Theme.FONT_MONO, Theme.TEXT_MUTED_COLOR);
        this.stackTraceArea.setBackground(Theme.TEXT_AREA_BG);
        this.stackTraceArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_COLOR_LIGHT),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        JScrollPane stackScroll = new JScrollPane(this.stackTraceArea);
        stackScroll.setBorder(null);
        stackScroll.setOpaque(false);
        stackScroll.getViewport().setOpaque(false);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, messageArea, stackScroll);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        splitPane.setDividerSize(10);
        splitPane.setDividerLocation(120);
        splitPane.setResizeWeight(0.2);

        JPanel actionsPanel = createActionsPanel();

        contentPanel.add(splitPane, BorderLayout.CENTER);
        contentPanel.add(actionsPanel, BorderLayout.SOUTH);
        return contentPanel;
    }

    private JTextArea createStyledTextArea(String text, Font font, Color foreground) {
        JTextArea textArea = new JTextArea(text);
        textArea.setFont(font);
        textArea.setForeground(foreground);
        textArea.setOpaque(false);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setCaretPosition(0);
        return textArea;
    }

    private JPanel createActionsPanel() {
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        actionsPanel.setOpaque(false);

        ModernButton copyButton = new ModernButton(COPY_BTN_TEXT, ModernButton.ButtonStyle.SECONDARY);
        copyButton.addActionListener(e -> copyStackTraceToClipboard());

        ModernButton closeButton = new ModernButton(CLOSE_BTN_TEXT, ModernButton.ButtonStyle.PRIMARY);
        closeButton.addActionListener(e -> mainPanel.hideAnimated());

        actionsPanel.add(copyButton);
        actionsPanel.add(closeButton);
        return actionsPanel;
    }

    private ImageIcon loadDialogIcon() {
        try (InputStream stream = AWTErrorDialog.class.getResourceAsStream(ICON_PATH)) {
            if (stream == null) {
                System.err.println("Warning: Dialog icon not found at " + ICON_PATH);
                return null;
            }
            List<BufferedImage> icons = new ICOParser().parse(stream);
            if (icons != null && icons.size() > 2) {
                Image sourceImage = icons.get(2); // Берем иконку среднего размера
                BufferedImage resizedImg = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = resizedImg.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(sourceImage, 0, 0, ICON_SIZE, ICON_SIZE, null);
                g2d.dispose();
                return new ImageIcon(resizedImg);
            }
        } catch (IOException e) {
            System.err.println("Error loading dialog icon: " + e.getMessage());
        }
        return null;
    }

    private void copyStackTraceToClipboard() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(stackTraceArea.getText()), null);
    }

    private void setupShortcuts() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeAction");
        actionMap.put("closeAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { mainPanel.hideAnimated(); }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copyAction");
        actionMap.put("copyAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { copyStackTraceToClipboard(); }
        });
    }

    public static void showDialog(Throwable throwable) {
        showDialog(null, DEFAULT_TITLE, throwable);
    }

    public static void showDialog(Component parent, String title, Throwable throwable) {
        final Frame owner = (parent instanceof Frame) ? (Frame) parent : null;
        final String message = formatErrorMessage(throwable);
        final String stackTrace = getFullStackTrace(throwable);

        SwingUtilities.invokeLater(() -> {
            AWTErrorDialog dialog = new AWTErrorDialog(owner, title, message, stackTrace);
            dialog.setVisible(true);
        });
    }

    private static String formatErrorMessage(Throwable throwable) {
        Throwable rootCause = findRootCause(throwable);
        String mainMessage = throwable.getClass().getSimpleName() + ": " + throwable.getLocalizedMessage();

        if (rootCause != throwable) {
            String causeMessage = rootCause.getClass().getSimpleName() + ": " + rootCause.getLocalizedMessage();
            return "An error occurred. Root cause: " + causeMessage + "\n" + mainMessage;
        }
        return mainMessage;
    }

    private static Throwable findRootCause(Throwable throwable) {
        Throwable cause = throwable.getCause();
        while (cause != null) {
            throwable = cause;
            cause = throwable.getCause();
        }
        return throwable;
    }

    private static String getFullStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}