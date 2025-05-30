package org.foxesworld.cge.tools.CGTEXcreator.preview;

import org.foxesworld.cge.tools.CGTEXcreator.info.TextureInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PreviewCell extends JPanel {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 250;
    private static final Color BACKGROUND_COLOR = new Color(30, 30, 30);
    private static final Color BORDER_COLOR = new Color(70, 70, 70);
    private static final Color INFO_TEXT_COLOR = new Color(130, 130, 130);

    public PreviewCell(TextureInfo ti) {
        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(BACKGROUND_COLOR);
        setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 2, true));
        JLabel previewLabel = createPreviewLabel(ti);
        add(previewLabel, BorderLayout.CENTER);

        // Добавляем плавный эффект при наведении мыши
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                setBackground(new Color(50, 50, 50));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                setBackground(BACKGROUND_COLOR);
            }
        });
    }

    private JLabel createPreviewLabel(TextureInfo ti) {
        BufferedImage img = ti.getPreviewImage();
        ImageIcon icon = new ImageIcon(img.getScaledInstance(180, -1, Image.SCALE_SMOOTH));
        JLabel previewLabel = new JLabel(icon);
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true));
        previewLabel.setBackground(Color.BLACK);
        previewLabel.setOpaque(true);
        return previewLabel;
    }

    private JPanel createInfoPanel(TextureInfo ti) {
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel typeLabel = new JLabel("Format: DXT" + ti.getFormatCode());
        typeLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        typeLabel.setForeground(INFO_TEXT_COLOR);
        infoPanel.add(typeLabel);

        JLabel sizeLabel = new JLabel(String.format("Size: %dx%d", ti.getWidth(), ti.getHeight()));
        sizeLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        sizeLabel.setForeground(INFO_TEXT_COLOR);
        infoPanel.add(sizeLabel);

        //JLabel fileSizeLabel = new JLabel(String.format("File Size: %.2f MB", ti.getFile().length() / 1024.0 / 1024.0));
        //fileSizeLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        //fileSizeLabel.setForeground(INFO_TEXT_COLOR);
        //infoPanel.add(fileSizeLabel);

        return infoPanel;
    }
}
