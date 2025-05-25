package org.foxesworld.cge.tools.SceneCreator;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import org.foxesworld.cge.core.cgs.writer.CGSFileWriter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class SceneCgsCreatorFrame extends JFrame {
    private CGSFileWriter writer;
    private ChunkListPanel listPanel;
    private ChunkControlsPanel controlsPanel;
    private final ChunkViewport viewport = new ChunkViewport();
    private final JTextField sceneNameField = new JTextField(20);

    public SceneCgsCreatorFrame() {
        super("CGS Scene Creator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Выбор файла сцены в начале
        File file = promptForFile();
        if (file == null) {
            dispose();
            return;
        }
        writer = new CGSFileWriter(file);

        // Инициализация панелей
        listPanel = new ChunkListPanel();
        controlsPanel = new ChunkControlsPanel(listPanel);
        listPanel.setWriter(writer);
        controlsPanel.setWriter(writer);

        // Панель ввода имени сцены
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createTitledBorder("Scene Name"));
        topPanel.add(new JLabel("Name:"));
        topPanel.add(sceneNameField);
        add(topPanel, BorderLayout.NORTH);

        // Раздел UI на список и предпросмотр
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.3);
        splitPane.setLeftComponent(listPanel);
        JPanel viewPanel = new JPanel(new BorderLayout());
        viewPanel.setBorder(BorderFactory.createTitledBorder("Chunk Preview"));
        viewPanel.add(viewport, BorderLayout.CENTER);
        splitPane.setRightComponent(viewPanel);
        add(splitPane, BorderLayout.CENTER);

        add(controlsPanel, BorderLayout.SOUTH);
        // Сохранять сцену: теперь просто пишет в ранее выбранный файл
        controlsPanel.setSaveAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String sceneName = sceneNameField.getText().trim();
                if (sceneName.isEmpty()) {
                    JOptionPane.showMessageDialog(SceneCgsCreatorFrame.this,
                            "Please enter a scene name", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                writer.setSceneName(sceneName);
                try {
                    writer.writeToFile();
                    JOptionPane.showMessageDialog(SceneCgsCreatorFrame.this,
                            "Scene '" + sceneName + "' saved to " + file.getName(),
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(SceneCgsCreatorFrame.this,
                            "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });

        setVisible(true);
    }

    /**
     * Показывает диалог выбора файла и возвращает файл с расширением .cgs или null при отмене
     */
    private File promptForFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select CGS Scene File");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".cgs")) {
            file = new File(file.getParentFile(), file.getName() + ".cgs");
        }
        return file;
    }

    public static void main(String[] args) {
        System.setProperty("log.dir", System.getProperty("user.dir"));
        System.setProperty("log.level", "DEBUG");
        setupTheme("theme/calista.properties");
        SwingUtilities.invokeLater(SceneCgsCreatorFrame::new);
    }

    public static void setupTheme(String theme) {
        try {
            InputStream themeStream = SceneCgsCreatorFrame.class.getClassLoader().getResourceAsStream(theme);

            if(themeStream == null) {
                throw new RuntimeException("Theme file not found in resources");
            }

            FlatPropertiesLaf laf = new FlatPropertiesLaf("Dark Theme", themeStream);
            FlatLaf.setup(laf);

        } catch(Exception ex) {
            // Fallback на стандартную темную тему
            FlatLaf.setup(new FlatDarkLaf());
            ex.printStackTrace();
        }

    }
}