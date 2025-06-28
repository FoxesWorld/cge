package com.jme3.awt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

class ModernButton extends JButton {
    public enum ButtonStyle { PRIMARY, SECONDARY, TRANSPARENT }

    private final ButtonStyle style;
    private float hoverAlpha = 0.0f;
    private float pressedAlpha = 0.0f;
    private final Timer hoverTimer;

    public ModernButton(String text, ButtonStyle style) {
        super(text);
        this.style = style;

        setFont(Theme.FONT_BOLD.deriveFont(13f));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(false);

        // Hover animation timer
        hoverTimer = new Timer(15, e -> {
            boolean isHover = getModel().isRollover();
            float targetAlpha = isHover ? 1.0f : 0.0f;
            if (Math.abs(hoverAlpha - targetAlpha) > 0.01f) {
                hoverAlpha += (targetAlpha - hoverAlpha) * 0.2f;
                repaint();
            } else if (!isHover) {
                ((Timer) e.getSource()).stop();
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!hoverTimer.isRunning()) hoverTimer.start();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!hoverTimer.isRunning()) hoverTimer.start();
            }
            @Override
            public void mousePressed(MouseEvent e) {
                pressedAlpha = 1.0f;
                repaint();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                pressedAlpha = 0.0f;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        Shape shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 10, 10);

        Color baseBg = Color.BLACK;
        Color baseFg = Color.WHITE;

        switch (style) {
            case PRIMARY:
                baseBg = Theme.PRIMARY_ACCENT_COLOR;
                baseFg = Color.WHITE;
                setBorder(BorderFactory.createEmptyBorder(9, 22, 9, 22));
                break;
            case SECONDARY:
                baseBg = Theme.BUTTON_SECONDARY_BG;
                baseFg = Theme.TEXT_COLOR;
                setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
                break;
            case TRANSPARENT:
                baseBg = new Color(0,0,0,0);
                baseFg = Theme.TEXT_COLOR;
                setBorder(null);
                break;
        }

        // Paint background
        g2d.setColor(baseBg);
        g2d.fill(shape);

        // Paint hover effect
        g2d.setComposite(AlphaComposite.SrcOver.derive(hoverAlpha * 0.15f));
        g2d.setColor(Color.WHITE);
        g2d.fill(shape);

        // Paint pressed effect
        g2d.setComposite(AlphaComposite.SrcOver.derive(pressedAlpha * 0.1f));
        g2d.setColor(Color.BLACK);
        g2d.fill(shape);

        g2d.setComposite(AlphaComposite.SrcOver);

        setForeground(baseFg);
        super.paintComponent(g);

        g2d.dispose();
    }
}