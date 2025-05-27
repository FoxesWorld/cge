package org.foxesworld.cge.tools.SceneCreator;

import org.foxesworld.cge.core.cgs.ChunkType;
import org.foxesworld.cge.core.cgs.writer.CGSFileWriter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.function.Consumer;

/**
 * Панель для отображения списка чанков с поддержкой контекстного меню и прослушки выбора.
 */
public class ChunkListPanel extends JPanel {
    private CGSFileWriter writer;
    private final DefaultListModel<String> chunkListModel = new DefaultListModel<>();
    private final JList<String> chunkList = new JList<>(chunkListModel);
    private final JPopupMenu contextMenu = new JPopupMenu();
    private final Pattern entryPattern = Pattern.compile("\\[(\\d+)]\\s+(\\w+)\\s+\\((\\d+) bytes\\)");

    private final List<Consumer<Integer>> selectionListeners = new ArrayList<>();

    public ChunkListPanel() {
        setLayout(new BorderLayout());

        JScrollPane scroll = new JScrollPane(chunkList);
        scroll.setBorder(BorderFactory.createTitledBorder("Chunks"));
        add(scroll, BorderLayout.CENTER);

        initContextMenu();
        bindListEvents();

        chunkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chunkList.setEnabled(false); // включается после setWriter
    }

    private void bindListEvents() {
        // Клик для отображения превью по выбору
        chunkList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = chunkList.getSelectedIndex();
                // Уведомляем слушателей (Frame, например)
                selectionListeners.forEach(l -> l.accept(idx));
            }
        });

        // Контекстное меню по правому клику
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
    }

    private void initContextMenu() {
        JMenuItem editItem   = new JMenuItem("Edit Chunk");
        JMenuItem removeItem = new JMenuItem("Remove Chunk");
        JMenuItem specItem   = new JMenuItem("View Spec");

        editItem.addActionListener(e -> editSelectedChunk());
        removeItem.addActionListener(e -> removeSelectedChunk());
        specItem.addActionListener(e -> showSpecForSelectedChunk());

        contextMenu.add(editItem);
        contextMenu.add(removeItem);
        contextMenu.addSeparator();
        contextMenu.add(specItem);
    }

    /**
     * Добавляет слушатель выбора элемента (передаётся индекс в модели, или -1 при снятии выбора).
     */
    public void addChunkSelectionListener(Consumer<Integer> listener) {
        selectionListeners.add(listener);
    }

    /**
     * Визуально добавляет элемент в список.
     */
    public void addChunkVisual(ChunkType type, String subtype, Map<String, Object> attributes) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(attributes.getOrDefault("id", chunkListModel.size())).append(']')
                .append(' ').append(type).append('/').append(subtype)
                .append(" (" + estimateSize(attributes) + " bytes)");
        chunkListModel.addElement(sb.toString());
    }

    private int estimateSize(Map<String, Object> attrs) {
        // Простой подсчёт байт (можно заменить реальным)
        return attrs.toString().getBytes().length;
    }

    private void editSelectedChunk() {
        int idx = chunkList.getSelectedIndex();
        if (idx >= 0) {
            // TODO: реализовать логику редактирования
            JOptionPane.showMessageDialog(this,
                    "Edit not implemented yet for index: " + idx,
                    "Edit Chunk", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void removeSelectedChunk() {
        int idx = chunkList.getSelectedIndex();
        if (idx >= 0) {
            chunkListModel.remove(idx);
            if (writer != null) writer.removeChunk(idx);
            // Уведомляем, что выбор сброшен
            selectionListeners.forEach(l -> l.accept(-1));
        }
    }

    private void showSpecForSelectedChunk() {
        int idx = chunkList.getSelectedIndex();
        if (idx >= 0 && writer != null) {
            String spec = writer.getChunkSpec(idx);
            JOptionPane.showMessageDialog(this,
                    spec != null ? spec : "No spec available.",
                    "Chunk Spec", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void setWriter(CGSFileWriter writer) {
        this.writer = writer;
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