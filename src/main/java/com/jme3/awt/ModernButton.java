package com.jme3.awt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Modern, polished button with smooth hover/press animations, ripple effect and focus ring.
 */
class ModernButton extends JButton {

    public enum ButtonStyle { PRIMARY, SECONDARY, TRANSPARENT }

    private final ButtonStyle style;

    // animation state
    private float hover = 0f;           // 0..1
    private float hoverTarget = 0f;
    private float press = 0f;           // 0..1 (pressed visual)
    private float pressTarget = 0f;
    private float focusAnim = 0f;       // 0..1 target when focused

    // ripple
    private boolean rippleActive = false;
    private float ripple = 0f;          // 0..1
    private Point rippleCenter = new Point();
    private float rippleMaxRadius = 0f;

    // shared animation timer (per-button instance)
    private Timer animTimer = null;
    private static final int FPS = 60;
    private static final int TIMER_DELAY = 1000 / FPS;

    // layout / spacing
    private static final Insets PRIMARY_PADDING = new Insets(9, 20, 9, 20);
    private static final Insets SECONDARY_PADDING = new Insets(8, 16, 8, 16);
    private static final int CORNER = 10;

    public ModernButton(String text, ButtonStyle style) {
        super(text);
        this.style = style == null ? ButtonStyle.PRIMARY : style;

        // Visual defaults
        setFont(Theme.FONT_BOLD.deriveFont(13f));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(false);
        setFocusable(true);

        // set preferred padding based on style
        switch (this.style) {
            case PRIMARY -> setBorder(BorderFactory.createEmptyBorder(
                    PRIMARY_PADDING.top, PRIMARY_PADDING.left, PRIMARY_PADDING.bottom, PRIMARY_PADDING.right));
            case SECONDARY -> setBorder(BorderFactory.createEmptyBorder(
                    SECONDARY_PADDING.top, SECONDARY_PADDING.left, SECONDARY_PADDING.bottom, SECONDARY_PADDING.right));
            case TRANSPARENT -> setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        }

        // Animation timer
        animTimer = new Timer(TIMER_DELAY, e -> {
            boolean needRepaint = false;

            // lerp factors
            float hLerp = 0.18f;
            float pLerp = 0.22f;
            float fLerp = 0.12f;

            // hover smoothing
            if (Math.abs(hover - hoverTarget) > 0.001f) {
                hover += (hoverTarget - hover) * hLerp;
                needRepaint = true;
            } else if (hoverTarget == 0f && hover != 0f) {
                hover = 0f;
                needRepaint = true;
            }

            // press smoothing
            if (Math.abs(press - pressTarget) > 0.001f) {
                press += (pressTarget - press) * pLerp;
                needRepaint = true;
            } else if (pressTarget == 0f && press != 0f) {
                press = 0f;
                needRepaint = true;
            }

            // focus smoothing
            float focusTarget = isFocusOwner() ? 1f : 0f;
            if (Math.abs(focusAnim - focusTarget) > 0.001f) {
                focusAnim += (focusTarget - focusAnim) * fLerp;
                needRepaint = true;
            }

            // ripple
            if (rippleActive) {
                ripple += 0.06f;
                if (ripple >= 1f) {
                    ripple = 1f;
                    rippleActive = false; // let it decay next cycles
                }
                needRepaint = true;
            } else if (ripple > 0f) {
                ripple *= 0.85f;
                if (ripple < 0.01f) ripple = 0f;
                needRepaint = true;
            }

            if (needRepaint) repaint();
            else animTimer.stop();
        });

        // Mouse interactions
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hoverTarget = 1f;
                if (!animTimer.isRunning()) animTimer.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverTarget = 0f;
                if (!animTimer.isRunning()) animTimer.start();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressTarget = 1f;
                // ripple center relative to component
                rippleCenter = e.getPoint();
                rippleMaxRadius = Math.max(getWidth(), getHeight()) * 1.2f;
                startRipple();
                if (!animTimer.isRunning()) animTimer.start();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressTarget = 0f;
                if (!animTimer.isRunning()) animTimer.start();
            }
        });

        // Keyboard activation and focus visuals
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) {
                    pressTarget = 1f;
                    // center ripple on button center for keyboard
                    rippleCenter = new Point(getWidth() / 2, getHeight() / 2);
                    rippleMaxRadius = Math.max(getWidth(), getHeight()) * 1.2f;
                    startRipple();
                    if (!animTimer.isRunning()) animTimer.start();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) {
                    pressTarget = 0f;
                    doClick();
                    if (!animTimer.isRunning()) animTimer.start();
                }
            }
        });

        // focus listener to animate focus ring
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (!animTimer.isRunning()) animTimer.start();
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (!animTimer.isRunning()) animTimer.start();
            }
        });
    }

    private void startRipple() {
        ripple = 0f;
        rippleActive = true;
        if (!animTimer.isRunning()) animTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            Shape base = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, CORNER, CORNER);

            // Background by style
            if (style == ButtonStyle.PRIMARY) {
                // subtle vertical gradient
                GradientPaint gp = new GradientPaint(0, 0, Theme.PRIMARY_TOP, 0, h, Theme.PRIMARY_BOTTOM);
                g2.setPaint(gp);
                g2.fill(base);

                // soft inner shadow when hovered/pressed
                if (hover > 0f || press > 0f) {
                    float alpha = Math.min(0.35f, hover * 0.18f + press * 0.28f);
                    g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                    g2.setColor(Color.black);
                    g2.fill(base);
                    g2.setComposite(AlphaComposite.SrcOver);
                }
            } else if (style == ButtonStyle.SECONDARY) {
                g2.setColor(Theme.SECONDARY_BG);
                g2.fill(base);
                // border
                g2.setColor(Theme.BORDER_COLOR);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(base);
            } else { // TRANSPARENT
                // no fill, just subtle hover/press overlays
            }

            // Hover overlay (white-ish)
            if (hover > 0f) {
                float alpha = 0.08f * hover;
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g2.setColor(Color.white);
                g2.fill(base);
                g2.setComposite(AlphaComposite.SrcOver);
            }

            // Press overlay (darken)
            if (press > 0f) {
                float alpha = 0.12f * press;
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g2.setColor(Color.black);
                g2.fill(base);
                g2.setComposite(AlphaComposite.SrcOver);
            }

            // Ripple
            if (ripple > 0f) {
                float r = ripple * rippleMaxRadius;
                float alpha = 0.32f * (1f - ripple);
                g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g2.setColor(Theme.RIPPLE_COLOR);
                Ellipse2D.Float circ = new Ellipse2D.Float(rippleCenter.x - r / 2f, rippleCenter.y - r / 2f, r, r);
                g2.fill(circ);
                g2.setComposite(AlphaComposite.SrcOver);
            }

            // Focus ring (outer stroke)
            if (focusAnim > 0f) {
                float strokeWidth = 2f + 1.5f * focusAnim;
                g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Color fc = Theme.FOCUS_COLOR;
                g2.setColor(new Color(fc.getRed(), fc.getGreen(), fc.getBlue(), (int) (90 * focusAnim)));
                RoundRectangle2D.Float outer = new RoundRectangle2D.Float(-strokeWidth / 2f, -strokeWidth / 2f, w + strokeWidth, h + strokeWidth, CORNER + 2, CORNER + 2);
                g2.draw(outer);
            }

            // Prepare foreground (text/icon)
            setForeground(style == ButtonStyle.PRIMARY ? Theme.PRIMARY_FG : Theme.TEXT_COLOR);
            // Let Swing paint text/icon on top (we used setContentAreaFilled(false))
            super.paintComponent(g2);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        // compute preferred size based on text + icon + padding
        FontMetrics fm = getFontMetrics(getFont());
        Icon ic = getIcon();
        int textW = fm.stringWidth(getText());
        int textH = fm.getHeight();

        Insets in = getInsets();
        int w = in.left + in.right + textW + 6 + (ic != null ? ic.getIconWidth() + 6 : 0);
        int h = in.top + in.bottom + Math.max(textH, ic != null ? ic.getIconHeight() : 0);
        return new Dimension(w, h);
    }

    // Small theme used by the button (kept local)
    private static final class Theme {
        static final Color PRIMARY_TOP = new Color(0xE53935);      // top of gradient
        static final Color PRIMARY_BOTTOM = new Color(0xD32F2F);   // bottom of gradient
        static final Color PRIMARY_FG = Color.WHITE;
        static final Color SECONDARY_BG = new Color(0x2E2F33);
        static final Color TEXT_COLOR = new Color(0xF4F4F4);
        static final Color BORDER_COLOR = new Color(0x3A3B3F);
        static final Color RIPPLE_COLOR = new Color(0xFFFFFF);
        static final Color FOCUS_COLOR = new Color(0x66A6FF);
        static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    }
}
