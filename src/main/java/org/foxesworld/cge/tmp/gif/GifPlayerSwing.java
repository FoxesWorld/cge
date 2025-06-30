package org.foxesworld.cge.tmp.gif;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;

/**
 * GifPlayerSwing: Устойчивый GIF-плеер, исправляющий баги рендеринга.
 * - Корректно обрабатывает все методы утилизации кадра (disposal methods).
 * - Учитывает смещение и размеры каждого кадра.
 * - Масштабирует итоговое изображение под размер контейнера.
 * - Использует фоновую загрузку и потокобезопасную анимацию.
 */
public class GifPlayerSwing extends JPanel {

    // Структура для хранения готового кадра анимации и его задержки
    private static class AnimationFrame {
        final BufferedImage image;
        final int delay; // в миллисекундах

        AnimationFrame(BufferedImage image, int delay) {
            this.image = image;
            this.delay = delay;
        }
    }

    private final List<AnimationFrame> animationFrames = Collections.synchronizedList(new ArrayList<>());
    private volatile int currentFrameIndex = 0;
    private volatile double speedFactor = 1.0;
    private volatile boolean isRunning = false;
    private ScheduledExecutorService executor;
    private final File gifFile;

    // Изображение, которое непосредственно рисуется на панели. Обновляется в потоке EDT.
    private volatile BufferedImage displayImage;

    public GifPlayerSwing(File gifFile) {
        this.gifFile = gifFile;
        // Установка прозрачного фона, чтобы корректно отображать GIF с прозрачностью
        setOpaque(false);
        loadInBackground();
    }

    private void loadInBackground() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (ImageInputStream stream = ImageIO.createImageInputStream(gifFile)) {
                    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
                    if (!readers.hasNext()) {
                        throw new IOException("GIF reader not found for file: " + gifFile.getName());
                    }
                    ImageReader reader = readers.next();
                    reader.setInput(stream);

                    // Получаем "логический экран" - общий размер для всех кадров
                    GifUtil.FrameMetadata firstFrameMeta = GifUtil.getFrameMetadata(reader, 0);
                    int logicalScreenWidth = firstFrameMeta.logicalScreenWidth;
                    int logicalScreenHeight = firstFrameMeta.logicalScreenHeight;

                    BufferedImage master = new BufferedImage(logicalScreenWidth, logicalScreenHeight, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D masterGraphics = master.createGraphics();

                    BufferedImage previousFrameState = null; // для disposal method 3

                    int numFrames = reader.getNumImages(true);
                    for (int i = 0; i < numFrames; i++) {
                        // 1. Получаем метаданные и изображение для ТЕКУЩЕГО кадра
                        GifUtil.FrameMetadata metadata = GifUtil.getFrameMetadata(reader, i);
                        BufferedImage frameImage = reader.read(i);

                        // 2. Сохраняем состояние холста ДО отрисовки текущего кадра, если это потребуется для СЛЕДУЮЩЕГО кадра
                        if (metadata.disposalMethod == 3) { // restoreToPrevious
                            previousFrameState = new BufferedImage(master.getColorModel(), master.copyData(null), master.isAlphaPremultiplied(), null);
                        }

                        // 3. Отрисовываем текущий кадр на холсте
                        masterGraphics.drawImage(frameImage, metadata.x, metadata.y, null);

                        // 4. Копируем результат для сохранения в списке кадров анимации
                        BufferedImage finalFrame = new BufferedImage(master.getColorModel(), master.copyData(null), master.isAlphaPremultiplied(), null);
                        animationFrames.add(new AnimationFrame(finalFrame, metadata.delay));

                        // 5. Выполняем утилизацию (disposal) ТЕКУЩЕГО кадра, подготавливая холст для СЛЕДУЮЩЕГО
                        switch (metadata.disposalMethod) {
                            case 2: // restoreToBackgroundColor: Очищаем только область, занимаемую текущим кадром
                                masterGraphics.setComposite(AlphaComposite.Clear);
                                masterGraphics.fillRect(metadata.x, metadata.y, metadata.width, metadata.height);
                                masterGraphics.setComposite(AlphaComposite.SrcOver);
                                break;
                            case 3: // restoreToPrevious: Восстанавливаем сохраненное состояние
                                if (previousFrameState != null) {
                                    master.setData(previousFrameState.getData());
                                }
                                break;
                        }

                        // 6. Устанавливаем первый кадр для немедленного отображения
                        if (i == 0) {
                            SwingUtilities.invokeLater(() -> {
                                displayImage = finalFrame;
                                revalidate();
                                repaint();
                                start(); // Запускаем анимацию после загрузки первого кадра
                            });
                        }
                    }

                    reader.dispose();
                    masterGraphics.dispose();
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Проверяем, не было ли исключений в doInBackground
                } catch (Exception e) {
                    e.printStackTrace();
                    // Можно отобразить сообщение об ошибке на панели
                    // Например: JOptionPane.showMessageDialog(GifPlayerSwing.this, "Failed to load GIF: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void scheduleNextFrame() {
        if (!isRunning || animationFrames.isEmpty()) return;
        int delay = (int) (animationFrames.get(currentFrameIndex).delay / speedFactor);
        executor.schedule(this::updateFrame, Math.max(10, delay), TimeUnit.MILLISECONDS); // Минимальная задержка 10мс
    }

    private void updateFrame() {
        currentFrameIndex = (currentFrameIndex + 1) % animationFrames.size();
        // Обновляем изображение и перерисовываем в потоке EDT
        SwingUtilities.invokeLater(() -> {
            displayImage = animationFrames.get(currentFrameIndex).image;
            repaint();
            scheduleNextFrame(); // Планируем следующий кадр после перерисовки текущего
        });
    }

    public synchronized void start() {
        if (isRunning || animationFrames.isEmpty()) return;
        isRunning = true;
        // Создаем новый исполнитель при каждом старте
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GifPlayerSwing-AnimationThread");
            t.setDaemon(true);
            return t;
        });
        // Начинаем цикл анимации
        scheduleNextFrame();
    }

    public synchronized void stop() {
        isRunning = false;
        if (executor != null) {
            executor.shutdownNow(); // Немедленно останавливаем все задачи
            executor = null;
        }
    }

    public void setSpeedFactor(double factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Speed factor must be positive.");
        }
        this.speedFactor = factor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (displayImage != null) {
            // Масштабируем изображение, чтобы оно вписывалось в размеры панели
            g.drawImage(displayImage, 0, 0, getWidth(), getHeight(), this);
        }
    }



    @Override
    public Dimension getPreferredSize() {
        if (displayImage != null) {
            return new Dimension(displayImage.getWidth(), displayImage.getHeight());
        }
        return new Dimension(100, 100); // Размер по умолчанию
    }
}


