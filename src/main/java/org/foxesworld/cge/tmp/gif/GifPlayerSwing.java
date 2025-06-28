package org.foxesworld.cge.tmp.gif;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * GifPlayerSwing: устойчивый GIF-плеер с масштабированием под размер контейнера,
 * фоновая загрузка, корректная прозрачность, скорость и disposal.
 */
public class GifPlayerSwing extends JPanel {
    private final List<BufferedImage> frames = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> delays = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> disposals = Collections.synchronizedList(new ArrayList<>());
    private volatile int currentFrame = 0;
    private volatile double speedFactor = 1.0;
    private volatile boolean isRunning = false;

    private ScheduledExecutorService executor;
    private final File gifFile;
    private BufferedImage displayImage;

    public GifPlayerSwing(File gifFile) {
        this.gifFile = gifFile;
        setBackground(Color.BLACK);
        loadInBackground();
    }

    private void loadInBackground() {
        new SwingWorker<Void, BufferedImage>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (ImageInputStream stream = ImageIO.createImageInputStream(gifFile)) {
                    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
                    if (!readers.hasNext()) throw new IOException("GIF reader not found");
                    ImageReader reader = readers.next();
                    reader.setInput(stream);

                    BufferedImage master = null;
                    BufferedImage prevMaster = null;
                    Graphics2D mg = null;
                    int count = reader.getNumImages(true);

                    for (int i = 0; i < count; i++) {
                        BufferedImage frame;
                        try {
                            frame = reader.read(i);
                        } catch (IOException e) {
                            e.printStackTrace();
                            continue;
                        }
                        var meta = reader.getImageMetadata(i);
                        int delay = GifUtil.getDelayTime(meta);
                        int disp = GifUtil.getDisposalMethod(meta);
                        delays.add(delay);
                        disposals.add(disp);

                        if (i == 0) {
                            master = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                            mg = master.createGraphics();
                            mg.setComposite(AlphaComposite.Src);
                            mg.setColor(new Color(0, 0, 0, 0));
                            mg.fillRect(0, 0, master.getWidth(), master.getHeight());
                        }

                        // Сохраняем мастер до изменений, если потребуется восстановление
                        BufferedImage restoreCopy = null;
                        if (disp == 3 && master != null) {
                            restoreCopy = new BufferedImage(master.getWidth(), master.getHeight(), BufferedImage.TYPE_INT_ARGB);
                            Graphics2D g = restoreCopy.createGraphics();
                            g.drawImage(master, 0, 0, null);
                            g.dispose();
                        }

                        if (i > 0) {
                            int prevDisp = disposals.get(i - 1);
                            switch (prevDisp) {
                                case 2 -> {
                                    mg.setComposite(AlphaComposite.Clear);
                                    mg.fillRect(0, 0, master.getWidth(), master.getHeight());
                                    mg.setComposite(AlphaComposite.SrcOver);
                                }
                                case 3 -> {
                                    if (prevMaster != null) {
                                        mg.setComposite(AlphaComposite.Src);
                                        mg.drawImage(prevMaster, 0, 0, null);
                                        mg.setComposite(AlphaComposite.SrcOver);
                                    }
                                }
                            }
                        }

                        mg.drawImage(frame, 0, 0, null);

                        BufferedImage copy = new BufferedImage(master.getWidth(), master.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D gc = copy.createGraphics();
                        gc.drawImage(master, 0, 0, null);
                        gc.dispose();
                        frames.add(copy);

                        prevMaster = restoreCopy;

                        if (i == 0) {
                            displayImage = copy;
                            SwingUtilities.invokeLater(() -> {
                                revalidate();
                                repaint();
                                start();
                            });
                        }
                    }
                    reader.dispose();
                    if (mg != null) mg.dispose();
                }
                return null;
            }
        }.execute();
    }

    private void scheduleNext() {
        if (!isRunning || frames.isEmpty()) return;
        int delay = (int) (delays.get(currentFrame) / speedFactor);
        executor.schedule(this::updateFrame, Math.max(1, delay), TimeUnit.MILLISECONDS);
    }

    private void updateFrame() {
        currentFrame = (currentFrame + 1) % frames.size();
        displayImage = frames.get(currentFrame);
        SwingUtilities.invokeLater(this::repaint);
        scheduleNext();
    }

    public synchronized void start() {
        if (isRunning || frames.isEmpty()) return;
        isRunning = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GifPlayerSwing"); t.setDaemon(true); return t;
        });
        scheduleNext();
    }

    public synchronized void stop() {
        isRunning = false;
        if (executor != null) executor.shutdownNow();
    }

    public void setSpeedFactor(double factor) {
        if (factor <= 0) throw new IllegalArgumentException("Speed factor must be >0");
        speedFactor = factor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (displayImage != null) {
            int panelW = getWidth();
            int panelH = getHeight();
            g.drawImage(displayImage, 0, 0, panelW, panelH, null);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (!frames.isEmpty()) {
            BufferedImage img = frames.get(0);
            return new Dimension(img.getWidth(), img.getHeight());
        }
        return super.getPreferredSize();
    }
}

class GifUtil {
    static int getDelayTime(javax.imageio.metadata.IIOMetadata metadata) {
        String fmt = metadata.getNativeMetadataFormatName();
        var root = metadata.getAsTree(fmt);
        var nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            var n = nodes.item(i);
            if ("GraphicControlExtension".equals(n.getNodeName())) {
                var attrs = n.getAttributes();
                return Integer.parseInt(attrs.getNamedItem("delayTime").getNodeValue()) * 10;
            }
        }
        return 100;
    }

    static int getDisposalMethod(javax.imageio.metadata.IIOMetadata metadata) {
        String fmt = metadata.getNativeMetadataFormatName();
        var root = metadata.getAsTree(fmt);
        var nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            var n = nodes.item(i);
            if ("GraphicControlExtension".equals(n.getNodeName())) {
                String m = n.getAttributes().getNamedItem("disposalMethod").getNodeValue();
                return switch (m) {
                    case "restoreToBackgroundColor" -> 2;
                    case "restoreToPrevious" -> 3;
                    case "doNotDispose" -> 1;
                    default -> 0;
                };
            }
        }
        return 0;
    }
}