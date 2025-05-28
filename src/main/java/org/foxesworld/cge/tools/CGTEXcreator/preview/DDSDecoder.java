package org.foxesworld.cge.tools.CGTEXcreator.preview;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DDSDecoder {

    /**
     * Декодирует DXT1 (formatCode=1) или DXT5 (5) данные в BufferedImage.
     * Если формат не поддержан, возвращает одноцветное изображение.
     */
    public static BufferedImage decode(int width, int height, byte formatCode, byte[] data) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (formatCode == 1) {
            decodeDXT1(width, height, data, img);
        } else {
            // для DXT3/5 можно добавить аналогичные декодеры
            // пока — просто чёрный фон
            Graphics2D g = img.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
            g.dispose();
        }
        return img;
    }

    private static void decodeDXT1(int w, int h, byte[] src, BufferedImage img) {
        ByteBuffer buf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
        int blocksWide = (w + 3) / 4;
        int blocksHigh= (h + 3) / 4;

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
                        int x = bx*4 + col;
                        int y = by*4 + row;
                        if (x < w && y < h) {
                            img.setRGB(x, y, colors[idx]);
                        }
                    }
                }
            }
        }
    }

    private static int rgb565(int v) {
        int r = ((v >>> 11) & 0x1F) << 3;
        int g = ((v >>> 5)  & 0x3F) << 2;
        int b = (v & 0x1F) << 3;
        return 0xFF000000 | (r<<16) | (g<<8) | b;
    }

    private static int interp(int c0, int c1, int w0, int w1) {
        int a0 = (c0 >> 24) & 0xFF, r0 = (c0>>16)&0xFF, g0=(c0>>8)&0xFF, b0=c0&0xFF;
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1>>16)&0xFF, g1=(c1>>8)&0xFF, b1=c1&0xFF;
        int wsum = w0 + w1;
        int a = (w0*a0 + w1*a1)/wsum;
        int r = (w0*r0 + w1*r1)/wsum;
        int g = (w0*g0 + w1*g1)/wsum;
        int b = (w0*b0 + w1*b1)/wsum;
        return (a<<24)|(r<<16)|(g<<8)|b;
    }
}