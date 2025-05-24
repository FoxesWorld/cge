package org.foxesworld.cge.tools.SceneCreator;

import javax.swing.*;
import java.awt.*;

public class ChunkViewport extends JPanel {
    private String info = "No Chunk Selected";

    public ChunkViewport() {
        setBackground(Color.DARK_GRAY);
    }

    public void showChunk(String description) {
        this.info = description;
        repaint();
    }

    public void clear() {
        this.info = "No Chunk Selected";
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.WHITE);
        int x = 10, y = 20;
        for (String line : info.split("\\n")) {
            g.drawString(line, x, y);
            y += 15;
        }
    }
}
