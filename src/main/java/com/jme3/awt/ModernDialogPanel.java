package com.jme3.awt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

class ModernDialogPanel extends JPanel {
    private float opacity = 0f;
    private final Timer fadeInTimer;
    private final Timer fadeOutTimer;
    private Point dragOffset;

    // --- НОВЫЙ КОД: Буфер для отрисовки ---
    private BufferedImage buffer;
    private boolean needsRedraw = true; // Флаг, чтобы перерисовывать буфер только при необходимости

    // Aurora effect properties
    private final Ellipse2D.Float aurora1 = new Ellipse2D.Float(-200, -150, 500, 300);
    private final Ellipse2D.Float aurora2 = new Ellipse2D.Float(500, 300, 450, 250);
    private double angle1 = 0, angle2 = Math.PI;

    public ModernDialogPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());

        fadeInTimer = new Timer(15, e -> {
            opacity = Math.min(1f, opacity + 0.05f);
            repaint(); // Просто просим перерисовать, не пересоздавая буфер
            if (opacity >= 1f) ((Timer) e.getSource()).stop();
        });
        fadeOutTimer = new Timer(15, e -> {
            opacity = Math.max(0f, opacity - 0.05f);
            repaint();
            if (opacity <= 0f) {
                ((Timer) e.getSource()).stop();
                SwingUtilities.getWindowAncestor(this).dispose();
            }
        });

        new Timer(40, e -> {
            angle1 += 0.01;
            angle2 -= 0.008;
            aurora1.x += (float) Math.cos(angle1) * 1.5f;
            aurora1.y += (float) Math.sin(angle2) * 1.5f;
            aurora2.x += (float) Math.sin(angle2) * 1.2f;
            aurora2.y += (float) Math.cos(angle1) * 1.2f;

            needsRedraw = true; // Движение авроры требует перерисовки буфера
            if(isVisible()) repaint();
        }).start();

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragOffset = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                Window w = SwingUtilities.getWindowAncestor(ModernDialogPanel.this);
                if (w != null) w.setLocation(e.getXOnScreen() - dragOffset.x, e.getYOnScreen() - dragOffset.y);
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    public void fadeIn() { fadeInTimer.start(); }
    public void fadeOut() { fadeOutTimer.start(); }

    @Override
    protected void paintComponent(Graphics g) {
        // --- НОВЫЙ КОД: Логика двойной буферизации ---
        // 1. Проверяем, нужно ли пересоздавать буфер (например, после изменения размера окна)
        if (buffer == null || buffer.getWidth() != getWidth() || buffer.getHeight() != getHeight()) {
            buffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            needsRedraw = true;
        }

        // 2. Если что-то изменилось (анимация, размер), перерисовываем содержимое в буфер
        if (needsRedraw) {
            drawToBuffer();
            needsRedraw = false; // Сбрасываем флаг до следующего изменения
        }

        // 3. Рисуем готовый буфер на экран. ЭТО ЕДИНСТВЕННАЯ ОПЕРАЦИЯ РИСОВАНИЯ ЗДЕСЬ.
        // super.paintComponent(g) не нужен, т.к. мы полностью контролируем отрисовку
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.SrcOver.derive(opacity));
        g2d.drawImage(buffer, 0, 0, null);
        g2d.dispose();
    }

    /**
     * Вся сложная логика отрисовки теперь здесь. Рисует не на экран, а в буфер.
     */
    private void drawToBuffer() {
        if (buffer == null) return;

        Graphics2D g2d = buffer.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // --- Шаг 0: Очищаем буфер полностью прозрачным цветом ---
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setComposite(AlphaComposite.SrcOver);

        // 1. Рисуем темный оверлей
        g2d.setColor(Theme.OVERLAY_BG);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // 2. Рисуем тень
        int shadowOffset = Theme.SHADOW_SIZE;
        int w = getWidth() - shadowOffset * 2;
        int h = getHeight() - shadowOffset * 2;
        g2d.setColor(Theme.SHADOW_COLOR);
        g2d.fill(new RoundRectangle2D.Float(shadowOffset, shadowOffset, w, h, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS));

        // 3. Рисуем основной фон диалога
        Shape dialogShape = new RoundRectangle2D.Float(shadowOffset, shadowOffset, w, h, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);
        g2d.setClip(dialogShape);

        // Базовый фон
        g2d.setColor(Theme.DIALOG_BG);
        g2d.fillRect(shadowOffset, shadowOffset, w, h);

        // Эффект Авроры
        Point2D center1 = new Point2D.Float(aurora1.x + aurora1.width / 2, aurora1.y + aurora1.height / 2);
        g2d.setPaint(new RadialGradientPaint(center1, aurora1.width / 2, new float[]{0f, 1f}, new Color[]{Theme.AURORA_1, new Color(0,0,0,0)}));
        g2d.fill(aurora1);

        Point2D center2 = new Point2D.Float(aurora2.x + aurora2.width / 2, aurora2.y + aurora2.height / 2);
        g2d.setPaint(new RadialGradientPaint(center2, aurora2.width / 2, new float[]{0f, 1f}, new Color[]{Theme.AURORA_2, new Color(0,0,0,0)}));
        g2d.fill(aurora2);

        g2d.setClip(null);

        // 4. Рисуем рамку
        g2d.setColor(Theme.BORDER_COLOR);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.draw(dialogShape);

        g2d.dispose();
    }
}