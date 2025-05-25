package org.foxesworld.cge.tools.SceneCreator;

import org.foxesworld.cge.core.cgs.writer.CGSFileWriter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.Pattern;

public class ChunkListPanel extends JPanel {
    private CGSFileWriter writer;
    private final DefaultListModel<String> chunkListModel = new DefaultListModel<>();
    private final JList<String> chunkList = new JList<>(chunkListModel);
    private final JPopupMenu contextMenu = new JPopupMenu();
    private final Pattern entryPattern = Pattern.compile("\\[(\\d+)]\\s+(\\w+)\\s+\\((\\d+) bytes\\)");

    public ChunkListPanel() {
        // Writer is set later via setWriter
        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(chunkList);
        scroll.setBorder(BorderFactory.createTitledBorder("Chunks"));
        add(scroll, BorderLayout.CENTER);

        // Context menu setup
        JMenuItem editItem = new JMenuItem("Edit Chunk");
        JMenuItem removeItem = new JMenuItem("Remove Chunk");
        JMenuItem specItem = new JMenuItem("View Spec");
        contextMenu.add(editItem);
        contextMenu.add(removeItem);
        contextMenu.addSeparator();
        contextMenu.add(specItem);

        chunkList.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showMenu(e); }
            private void showMenu(MouseEvent e) {
                int idx = chunkList.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    chunkList.setSelectedIndex(idx);
                    contextMenu.show(chunkList, e.getX(), e.getY());
                }
            }
        });

        // Disable interactions until writer is set
        chunkList.setEnabled(false);
    }

    public void setWriter(CGSFileWriter writer) {
        this.writer = writer;
        // Enable list once writer is available
        chunkList.setEnabled(true);
    }

    public CGSFileWriter getWriter() {
        return writer;
    }

    public DefaultListModel<String> getChunkListModel() {
        return chunkListModel;
    }

    public JList<String> getChunkList() {
        return chunkList;
    }

    public Pattern getEntryPattern() {
        return entryPattern;
    }
}
