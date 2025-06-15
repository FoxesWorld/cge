package com.jme3.awt;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatCheckBox;
import com.formdev.flatlaf.extras.components.FlatComboBox;
import com.formdev.flatlaf.extras.components.FlatLabel;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;

@Deprecated
public class CustomSettingsWindow extends JFrame {

    private FlatComboBox<String> resolutionComboBox;
    private FlatCheckBox fullscreenCheckBox;
    private Image logoImage;

    public CustomSettingsWindow() {
        try {
            logoImage = ImageIO.read(getClass().getClassLoader().getResource("theme/logo.png"));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Logo not found: " + e.getMessage());
        }

        setTitle("Calista Game Settings");
        setSize(700, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setUndecorated(true);
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 30, 30));

        JPanel mainPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                GradientPaint gp = new GradientPaint(0, 0, new Color(30, 30, 40), 0, getHeight(), new Color(50, 50, 60));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                g2.dispose();
            }
        };
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        setContentPane(mainPanel);

        // Верхняя панель с логотипом
        if (logoImage != null) {
            JLabel logoLabel = new JLabel(new ImageIcon(logoImage.getScaledInstance(getWidth() - 40, 150, Image.SCALE_SMOOTH)));
            logoLabel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(120, 160, 200, 100), 1, true),
                    new EmptyBorder(10, 10, 10, 10)
            ));
            mainPanel.add(logoLabel, BorderLayout.NORTH);
        }

        // Панель с настройками
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setOpaque(false);
        settingsPanel.setBorder(new EmptyBorder(30, 60, 30, 60));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(14, 10, 14, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Resolution
        gbc.gridx = 0;
        gbc.gridy = 0;
        FlatLabel resolutionLabel = new FlatLabel();
        resolutionLabel.setText("Resolution:");
        resolutionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        resolutionLabel.setForeground(Color.LIGHT_GRAY);
        settingsPanel.add(resolutionLabel, gbc);

        gbc.gridx = 1;
        resolutionComboBox = new FlatComboBox<>();
        resolutionComboBox.addItem("1920x1080");
        resolutionComboBox.addItem("1600x900");
        resolutionComboBox.addItem("1280x720");
        resolutionComboBox.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        settingsPanel.add(resolutionComboBox, gbc);

        // Fullscreen
        gbc.gridx = 0;
        gbc.gridy++;
        FlatLabel fullscreenLabel = new FlatLabel();
        fullscreenLabel.setText("Fullscreen");
        fullscreenLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        fullscreenLabel.setForeground(Color.LIGHT_GRAY);
        settingsPanel.add(fullscreenLabel, gbc);

        gbc.gridx = 1;
        fullscreenCheckBox = new FlatCheckBox();
        fullscreenCheckBox.setOpaque(false);
        settingsPanel.add(fullscreenCheckBox, gbc);

        mainPanel.add(settingsPanel, BorderLayout.CENTER);

        // Кнопки
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 10));
        buttonPanel.setOpaque(false);
        FlatButton applyButton = new FlatButton();
        applyButton.setText("Apply");
        applyButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        applyButton.setForeground(new Color(180, 240, 255));
        applyButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        applyButton.addActionListener(e -> applySettings());

        FlatButton closeButton = new FlatButton();
        closeButton.setText("Close");
        closeButton.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        closeButton.setForeground(new Color(255, 120, 120));
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(applyButton);
        buttonPanel.add(closeButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void applySettings() {
        String resolution = (String) resolutionComboBox.getSelectedItem();
        boolean fullscreen = fullscreenCheckBox.isSelected();

        JOptionPane.showMessageDialog(this,
                "<html><body style='font-family:Segoe UI; font-size:12px;'>" +
                        "<b>Resolution:</b> " + resolution + "<br>" +
                        "<b>Fullscreen:</b> " + (fullscreen ? "Enabled" : "Disabled") +
                        "</body></html>",
                "Settings Applied",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        FlatAnimatedLafChange.showSnapshot();
        SwingUtilities.invokeLater(() -> {
            CustomSettingsWindow window = new CustomSettingsWindow();
            window.setVisible(true);
        });
    }
}
