package org.foxesworld.cge.tools.CGTEXcreator.preview;

import org.foxesworld.cge.tools.CGTEXcreator.info.TextureInfo;

import javax.swing.*;
import java.awt.*;

public class TextureCellRenderer extends JPanel implements ListCellRenderer<TextureInfo> {
    private final JLabel iconLabel = new JLabel();
    private final JLabel nameLabel = new JLabel();
    private final JLabel infoLabel = new JLabel();

    public TextureCellRenderer() {
        setLayout(new BorderLayout(5,5));
        JPanel textPanel = new JPanel(new GridLayout(0,1));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.PLAIN, 10f));
        infoLabel.setForeground(Color.GRAY);
        textPanel.add(nameLabel);
        textPanel.add(infoLabel);
        add(iconLabel, BorderLayout.WEST);
        add(textPanel, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends TextureInfo> list,
                                                  TextureInfo value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
        //iconLabel.setIcon(value.getThumbnailIcon());
        nameLabel.setText(value.getName());
        infoLabel.setText(String.format("%dx%d, DXT%s",
                value.getWidth(),
                value.getHeight(),
                value.getFormatCode()));
                //value.getMipMapCount()));

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }
        setOpaque(true);
        return this;
    }
}





