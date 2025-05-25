package com.jme3.awt;

import javax.swing.*;

public class CustomSettingsWindow extends JFrame {

    private JComboBox<String> resolutionComboBox;
    private JCheckBox fullscreenCheckBox;

    public CustomSettingsWindow() {
        setTitle("Custom App Settings");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Разрешение экрана
        String[] resolutions = {"1920x1080", "1280x720", "1024x768"};
        resolutionComboBox = new JComboBox<>(resolutions);
        panel.add(new JLabel("Screen Resolution"));
        panel.add(resolutionComboBox);

        // Режим полноэкранного отображения
        fullscreenCheckBox = new JCheckBox("Fullscreen");
        panel.add(fullscreenCheckBox);

        // Применить настройки
        JButton applyButton = new JButton("Apply Settings");
        applyButton.addActionListener(e -> applySettings());
        panel.add(applyButton);

        add(panel);
    }

    private void applySettings() {
        String resolution = (String) resolutionComboBox.getSelectedItem();
        boolean fullscreen = fullscreenCheckBox.isSelected();

        // Логика применения настроек
        System.out.println("Resolution: " + resolution + ", Fullscreen: " + fullscreen);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CustomSettingsWindow window = new CustomSettingsWindow();
            window.setVisible(true);
        });
    }
}
