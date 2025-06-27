package org.foxesworld.cge.core.io.progressBar;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Swing-based progress bar frame that closes itself when loading is complete.
 */
public class StatusProgressBar extends JFrame implements ProgressListener {
    private final JProgressBar progressBar;
    private final JLabel label;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public StatusProgressBar() {
        super("Loading Assets");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setAlwaysOnTop(true);
        setResizable(false);

        label = new JLabel("Loading...");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.add(label, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    @Override
    public void onProgress(String assetType, int loaded, int total) {
        if (closed.get()) return;
        SwingUtilities.invokeLater(() -> {
            int percent = total > 0 ? (int) ((loaded * 100.0) / total) : 100;
            label.setText("Loading: " + assetType);
            progressBar.setMaximum(total);
            progressBar.setValue(loaded);
            progressBar.setString(loaded + " / " + total + " (" + percent + "%)");

            if (loaded >= total && !closed.get()) {
                closed.set(true);
                dispose();
            }
        });
    }
}