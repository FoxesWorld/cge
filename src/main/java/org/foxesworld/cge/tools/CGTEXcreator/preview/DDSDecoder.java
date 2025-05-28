package org.foxesworld.cge.tools.CGTEXcreator.preview;

import org.foxesworld.cge.tmp.TextureLoader;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

public final class DDSDecoder {

    /**
     * Декодирует DXT1, DXT3 или DXT5 данные в BufferedImage.
     * Если формат не поддержан, возвращает одноцветное изображение.
     */
    private static final Logger logger = Logger.getLogger(DDSDecoder.class.getName());
    public static BufferedImage decode(int width, int height, byte formatCode, byte[] data) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        switch (formatCode) {
            case 1 -> decodeDXT1(width, height, data, img);
            case 3 -> decodeDXT3(width, height, data, img);
            case 5 -> decodeDXT5(width, height, data, img);
            default -> {
                Graphics2D g = img.createGraphics();
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, width, height);

                // Настройки сетки
                int gridSize = 20; // Размер ячеек сетки
                for (int x = 0; x < width; x += gridSize) {
                    for (int y = 0; y < height; y += gridSize) {
                        // Чередование цветов черный и фиолетовый для сетки
                        if ((x + y) % 2 == 0) {
                            g.setColor(new Color(128, 0, 128)); // Фиолетовый
                        } else {
                            g.setColor(Color.BLACK); // Черный
                        }
                        g.drawLine(x, 0, x, height); // Вертикальные линии
                        g.drawLine(0, y, width, y);  // Горизонтальные линии
                    }
                }
                g.dispose();
            }

        }
        return img;
    }

    // Декодирует DXT1 данные
    private static void decodeDXT1(int w, int h, byte[] src, BufferedImage img) {
        ByteBuffer buf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
        int blocksWide = (w + 3) / 4;
        int blocksHigh = (h + 3) / 4;

        for (int by = 0; by < blocksHigh; by++) {
            for (int bx = 0; bx < blocksWide; bx++) {
                int c0 = buf.getShort() & 0xFFFF;
                int c1 = buf.getShort() & 0xFFFF;
                int[] colors = new int[4];
                colors[0] = rgb565(c0);
                colors[1] = rgb565(c1);
                if (c0 > c1) {
                    colors[2] = interp(colors[0], colors[1], 2, 1);
                    colors[3] = interp(colors[0], colors[1], 1, 2);
                } else {
                    colors[2] = interp(colors[0], colors[1], 1, 1);
                    colors[3] = 0x00000000; // полностью прозрачный
                }
                int bits = buf.getInt();
                for (int row = 0; row < 4; row++) {
                    for (int col = 0; col < 4; col++) {
                        int idx = bits & 0x3;
                        bits >>>= 2;
                        int x = bx * 4 + col;
                        int y = by * 4 + row;
                        if (x < w && y < h) {
                            img.setRGB(x, y, colors[idx]);
                        }
                    }
                }
            }
        }
    }

    // Декодирует DXT3 данные (с альфа-каналом)
    private static void decodeDXT3(int w, int h, byte[] src, BufferedImage img) {
        ByteBuffer buf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
        int blocksWide = (w + 3) / 4;
        int blocksHigh = (h + 3) / 4;

        for (int by = 0; by < blocksHigh; by++) {
            for (int bx = 0; bx < blocksWide; bx++) {
                int alpha1 = buf.getShort() & 0xFFFF;
                int alpha2 = buf.getShort() & 0xFFFF;
                int[] alpha = new int[8];
                for (int i = 0; i < 8; i++) {
                    alpha[i] = (i < 4) ? (alpha1 >> (i * 4)) & 0xF : (alpha2 >> ((i - 4) * 4)) & 0xF;
                    alpha[i] = (alpha[i] << 4) | (alpha[i] & 0xF);
                }
                int c0 = buf.getShort() & 0xFFFF;
                int c1 = buf.getShort() & 0xFFFF;
                int[] colors = new int[4];
                colors[0] = rgb565(c0);
                colors[1] = rgb565(c1);
                if (c0 > c1) {
                    colors[2] = interp(colors[0], colors[1], 2, 1);
                    colors[3] = interp(colors[0], colors[1], 1, 2);
                } else {
                    colors[2] = interp(colors[0], colors[1], 1, 1);
                    colors[3] = 0x00000000; // полностью прозрачный
                }
                int bits = buf.getInt();
                for (int row = 0; row < 4; row++) {
                    for (int col = 0; col < 4; col++) {
                        int idx = bits & 0x3;
                        bits >>>= 2;
                        int x = bx * 4 + col;
                        int y = by * 4 + row;
                        if (x < w && y < h) {
                            img.setRGB(x, y, (alpha[idx] << 24) | colors[idx]);
                        }
                    }
                }
            }
        }
    }

    // Декодирует DXT5 данные (с альфа-каналом)
    private static void decodeDXT5(int w, int h, byte[] src, BufferedImage img) {
        ByteBuffer buf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
        int blocksWide = (w + 3) / 4;
        int blocksHigh = (h + 3) / 4;

        for (int by = 0; by < blocksHigh; by++) {
            for (int bx = 0; bx < blocksWide; bx++) {
                int alpha0 = buf.get();
                int alpha1 = buf.get();
                int[] alpha = new int[8];
                alpha[0] = alpha0;
                alpha[1] = alpha1;
                if (alpha0 > alpha1) {
                    for (int i = 2; i < 8; i++) {
                        alpha[i] = (alpha0 * (8 - i) + alpha1 * (i)) / 7;
                    }
                } else {
                    for (int i = 2; i < 8; i++) {
                        alpha[i] = (alpha0 * (7 - i) + alpha1 * (i)) / 7;
                    }
                }
                int c0 = buf.getShort() & 0xFFFF;
                int c1 = buf.getShort() & 0xFFFF;
                int[] colors = new int[4];
                colors[0] = rgb565(c0);
                colors[1] = rgb565(c1);
                if (c0 > c1) {
                    colors[2] = interp(colors[0], colors[1], 2, 1);
                    colors[3] = interp(colors[0], colors[1], 1, 2);
                } else {
                    colors[2] = interp(colors[0], colors[1], 1, 1);
                    colors[3] = 0x00000000; // полностью прозрачный
                }
                int bits = buf.getInt();
                for (int row = 0; row < 4; row++) {
                    for (int col = 0; col < 4; col++) {
                        int idx = bits & 0x3;
                        bits >>>= 2;
                        int x = bx * 4 + col;
                        int y = by * 4 + row;
                        if (x < w && y < h) {
                            img.setRGB(x, y, (alpha[idx] << 24) | colors[idx]);
                        }
                    }
                }
            }
        }
    }

    // Преобразует значение RGB565 в ARGB
    private static int rgb565(int v) {
        int r = ((v >>> 11) & 0x1F) << 3;
        int g = ((v >>> 5) & 0x3F) << 2;
        int b = (v & 0x1F) << 3;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // Интерполирует два цвета
    private static int interp(int c0, int c1, int w0, int w1) {
        int a0 = (c0 >> 24) & 0xFF, r0 = (c0 >> 16) & 0xFF, g0 = (c0 >> 8) & 0xFF, b0 = c0 & 0xFF;
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int wsum = w0 + w1;
        int a = (w0 * a0 + w1 * a1) / wsum;
        int r = (w0 * r0 + w1 * r1) / wsum;
        int g = (w0 * g0 + w1 * g1) / wsum;
        int b = (w0 * b0 + w1 * b1) / wsum;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}