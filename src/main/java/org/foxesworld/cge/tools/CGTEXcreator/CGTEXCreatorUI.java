package org.foxesworld.cge.tools.CGTEXcreator;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import org.foxesworld.cge.ICOParser;
import org.foxesworld.cge.core.file.cgtex.writer.CGTEXFileWriter;
import org.foxesworld.cge.tools.CGTEXcreator.info.TextureInfo;
import org.foxesworld.cge.tools.CGTEXcreator.preview.DDSParser;
import org.foxesworld.cge.tools.CGTEXcreator.preview.PreviewCell;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class CGTEXCreatorUI extends JFrame {
    private final List<TextureInfo> textures = new ArrayList<>();
    private final DefaultListModel<File> listModel = new DefaultListModel<>();
    private final JList<File> fileList = new JList<>(listModel);
    private final JPanel previewPanel = new JPanel(new BorderLayout());

    public CGTEXCreatorUI() {
        super("CGTEX Creator");

        try (InputStream icoStream = CGTEXCreatorUI.class.getClassLoader().getResourceAsStream("theme/icon/textureEditor.ico")) {
            if (icoStream != null) {
                ICOParser parser = new ICOParser();
                List<BufferedImage> iconsList = parser.parse(icoStream);  // <- вызов parse(InputStream)
                BufferedImage bestIcon = parser.getBestIcon(iconsList);
                if (bestIcon != null) {
                    setIconImages(List.of(bestIcon));
                }
            } else {
                System.err.println("ICO icon resource not found");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        initUI();
    }

    private void initUI() {
        // --- File list panel ---
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);  // Only allow single selection
        JScrollPane listScroll = new JScrollPane(fileList);
        listScroll.setBorder(new TitledBorder("DDS Files"));

        fileList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    File selectedFile = fileList.getSelectedValue();
                    if (selectedFile != null) {
                        updatePreview(selectedFile);
                    }
                }
            }
        });

        JButton addBtn = new JButton("Add DDS");
        JButton remBtn = new JButton("Remove");
        addBtn.addActionListener(e -> onAdd());
        remBtn.addActionListener(e -> onRemove());
        JPanel btns = new JPanel();
        btns.add(addBtn);
        btns.add(remBtn);

        JPanel left = new JPanel(new BorderLayout(5,5));
        left.add(listScroll, BorderLayout.CENTER);
        left.add(btns, BorderLayout.SOUTH);

        // --- Preview panel ---
        previewPanel.setBorder(new TitledBorder("Preview"));
        JScrollPane previewScroll = new JScrollPane(previewPanel);

        // --- Save button ---
        JButton save = new JButton("Save .cgtex");
        save.addActionListener(e -> onSave());
        JPanel south = new JPanel();
        south.add(save);

        // --- Layout ---
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, previewScroll);
        split.setResizeWeight(0.3);

        getContentPane().setLayout(new BorderLayout(5,5));
        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
    }

    private void updatePreview(File selectedFile) {
        previewPanel.removeAll();

        try {
            TextureInfo textureInfo = DDSParser.parse(selectedFile);
            PreviewCell previewCell = new PreviewCell(textureInfo);  // Assuming PreviewCell handles the preview
            previewPanel.add(previewCell, BorderLayout.CENTER);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot parse DDS: " + selectedFile.getName() + "\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        previewPanel.revalidate();
        previewPanel.repaint();
    }

    private void onAdd() {
        JFileChooser c = new JFileChooser();
        c.setMultiSelectionEnabled(true);
        c.setFileFilter(new FileNameExtensionFilter("DDS", "dds"));
        if (c.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        for (File f : c.getSelectedFiles()) {
            try {
                TextureInfo ti = DDSParser.parse(f);
                textures.add(ti);
                listModel.addElement(f);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Cannot parse DDS: " + f.getName() + "\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onRemove() {
        for (File f : fileList.getSelectedValuesList()) {
            textures.removeIf(ti -> ti.getFile().equals(f));
            listModel.removeElement(f);
        }
    }

    private void onSave() {
        if (textures.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No textures", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser c = new JFileChooser();
        c.setFileFilter(new FileNameExtensionFilter("CGTEX", "cgtex"));
        if (c.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File out = c.getSelectedFile();
        if (!out.getName().endsWith(".cgtex")) out = new File(out.getParent(), out.getName() + ".cgtex");

        try (CGTEXFileWriter w = new CGTEXFileWriter(out)) {
            for (TextureInfo ti : textures) {
                w.addTexture(ti.getWidth(), ti.getHeight(), ti.getFormatCode(), ti.getData());
            }
            w.writeToFile();
            JOptionPane.showMessageDialog(this, "Saved: " + out, "OK", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Cannot save:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        FlatLaf.setup(new FlatDarkLaf());
        SwingUtilities.invokeLater(() -> new CGTEXCreatorUI().setVisible(true));
    }
}
