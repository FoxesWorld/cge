package org.foxesworld.cge.tools.CGTEXcreator.preview;

import org.foxesworld.cge.tools.CGTEXcreator.TextureInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PreviewCell extends JPanel {
    public PreviewCell(TextureInfo ti) {
        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(200, 250));
        setBackground(new Color(30, 30, 30));  // Темный фон для панели
        setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 2, true));  // Границы с мягким радиусом

        // Название текстуры
        JLabel name = new JLabel(ti.getFile().getName(), SwingConstants.CENTER);
        name.setFont(new Font("Arial", Font.BOLD, 14));
        name.setForeground(Color.WHITE);
        name.setOpaque(false);
        add(name, BorderLayout.NORTH);

        // Превью изображения
        BufferedImage img = ti.getPreviewImage();
        ImageIcon icon = new ImageIcon(img.getScaledInstance(180, -1, Image.SCALE_SMOOTH));  // Увеличиваем размер превью
        JLabel preview = new JLabel(icon);
        preview.setHorizontalAlignment(SwingConstants.CENTER);
        preview.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true));  // Добавляем светлую границу вокруг изображения
        preview.setBackground(Color.BLACK);
        preview.setOpaque(true);
        add(preview, BorderLayout.CENTER);

        // Информация о текстуре
        String infoText = String.format("<html><font color='#8e8e8e'>DXT%d</font><br/><font color='#8e8e8e'>%dx%d</font></html>",
                ti.getFormatCode(), ti.getWidth(), ti.getHeight());
        JLabel infoLabel = new JLabel(infoText, SwingConstants.CENTER);
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        infoLabel.setForeground(Color.WHITE);
        add(infoLabel, BorderLayout.SOUTH);

        // Плавный эффект при наведении мыши
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                setBackground(new Color(50, 50, 50));  // Меняем фон при наведении
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                setBackground(new Color(30, 30, 30));  // Восстанавливаем исходный фон
            }
        });
    }
}
