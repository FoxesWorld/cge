package org.foxesworld.cge.tools.SceneCreator;

import javax.swing.*;
import java.awt.*;

public class ChunkViewport extends JScrollPane {
    private final JTextArea textArea;

    public ChunkViewport() {
        textArea = new JTextArea("No Chunk Selected");
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBackground(Color.DARK_GRAY);
        textArea.setForeground(Color.WHITE);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        setViewportView(textArea);
        // Убираем рамку у текстового поля, оставляем только рамку скроллпэна
        textArea.setBorder(null);
    }

    /**
     * Показывает описание чанка.
     * @param description multiline-текст, который автоматически перенесётся и прокрутится
     */
    public void showChunk(String description) {
        textArea.setText(description);
        // возвращаем видимую область в начало
        textArea.setCaretPosition(0);
    }

    public void clear() {
        showChunk("No Chunk Selected");
    }
}
