package org.foxesworld.cge.tools.SceneCreator;

import org.foxesworld.cge.core.cgs.writer.CGSWriter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

public class SceneCgsCreatorFrame extends JFrame {
    private final CGSWriter writer = new CGSWriter();
    private final ChunkListPanel listPanel = new ChunkListPanel(writer);
    private final ChunkControlsPanel controlsPanel;
    private final ChunkViewport viewport = new ChunkViewport();
    private final JTextField sceneNameField = new JTextField(20);

    public SceneCgsCreatorFrame() {
        super("CGS Scene Creator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Панель ввода имени сцены
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createTitledBorder("Scene Name"));
        topPanel.add(new JLabel("Name:"));
        topPanel.add(sceneNameField);
        add(topPanel, BorderLayout.NORTH);

        // Списки и предпросмотр чанков
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.3);
        splitPane.setLeftComponent(listPanel);

        JPanel viewPanel = new JPanel(new BorderLayout());
        viewPanel.setBorder(BorderFactory.createTitledBorder("Chunk Preview"));
        viewPanel.add(viewport, BorderLayout.CENTER);
        splitPane.setRightComponent(viewPanel);
        add(splitPane, BorderLayout.CENTER);

        controlsPanel = new ChunkControlsPanel(listPanel);
        add(controlsPanel, BorderLayout.SOUTH);

        // Обработка кнопки сохранения с учётом имени сцены
        controlsPanel.setSaveAction(new SaveAction());

        setVisible(true);
    }

    private class SaveAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String sceneName = sceneNameField.getText().trim();
            if (sceneName.isEmpty()) {
                JOptionPane.showMessageDialog(SceneCgsCreatorFrame.this,
                        "Please enter a scene name", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            writer.setSceneName(sceneName);
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save CGS Scene");
            if (chooser.showSaveDialog(SceneCgsCreatorFrame.this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".cgs")) {
                    file = new File(file.getParentFile(), file.getName() + ".cgs");
                }
                try {
                    writer.writeToFile(file);
                    JOptionPane.showMessageDialog(SceneCgsCreatorFrame.this,
                            "Scene '" + sceneName + "' saved to " + file.getName(),
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(SceneCgsCreatorFrame.this,
                            "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        SwingUtilities.invokeLater(SceneCgsCreatorFrame::new);
    }
}