/**
 * Вспомогательный класс для извлечения метаданных из кадра GIF.
 * Использует ручной обход узлов для максимальной совместимости.
 */
class GifUtil {

    // Структура для хранения всех необходимых метаданных кадра
    static class FrameMetadata {
        final int delay;
        final int disposalMethod;
        final int x, y, width, height;
        final int logicalScreenWidth, logicalScreenHeight;

        FrameMetadata(int delay, int disposal, int x, int y, int w, int h, int lsw, int lsh) {
            this.delay = delay;
            this.disposalMethod = disposal;
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            this.logicalScreenWidth = lsw;
            this.logicalScreenHeight = lsh;
        }
    }

    /**
     * Рекурсивно ищет узел по имени, начиная с родительского узла.
     * @param parentNode Родительский узел для начала поиска.
     * @param nodeName Имя искомого узла.
     * @return Найденный узел или null, если узел не найден.
     */
    private static Node findNode(Node parentNode, String nodeName) {
        if (parentNode.getNodeName().equals(nodeName)) {
            return parentNode;
        }
        NodeList children = parentNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            Node found = findNode(child, nodeName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static FrameMetadata getFrameMetadata(ImageReader reader, int frameIndex) throws IOException {
        IIOMetadata metadata = reader.getImageMetadata(frameIndex);
        String formatName = metadata.getNativeMetadataFormatName();
        Node root = metadata.getAsTree(formatName);

        // --- Ищем необходимые узлы вручную ---
        Node graphicControlExtensionNode = findNode(root, "GraphicControlExtension");
        Node imageDescriptorNode = findNode(root, "ImageDescriptor");
        Node logicalScreenDescriptorNode = findNode(root, "LogicalScreenDescriptor");

        // --- Извлечение размеров логического экрана ---
        int logicalScreenWidth = 0;
        int logicalScreenHeight = 0;
        if (logicalScreenDescriptorNode != null) {
            NamedNodeMap attributes = logicalScreenDescriptorNode.getAttributes();
            logicalScreenWidth = Integer.parseInt(attributes.getNamedItem("logicalScreenWidth").getNodeValue());
            logicalScreenHeight = Integer.parseInt(attributes.getNamedItem("logicalScreenHeight").getNodeValue());
        }

        // --- Извлечение данных о текущем кадре ---
        int delay = 100; // Значение по умолчанию
        int disposal = 0;  // Значение по умолчанию
        if (graphicControlExtensionNode != null) {
            NamedNodeMap attributes = graphicControlExtensionNode.getAttributes();
            delay = Integer.parseInt(attributes.getNamedItem("delayTime").getNodeValue()) * 10;
            String disposalMethodStr = attributes.getNamedItem("disposalMethod").getNodeValue();
            disposal = switch (disposalMethodStr) {
                case "restoreToBackgroundColor" -> 2;
                case "restoreToPrevious" -> 3;
                case "doNotDispose" -> 1;
                default -> 0; // unspecified
            };
        }

        int x = 0, y = 0, width = 0, height = 0;
        if (imageDescriptorNode != null) {
            NamedNodeMap attributes = imageDescriptorNode.getAttributes();
            x = Integer.parseInt(attributes.getNamedItem("imageLeftPosition").getNodeValue());
            y = Integer.parseInt(attributes.getNamedItem("imageTopPosition").getNodeValue());
            width = Integer.parseInt(attributes.getNamedItem("imageWidth").getNodeValue());
            height = Integer.parseInt(attributes.getNamedItem("imageHeight").getNodeValue());
        }

        // Если логический экран не был найден (маловероятно для GIF), используем размеры первого кадра
        if (logicalScreenWidth == 0) logicalScreenWidth = width;
        if (logicalScreenHeight == 0) logicalScreenHeight = height;

        return new FrameMetadata(delay, disposal, x, y, width, height, logicalScreenWidth, logicalScreenHeight);
    }

    // Внутри класса org.foxesworld.cge.tmp.gif.GifPlayerSwing
}