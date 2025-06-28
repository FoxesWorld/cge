package com.jme3.awt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

class ModernTitleBar extends JPanel {
    public ModernTitleBar(String title, ImageIcon icon, ActionListener closeAction) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(0, 15, 0, 8));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.FONT_BOLD);
        titleLabel.setForeground(Theme.TEXT_COLOR);
        if (icon != null) {
            titleLabel.setIcon(icon);
            titleLabel.setIconTextGap(10);
        }

        ModernButton closeButton = new ModernButton(null, ModernButton.ButtonStyle.TRANSPARENT);
        closeButton.setIcon(UIManager.getIcon("InternalFrame.closeIcon"));
        closeButton.setToolTipText("Close (Esc)");
        closeButton.addActionListener(closeAction);

        add(titleLabel, BorderLayout.WEST);
        add(closeButton, BorderLayout.EAST);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        // Create a rounded rectangle shape for the top part of the dialog
        Shape clipShape = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight() + Theme.CORNER_RADIUS, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);
        Area clipArea = new Area(clipShape);
        // Subtract a rectangle from the bottom to make the bottom edge straight
        clipArea.subtract(new Area(new Rectangle2D.Float(0, getHeight(), getWidth(), Theme.CORNER_RADIUS)));

        g2d.setClip(clipArea);
        g2d.setColor(Theme.TITLE_BAR_BG);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.setColor(Theme.BORDER_COLOR_LIGHT);
        g2d.drawLine(0, getHeight()-1, getWidth(), getHeight()-1);

        g2d.dispose();
        super.paintComponent(g);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, 42);
    }
}