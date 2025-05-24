package org.foxesworld.cge.core.cgs.writer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class SceneCgsCreatorFrame extends JFrame {

    private final CGSWriter writer = new CGSWriter();
    private final DefaultListModel<String> chunkListModel = new DefaultListModel<>();

    public SceneCgsCreatorFrame() {
        super("CGS Scene Creator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(400, 300);
        setLayout(new BorderLayout());

        // Chunk list
        JList<String> chunkList = new JList<>(chunkListModel);
        add(new JScrollPane(chunkList), BorderLayout.CENTER);

        // Controls
        JPanel controls = new JPanel(new FlowLayout());

        JButton addGeometry = new JButton("Add Geometry");
        JButton addPhysics = new JButton("Add Physics");
        JButton saveBtn = new JButton("Save .cgs");

        controls.add(addGeometry);
        controls.add(addPhysics);
        controls.add(saveBtn);
        add(controls, BorderLayout.SOUTH);

        // Actions
        addGeometry.addActionListener(e -> {
            byte[] fakeGeometry = generateFakeData(128);
            int id = chunkListModel.size();
            writer.addChunk(id, 0, fakeGeometry);
            chunkListModel.addElement("Chunk " + id + " [GEOMETRY]");
        });

        addPhysics.addActionListener(e -> {
            byte[] fakePhysics = generateFakeData(64);
            int id = chunkListModel.size();
            writer.addChunk(id, 1, fakePhysics);
            chunkListModel.addElement("Chunk " + id + " [PHYSICS]");
        });

        saveBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save CGS Scene");
            int result = fileChooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                if (!selectedFile.getName().toLowerCase().endsWith(".cgs")) {
                    selectedFile = new File(selectedFile.getAbsolutePath() + ".cgs");
                }
                try {
                    writer.writeToFile(selectedFile);
                    JOptionPane.showMessageDialog(this, "Scene saved successfully!");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Failed to save file: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });


        setVisible(true);
    }

    private byte[] generateFakeData(int size) {
        byte[] data = new byte[size];
        new Random().nextBytes(data);
        return data;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SceneCgsCreatorFrame::new);
    }
}
