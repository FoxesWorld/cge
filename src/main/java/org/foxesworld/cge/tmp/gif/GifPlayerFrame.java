package org.foxesworld.cge.tmp.gif;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import static org.foxesworld.cge.tools.SceneCGSCreator.SceneCgsCreatorFrame.setupTheme;

public class GifPlayerFrame extends JFrame implements ActionListener {
    private final JButton openButton;
    private final JPanel contentPanel;
    private GifPlayerSwing player;

    public GifPlayerFrame() {
        super("GIF Player");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        openButton = new JButton("Открыть GIF...");
        openButton.addActionListener(this);
        add(openButton, BorderLayout.NORTH);

        contentPanel = new JPanel(new BorderLayout());
        add(contentPanel, BorderLayout.CENTER);

        setSize(600, 400);
        setLocationRelativeTo(null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                if (player != null) player.stop();
                contentPanel.removeAll();
                player = new GifPlayerSwing(file);
                contentPanel.add(player, BorderLayout.CENTER);
                contentPanel.revalidate();
                contentPanel.repaint();
                pack();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Ошибка загрузки GIF: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        setupTheme("assets/theme/calista.properties");
        SwingUtilities.invokeLater(() -> {
            GifPlayerFrame frame = new GifPlayerFrame();
            frame.setVisible(true);
        });
    }
}
