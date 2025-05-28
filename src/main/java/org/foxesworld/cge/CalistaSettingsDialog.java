package org.foxesworld.cge;

import com.jme3.system.AppSettings;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

public class CalistaSettingsDialog {

    public static AppSettings show() {

        JFrame frame = new JFrame("Calista Game Engine Settings");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(460, 360);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(10, 10));

        // Логотип сверху
        try (InputStream logoStream = CalistaSettingsDialog.class.getResourceAsStream("/theme/logo.png")) {
            if (logoStream != null) {
                ImageIcon icon = new ImageIcon(ImageIO.read(logoStream));
                JLabel logoLabel = new JLabel(icon);
                logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
                frame.add(logoLabel, BorderLayout.NORTH);
            }
        } catch (IOException e) {
            System.err.println("Failed to load logo.");
        }

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        // Resolution width and height separately
        JLabel widthLabel = new JLabel("Width:");
        JTextField widthField = new JTextField("1280");
        JLabel heightLabel = new JLabel("Height:");
        JTextField heightField = new JTextField("720");
        panel.add(widthLabel);
        panel.add(widthField);
        panel.add(heightLabel);
        panel.add(heightField);

        // Fullscreen
        JLabel fullscreenLabel = new JLabel("Fullscreen:");
        JCheckBox fullscreenCheck = new JCheckBox();
        panel.add(fullscreenLabel);
        panel.add(fullscreenCheck);

        // VSync
        JLabel vsyncLabel = new JLabel("VSync:");
        JCheckBox vsyncCheck = new JCheckBox();
        panel.add(vsyncLabel);
        panel.add(vsyncCheck);

        // Anti-aliasing
        JLabel aaLabel = new JLabel("Anti-aliasing (samples):");
        Integer[] samples = {0, 2, 4, 8};
        JComboBox<Integer> aaCombo = new JComboBox<>(samples);
        aaCombo.setSelectedItem(4);
        panel.add(aaLabel);
        panel.add(aaCombo);

        // Start button
        JButton startButton = new JButton("Start Game");
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        startButton.setPreferredSize(new Dimension(140, 40));

        frame.add(panel, BorderLayout.CENTER);
        frame.add(startButton, BorderLayout.SOUTH);

        final AppSettings[] settings = new AppSettings[1];

        startButton.addActionListener(e -> {
            try {
                int width = Integer.parseInt(widthField.getText().trim());
                int height = Integer.parseInt(heightField.getText().trim());

                settings[0] = new AppSettings(true);
                settings[0].setTitle("Calista Game Engine");
                settings[0].setResolution(width, height);
                settings[0].setFullscreen(fullscreenCheck.isSelected());
                settings[0].setVSync(vsyncCheck.isSelected());
                settings[0].setSamples((Integer) aaCombo.getSelectedItem());
                settings[0].setResizable(true);
                settings[0].setFrameRate(-1);

                frame.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Invalid input: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        frame.setVisible(true);

        // Wait until user closes the dialog
        while (frame.isDisplayable()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }

        return settings[0];
    }
}