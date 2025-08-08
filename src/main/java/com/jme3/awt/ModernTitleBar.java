package com.jme3.awt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * ModernTitleBar — исправлена проблема дублирования текста при hover close-button.
 * Тень теперь рисуется внутри ShadowLabel, сам paintComponent больше не рисует текст.
 */
class ModernTitleBar extends JPanel {
    private final ShadowLabel titleLabel;         // <-- заменили JLabel на ShadowLabel
    private final JLabel subtitleLabel;
    private final CloseButton closeButton;
    // drag state...
    private Rectangle restoredBounds;
    private boolean maximized = false;
    private Point initialMouseScreen;
    private Rectangle initialWindowBounds;
    private boolean dragging = false;
    private long pressTimeMs = 0L;

    // thresholds
    private static final int DRAG_START_THRESHOLD = 4;
    private static final int DOUBLE_CLICK_MS = 400;
    private static final int SNAP_MARGIN = 12;
    private static final int MIN_VISIBLE_ON_EDGE = 60;

    public ModernTitleBar(String title, ImageIcon icon, ActionListener closeAction) {
        this(title, null, icon, closeAction);
    }

    public ModernTitleBar(String title, String subtitle, ImageIcon icon, ActionListener closeAction) {
        setLayout(new BorderLayout(8, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(6, 14, 6, 8));
        setPreferredSize(new Dimension(200, 44));

        // text panel
        JPanel textPanel = new JPanel(new GridBagLayout());
        textPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        titleLabel = new ShadowLabel(title != null ? title : "");
        titleLabel.setFont(Theme.FONT_BOLD.deriveFont(18f));
        titleLabel.setForeground(Theme.TEXT_COLOR);
        titleLabel.setBorder(new EmptyBorder(0, icon != null ? 8 : 0, 0, 0));
        titleLabel.setOpaque(false);
        titleLabel.setFocusable(false);

        subtitleLabel = new JLabel(subtitle != null ? subtitle : "");
        subtitleLabel.setFont(Theme.FONT_REGULAR.deriveFont(11f));
        subtitleLabel.setForeground(Theme.TEXT_COLOR_DERIVED);
        subtitleLabel.setOpaque(false);

        gbc.gridy = 0;
        textPanel.add(titleLabel, gbc);
        gbc.gridy = 1;
        textPanel.add(subtitleLabel, gbc);

        if (icon != null) {
            JLabel iconLabel = new JLabel(scaleIcon(icon));
            iconLabel.setBorder(new EmptyBorder(0, 0, 0, 6));
            iconLabel.setOpaque(false);
            add(iconLabel, BorderLayout.WEST);
        }

        add(textPanel, BorderLayout.CENTER);

        closeButton = new CloseButton(closeAction);
        add(closeButton, BorderLayout.EAST);

        // enhanced dragging & double-click maximize/restore
        MouseAdapter ma = new MouseAdapter() {
            private long lastClick = 0L;

            @Override
            public void mousePressed(MouseEvent e) {
                Window w = SwingUtilities.getWindowAncestor(ModernTitleBar.this);
                if (w == null) return;
                initialMouseScreen = e.getLocationOnScreen();
                initialWindowBounds = w.getBounds();
                pressTimeMs = System.currentTimeMillis();
                dragging = false;

                long now = System.currentTimeMillis();
                if (now - lastClick < DOUBLE_CLICK_MS && SwingUtilities.isLeftMouseButton(e)) {
                    if (w instanceof Frame) toggleMaximize((Frame) w);
                }
                lastClick = now;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Window w = SwingUtilities.getWindowAncestor(ModernTitleBar.this);
                if (w == null || initialMouseScreen == null || initialWindowBounds == null) return;
                Point current = e.getLocationOnScreen();
                int dx = current.x - initialMouseScreen.x;
                int dy = current.y - initialMouseScreen.y;

                if (!dragging) {
                    if (Math.hypot(dx, dy) < DRAG_START_THRESHOLD) return;
                    dragging = true;
                    if (maximized) {
                        // compute ratio of cursor over the window to preserve cursor position after restore
                        double ratioX = (double) (initialMouseScreen.x - initialWindowBounds.x) / (double) initialWindowBounds.width;
                        Rectangle targetRestore = (restoredBounds != null) ? new Rectangle(restoredBounds) : computeDefaultRestoreBounds(w);
                        Rectangle screen = getWindowScreenBounds(w);
                        targetRestore = clampToScreen(targetRestore, screen);
                        int newX = (int) (current.x - ratioX * targetRestore.width);
                        int newY = current.y - Math.max(24, targetRestore.height / 10);
                        targetRestore.setLocation(newX, newY);
                        w.setBounds(targetRestore);
                        maximized = false;
                        initialWindowBounds = w.getBounds();
                        initialMouseScreen = current;
                        return;
                    }
                }
                int newX = initialWindowBounds.x + dx;
                int newY = initialWindowBounds.y + dy;
                Rectangle screen = getWindowScreenBounds(w);
                newX = Math.max(screen.x - (initialWindowBounds.width - MIN_VISIBLE_ON_EDGE),
                        Math.min(newX, screen.x + screen.width - MIN_VISIBLE_ON_EDGE));
                newY = Math.max(screen.y - (initialWindowBounds.height - MIN_VISIBLE_ON_EDGE),
                        Math.min(newY, screen.y + screen.height - MIN_VISIBLE_ON_EDGE));
                w.setLocation(newX, newY);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Window w = SwingUtilities.getWindowAncestor(ModernTitleBar.this);
                if (w == null) return;
                Point release = e.getLocationOnScreen();
                Rectangle screen = getWindowScreenBounds(w);

                if (release.y <= screen.y + SNAP_MARGIN) {
                    if (w instanceof Frame) { restoredBounds = w.getBounds(); maximize((Frame) w, screen); maximized = true; }
                    dragging = false; return;
                }
                if (Math.abs(release.x - screen.x) <= SNAP_MARGIN) {
                    Rectangle half = new Rectangle(screen.x, screen.y, screen.width / 2, screen.height);
                    if (w instanceof Frame) { restoredBounds = w.getBounds(); w.setBounds(half); maximized = false; }
                    dragging = false; return;
                }
                if (Math.abs(release.x - (screen.x + screen.width)) <= SNAP_MARGIN) {
                    Rectangle half = new Rectangle(screen.x + screen.width / 2, screen.y, screen.width / 2, screen.height);
                    if (w instanceof Frame) { restoredBounds = w.getBounds(); w.setBounds(half); maximized = false; }
                    dragging = false; return;
                }
                dragging = false;
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        setToolTipText(title);
        titleLabel.getAccessibleContext().setAccessibleName("Title");
        closeButton.getAccessibleContext().setAccessibleName("Close");
        subtitleLabel.setVisible(subtitle != null && !subtitle.isEmpty());
    }

    private Rectangle getWindowScreenBounds(Window w) {
        GraphicsConfiguration gc = w.getGraphicsConfiguration();
        return (gc != null) ? gc.getBounds() : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    private void toggleMaximize(Frame frame) {
        Rectangle screenBounds = frame.getGraphicsConfiguration().getBounds();
        if (!maximized) {
            restoredBounds = frame.getBounds();
            maximize(frame, screenBounds);
            maximized = true;
        } else {
            if (restoredBounds != null) frame.setBounds(restoredBounds);
            maximized = false;
        }
    }

    private void maximize(Frame frame, Rectangle screenBounds) {
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(frame.getGraphicsConfiguration());
        Rectangle usable = new Rectangle(screenBounds);
        usable.x += insets.left; usable.y += insets.top;
        usable.width -= (insets.left + insets.right); usable.height -= (insets.top + insets.bottom);
        frame.setBounds(usable);
    }

    private Rectangle computeDefaultRestoreBounds(Window w) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int wW = Math.min(1000, Math.max(700, screen.width * 2 / 3));
        int wH = Math.min(800, Math.max(480, screen.height * 2 / 3));
        int x = Math.max(50, (screen.width - wW) / 2);
        int y = Math.max(50, (screen.height - wH) / 2);
        return new Rectangle(x, y, wW, wH);
    }

    private Rectangle clampToScreen(Rectangle r, Rectangle screen) {
        int x = Math.max(screen.x, Math.min(r.x, screen.x + screen.width - Math.max(r.width, MIN_VISIBLE_ON_EDGE)));
        int y = Math.max(screen.y, Math.min(r.y, screen.y + screen.height - Math.max(r.height, MIN_VISIBLE_ON_EDGE)));
        return new Rectangle(x, y, Math.min(r.width, screen.width), Math.min(r.height, screen.height));
    }

    private ImageIcon scaleIcon(ImageIcon icon) {
        int target = 28;
        Image img = icon.getImage();
        int w = img.getWidth(null);
        int h = img.getHeight(null);
        if (w == target && h == target) return icon;
        Image scaled = img.getScaledInstance(target, target, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int w = getWidth();
            int h = getHeight();

            Shape clipShape = new RoundRectangle2D.Float(0, 0, w, h + Theme.CORNER_RADIUS, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);
            Area area = new Area(clipShape);
            area.subtract(new Area(new Rectangle2D.Float(0, h, w, Theme.CORNER_RADIUS)));
            g2.setClip(area);

            GradientPaint gp = new GradientPaint(0, 0, Theme.TITLE_BAR_GRADIENT_TOP, 0, h, Theme.TITLE_BAR_GRADIENT_BOTTOM);
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Float(4, 4, w - 8, h / 2f, Theme.CORNER_RADIUS / 2f, Theme.CORNER_RADIUS / 2f));
            g2.setComposite(AlphaComposite.SrcOver);

            g2.setPaint(new GradientPaint(0, h - 4, Theme.BOTTOM_ACCENT_START, 0, h + 6, Theme.BOTTOM_ACCENT_END));
            g2.fillRect(0, h - 4, w, 6);

            g2.setColor(Theme.BORDER_COLOR_LIGHT);
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(0, h - 1, w, h - 1);

            g2.setClip(null);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f));
            g2.setColor(Color.BLACK);
            g2.fill(new RoundRectangle2D.Float(2, 2 + h, w - 4, 8, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS));
            g2.setComposite(AlphaComposite.SrcOver);

            // ** УБРАЛИ РУЧНУЮ ОТРИСОВКУ ТЕКСТА **
            // теперь ShadowLabel рисует тень + затем сам текст (через super.paintComponent)
        } finally {
            g2.dispose();
        }
        super.paintComponent(g); // <-- оставляем чтобы Swing нарисовал детей (label, button)
    }

