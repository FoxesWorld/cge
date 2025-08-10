package com.jme3.awt;

import com.formdev.flatlaf.FlatDarkLaf;
import org.foxesworld.cge.ICOParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static test.Game.setupTheme;

/**
 * Ультра-современное диалоговое окно для отображения критических ошибок,
 * стилизованное под отчеты о сбоях в Unreal Engine 5.
 * Обеспечивает максимальную информативность для разработчика и понятность для пользователя.
 *
 * @version 4.0 (UE5 Style)
 */
public class AWTErrorDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(AWTErrorDialog.class);

    // --- Стилизация и Константы ---
    private static final String ENGINE_NAME = "Calista Game Engine";
    private static final String ENGINE_VERSION = "1.5.0";
    private static final String REPORT_ISSUE_URI = "https://github.com/FoxesWorld/CGE/issues";
    private static final String ICON_PATH = "/assets/theme/icon/engineLogo.ico";
    private static final int ICON_SIZE = 64;


    // Цветовая палитра в стиле UE5
    //private static final Color COLOR_BACKGROUND = new Color(25, 25, 25);
    //private static final Color COLOR_HEADER_BG = new Color(45, 45, 45);
    private static final Color COLOR_TEXT_PRIMARY = new Color(220, 220, 220);
    private static final Color COLOR_TEXT_SECONDARY = new Color(160, 160, 160);
    private static final Color COLOR_ACCENT_YELLOW = new Color(255, 198, 0);
    private static final Color COLOR_BORDER = new Color(60, 60, 60);
    private static final Color COLOR_TEXT_AREA_BG = new Color(28, 28, 30);
    private static final Color COLOR_SUCCESS = new Color(0, 160, 60);

    // Шрифты
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_HEADER = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_LABEL = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);

    // Тексты UI
    private static final String COPY_BTN_TEXT = "Copy Report";
    private static final String COPIED_BTN_TEXT = "✔ Copied";
    private static final String REPORT_BTN_TEXT = "Report Issue";
    private static final String CLOSE_BTN_TEXT = "Close";

    // --- Поля класса ---
    private JTextArea stackTraceArea;
    private final transient Throwable throwable;
    private final AtomicBoolean isCopying = new AtomicBoolean(false);

    /**
     * Приватный конструктор. Используйте статический метод showDialog().
     */
    private AWTErrorDialog(Frame owner, Throwable throwable) {
        super(owner, ENGINE_NAME + " - Crash Report", true);
        this.throwable = throwable;

        // Настройка тёмной темы и UIManager overrides
        //FlatDarkLaf.setup();
        setupTheme("assets/theme/calista.properties");
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("Component.error.borderColor", COLOR_ACCENT_YELLOW);
        UIManager.put("Component.focusWidth", 1);

        // Логируем ошибку в основной лог
        log.error("Showing crash dialog for throwable", throwable);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setUndecorated(true);
        setSize(920, 700);
        setLocationRelativeTo(owner);
        // Скругленные углы
        try {
            setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 18, 18));
        } catch (Exception ex) {
            log.debug("Window shaping not supported on this platform", ex);
        }

        // --- Создание UI ---
        JPanel rootPanel = new JPanel(new BorderLayout());
        //rootPanel.setBackground(COLOR_BACKGROUND);
        rootPanel.setBorder(BorderFactory.createLineBorder(COLOR_BORDER.brighter(), 1));
        setContentPane(rootPanel);

        ErrorInfo errorInfo = formatErrorInfo(throwable);

        rootPanel.add(createHeaderPanel(errorInfo), BorderLayout.NORTH);
        rootPanel.add(createDetailsPanel(errorInfo), BorderLayout.CENTER);
        rootPanel.add(createActionsPanel(), BorderLayout.SOUTH);

        // --- Перетаскивание окна ---
        FrameDragListener frameDragListener = new FrameDragListener();
        addMouseListener(frameDragListener);
        addMouseMotionListener(frameDragListener);

        setupShortcuts();

        // Начальное значение прозрачности (fallback, если не поддерживается, будет игнорировано)
        try {
            setOpacity(0f);
        } catch (Exception e) {
            log.debug("Opacity not supported", e);
        }
    }

    // --- Методы для создания компонентов UI ---

    private JPanel createHeaderPanel(ErrorInfo info) {
        JPanel headerPanel = new JPanel(new BorderLayout(12, 0));
        //headerPanel.setBackground(COLOR_HEADER_BG);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)
        ));

        // Иконка движка
        JLabel iconLabel = new JLabel(loadDialogIcon());
        headerPanel.add(iconLabel, BorderLayout.WEST);

        // Текстовая часть заголовка
        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(info.title);
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(COLOR_TEXT_PRIMARY);

        JTextArea messageArea = createStyledTextArea(info.friendlyMessage, FONT_HEADER, COLOR_TEXT_SECONDARY);
        messageArea.setWrapStyleWord(true);

        // Подстрочные метаданные (версия/время/поток)
        JLabel metaLabel = new JLabel(String.format("Engine: %s  •  Version: %s  •  Time: %s  •  Thread: %s",
                ENGINE_NAME, ENGINE_VERSION, info.timestamp, info.threadName));
        metaLabel.setFont(FONT_HEADER.deriveFont(Font.PLAIN, 11f));
        metaLabel.setForeground(COLOR_TEXT_SECONDARY);

        textPanel.add(titleLabel);
        textPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        textPanel.add(messageArea);
        textPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        textPanel.add(metaLabel);

        headerPanel.add(textPanel, BorderLayout.CENTER);

        return headerPanel;
    }

    private JPanel createDetailsPanel(ErrorInfo info) {
        // Панель с основной причиной ошибки
        JPanel causePanel = new JPanel(new BorderLayout(0, 6));
        causePanel.setOpaque(false);
        causePanel.setBorder(BorderFactory.createEmptyBorder(14, 20, 8, 20));

        JLabel causeLabel = new JLabel("Unhandled Exception: " + info.rootCauseClass);
        causeLabel.setFont(FONT_LABEL);
        causeLabel.setForeground(COLOR_ACCENT_YELLOW);

        JTextArea causeMessageArea = createStyledTextArea(info.rootCauseMessage, FONT_HEADER.deriveFont(Font.BOLD), COLOR_TEXT_PRIMARY);
        causeMessageArea.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        causeMessageArea.setBackground(COLOR_TEXT_AREA_BG);
        causeMessageArea.setOpaque(true);
        causeMessageArea.setLineWrap(true);
        causeMessageArea.setWrapStyleWord(true);

        causePanel.add(causeLabel, BorderLayout.NORTH);
        causePanel.add(causeMessageArea, BorderLayout.CENTER);

        // Панель с полным стектрейсом
        JPanel stackTracePanel = new JPanel(new BorderLayout(0, 6));
        stackTracePanel.setOpaque(false);
        stackTracePanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 16, 20));

        JLabel stackTraceLabel = new JLabel("Full Call Stack:");
        stackTraceLabel.setFont(FONT_LABEL);
        stackTraceLabel.setForeground(COLOR_TEXT_SECONDARY);

        this.stackTraceArea = createStyledTextArea(getFullStackTrace(throwable), FONT_MONO, COLOR_TEXT_SECONDARY);
        this.stackTraceArea.setBackground(COLOR_TEXT_AREA_BG);
        this.stackTraceArea.setOpaque(true);
        this.stackTraceArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        this.stackTraceArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(this.stackTraceArea);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(COLOR_TEXT_AREA_BG);

        stackTracePanel.add(stackTraceLabel, BorderLayout.NORTH);
        stackTracePanel.add(scrollPane, BorderLayout.CENTER);

        // Разделитель: вертикальный split (панель причины / панель стектрейса)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, causePanel, stackTracePanel);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        splitPane.setDividerSize(8);
        splitPane.setDividerLocation(140);
        splitPane.setResizeWeight(0.15);
        splitPane.putClientProperty("JSplitPane.style", "line");

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(splitPane, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createActionsPanel() {
        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 10));
        actionsPanel.setOpaque(false);
        actionsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDER),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)
        ));

        JButton copyButton = new JButton(COPY_BTN_TEXT);
        styleButton(copyButton, false);
        copyButton.addActionListener(e -> copyReportToClipboard(copyButton));

        JButton reportButton = new JButton(REPORT_BTN_TEXT);
        styleButton(reportButton, false);
        reportButton.addActionListener(e -> openReportUrl());

        JButton closeButton = new JButton(CLOSE_BTN_TEXT);
        styleButton(closeButton, true); // primary
        closeButton.addActionListener(e -> dispose());

        actionsPanel.add(copyButton);
        actionsPanel.add(reportButton);
        actionsPanel.add(closeButton);
        return actionsPanel;
    }

    // --- Вспомогательные методы ---

    private JTextArea createStyledTextArea(String text, Font font, Color color) {
        JTextArea textArea = new JTextArea(text);
        textArea.setFont(font);
        textArea.setForeground(color);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setHighlighter(null);
        textArea.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        textArea.setOpaque(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        return textArea;
    }


    private void styleButton(JButton button, boolean isPrimary) {
        button.setFont(FONT_HEADER.deriveFont(Font.BOLD));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        button.setPreferredSize(new Dimension(120, 34));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        button.setBorderPainted(false);
        if (isPrimary) {
            button.setBackground(COLOR_ACCENT_YELLOW);
            button.setForeground(Color.BLACK);
        } else {
            button.setBackground(new Color(72, 72, 72));
            button.setForeground(COLOR_TEXT_PRIMARY);
        }
        button.putClientProperty("JButton.buttonType", "roundRect");
    }

    private void copyReportToClipboard(JButton feedbackButton) {
        if (isCopying.getAndSet(true)) return;

        ErrorInfo info = formatErrorInfo(throwable);
        String fullReport = buildFullReport(info, stackTraceArea.getText());

        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(fullReport), null);
            log.info("Crash report copied to clipboard.");
        } catch (Exception ex) {
            log.warn("Failed to copy crash report to clipboard", ex);
            JOptionPane.showMessageDialog(this, "Failed to copy to clipboard: " + ex.getMessage(), "Copy Error", JOptionPane.ERROR_MESSAGE);
            isCopying.set(false);
            return;
        }

        // Визуальная обратная связь: меняем текст и цвет, возвращаем через 2 секунды
        String originalText = feedbackButton.getText();
        Color originalBg = feedbackButton.getBackground();
        Color originalFg = feedbackButton.getForeground();

        feedbackButton.setText(COPIED_BTN_TEXT);
        feedbackButton.setBackground(COLOR_SUCCESS);
        feedbackButton.setForeground(Color.WHITE);

        Timer timer = new Timer(2000, e -> {
            feedbackButton.setText(originalText);
            feedbackButton.setBackground(originalBg);
            feedbackButton.setForeground(originalFg);
            isCopying.set(false);
        });
        timer.setRepeats(false);
        timer.start();
    }

    private String buildFullReport(ErrorInfo info, String stackTrace) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(ENGINE_NAME).append(" Crash Report ---\n\n");
        sb.append("Time: ").append(info.timestamp).append("\n");
        sb.append("Thread: ").append(info.threadName).append("\n");
        sb.append("Engine Version: ").append(ENGINE_VERSION).append("\n");
        sb.append("Exception: ").append(info.rootCauseClass).append("\n");
        sb.append("Message: ").append(info.rootCauseMessage).append("\n\n");

        sb.append("--- System Info ---\n");
        sb.append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
        sb.append("Java: ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor")).append(")\n");
        sb.append("Available processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        sb.append("Memory (free/total/max bytes): ")
                .append(Runtime.getRuntime().freeMemory()).append(" / ")
                .append(Runtime.getRuntime().totalMemory()).append(" / ")
                .append(Runtime.getRuntime().maxMemory()).append("\n\n");

        sb.append("--- Full Call Stack ---\n");
        sb.append(stackTrace).append("\n");
        return sb.toString();
    }

    private void openReportUrl() {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(REPORT_ISSUE_URI));
            } catch (IOException | URISyntaxException e) {
                log.warn("Failed to open report URL: {}", REPORT_ISSUE_URI, e);
                JOptionPane.showMessageDialog(this,
                        "Could not open the link. Please manually go to:\n" + REPORT_ISSUE_URI,
                        "Browser Error", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                    "Cannot open browser on this platform. Please visit:\n" + REPORT_ISSUE_URI,
                    "Browser Unsupported", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void setupShortcuts() {
        JRootPane rootPane = getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeAction");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copyAction");
        rootPane.getActionMap().put("closeAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });
        rootPane.getActionMap().put("copyAction", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                // Найдём кнопку Copy в панели действий и кликнем её
                for (Component c : ((JPanel)getContentPane().getComponent(2)).getComponents()) {
                    if (c instanceof JButton) {
                        JButton b = (JButton) c;
                        if (COPY_BTN_TEXT.equals(b.getText())) {
                            b.doClick();
                            break;
                        }
                    }
                }
            }
        });
    }

    // --- Статические методы для вызова диалога ---

    public static void showDialog(Throwable throwable) {
        SwingUtilities.invokeLater(() -> {
            AWTErrorDialog dialog = new AWTErrorDialog(null, throwable);

            // Запускаем анимацию появления (если поддерживается)
            dialog.startFadeIn();

            dialog.setVisible(true); // modal - будет блокировать
        });
    }

    private void startFadeIn() {
        try {
            setOpacity(0f);
        } catch (Exception e) {
            return;
        }
        final int delay = 20;
        final float step = 0.06f;
        Timer fadeTimer = new Timer(delay, null);
        fadeTimer.addActionListener(new ActionListener() {
            float current = 0f;
            @Override
            public void actionPerformed(ActionEvent e) {
                current += step;
                if (current >= 1f) {
                    try { setOpacity(1f); } catch (Exception ex) { /* ignore */ }
                    ((Timer)e.getSource()).stop();
                } else {
                    try { setOpacity(Math.min(1f, current)); } catch (Exception ex) { /* ignore */ }
                }
            }
        });
        fadeTimer.start();
    }

    // --- Утилиты для обработки исключений ---

    private static class ErrorInfo {
        String title;
        String friendlyMessage;
        String rootCauseClass;
        String rootCauseMessage;
        String timestamp;
        String threadName;

        public ErrorInfo(String title, String friendlyMessage, String rootCauseClass, String rootCauseMessage, String timestamp, String threadName) {
            this.title = title;
            this.friendlyMessage = friendlyMessage;
            this.rootCauseClass = rootCauseClass;
            this.rootCauseMessage = rootCauseMessage;
            this.timestamp = timestamp;
            this.threadName = threadName;
        }
    }

    private static ErrorInfo formatErrorInfo(Throwable throwable) {
        Throwable rootCause = findRootCause(throwable);
        String title = ENGINE_NAME + " has crashed.";
        String friendlyMessage = """
                A fatal error has occurred. We apologize for the inconvenience.
                Please copy the report and open an issue so we can investigate.
                """;
        String rootCauseClass = rootCause.getClass().getName();
        String rootCauseMessage = rootCause.getLocalizedMessage() != null ? rootCause.getLocalizedMessage() : "No message provided.";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String threadName = Thread.currentThread().getName();
        return new ErrorInfo(title, friendlyMessage, rootCauseClass, rootCauseMessage, timestamp, threadName);
    }

    private static Throwable findRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String getFullStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
        }
        return sw.toString();
    }

    // --- Утилиты для отрисовки и загрузки иконки ---

    /**
     * Создаёт иконку движка из ресурсов. Если не удаётся загрузить, возвращает иконку-предупреждение.
     */

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

    /**
     * Создает иконку предупреждения (желтый треугольник с восклицательным знаком).
     */
    private static Icon createWarningIcon(int size, Color color) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Polygon triangle = new Polygon();
            triangle.addPoint(size / 2, 3);
            triangle.addPoint(size - 3, size - 4);
            triangle.addPoint(3, size - 4);

            g2d.setColor(color);
            g2d.fill(triangle);

            // восклицательный знак - центрируем
            g2d.setFont(new Font("Segoe UI", Font.BOLD, Math.max(10, size / 2)));
            FontMetrics fm = g2d.getFontMetrics();
            String ex = "!";
            int textW = fm.stringWidth(ex);
            int textH = fm.getAscent();
            g2d.setColor(Color.BLACK);
            g2d.drawString(ex, (size - textW) / 2, (size + textH) / 2 - 4);
        } finally {
            g2d.dispose();
        }
        return new ImageIcon(image);
    }

    // --- Внутренний слушатель для перетаскивания окна ---
    private class FrameDragListener extends MouseAdapter {
        private Point mouseDownCompCoords = null;
        @Override
        public void mouseReleased(MouseEvent e) {
            mouseDownCompCoords = null;
        }
        @Override
        public void mousePressed(MouseEvent e) {
            mouseDownCompCoords = e.getPoint();
        }
        @Override
        public void mouseDragged(MouseEvent e) {
            Point currCoords = e.getLocationOnScreen();
            setLocation(currCoords.x - mouseDownCompCoords.x, currCoords.y - mouseDownCompCoords.y);
        }
    }
}