    private boolean iconPresent() {
        Component west = getComponentCount() > 0 ? getComponent(0) : null;
        return west instanceof JLabel && ((JLabel) west).getIcon() != null;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, 46);
    }

    // ---- inner CloseButton (удалён для краткости — оставь свою версию) ----
    private static final class CloseButton extends JButton {
        // ... (оставь свою реализацию кнопки) ...
        CloseButton(ActionListener closeAction) { /* ... */ }
        // реализуй paintComponent и т.д.
    }

    // ---- NEW: ShadowLabel — рисует тень, потом вызывает super.paintComponent */
    private static final class ShadowLabel extends JLabel {
        ShadowLabel(String text) {
            super(text);
            setOpaque(false);
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                String txt = getText();
                if (txt != null && !txt.isEmpty()) {
                    FontMetrics fm = g2.getFontMetrics(getFont());
                    int x = 0; // we rely on label's insets/layout; adjust if needed
                    int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                    // draw subtle shadow
                    g2.setColor(new Color(0, 0, 0, 90));
                    g2.setFont(getFont());
                    g2.drawString(txt, x + 1, y + 1);
                }
            } finally {
                g2.dispose();
            }
            // Now let JLabel paint the actual text (once)
            super.paintComponent(g);
        }
    }

    // ---------- Theme constants ----------
    private static final class Theme {
        static final Color TITLE_BAR_GRADIENT_TOP = new Color(0x121217);
        static final Color TITLE_BAR_GRADIENT_BOTTOM = new Color(0x16161A);
        static final Color BOTTOM_ACCENT_START = new Color(0x2A2A2E, true);
        static final Color BOTTOM_ACCENT_END = new Color(0x2A2A2E, true);
        static final Color BORDER_COLOR_LIGHT = new Color(0x2E2E32);
        static final Color TEXT_COLOR = new Color(0xF4F4F4);
        static final Color TEXT_COLOR_DERIVED = new Color(0xBDBDBD);
        static final Color ACCENT = new Color(0xD32F2F);
        static final int CORNER_RADIUS = 12;
        static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
        static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 12);
    }
}
