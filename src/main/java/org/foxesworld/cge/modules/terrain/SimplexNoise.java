package org.foxesworld.cge.modules.terrain;

import java.util.Random;

class SimplexNoise {
    private static final int[] perm = new int[512];
    private static final int[] permMod12 = new int[512];
    private static final int[] p = new int[256];
    private static final double SQRT3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT3 - 1.0);
    private static final double G2 = (3.0 - SQRT3) / 6.0;

    public SimplexNoise(long seed) {
        Random rand = new Random(seed);
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = perm[i] % 12;
        }
    }

    private static final double[][] grad3 = {
            {1,1,0},{-1,1,0},{1,-1,0},{-1,-1,0},
            {1,0,1},{-1,0,1},{1,0,-1},{-1,0,-1},
            {0,1,1},{0,-1,1},{0,1,-1},{0,-1,-1}
    };

    public float noise2D(double xin, double yin) {
        double n0, n1, n2; // Noise contributions from the three corners
        double s = (xin + yin) * F2;
        int i = fastfloor(xin + s);
        int j = fastfloor(yin + s);
        double t = (i + j) * G2;
        double X0 = i - t;
        double Y0 = j - t;
        double x0 = xin - X0;
        double y0 = yin - Y0;
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } // lower triangle
        else { i1 = 0; j1 = 1; }
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255;
        int jj = j & 255;
        double t0 = 0.5 - x0*x0 - y0*y0;
        if (t0 < 0) n0 = 0.0;
        else {
            t0 *= t0;
            int gi0 = permMod12[ii + perm[jj]];
            n0 = t0 * t0 * dot(grad3[gi0], x0, y0);
        }
        double t1 = 0.5 - x1*x1 - y1*y1;
        if (t1 < 0) n1 = 0.0;
        else {
            t1 *= t1;
            int gi1 = permMod12[ii + i1 + perm[jj + j1]];
            n1 = t1 * t1 * dot(grad3[gi1], x1, y1);
        }
        double t2 = 0.5 - x2*x2 - y2*y2;
        if (t2 < 0) n2 = 0.0;
        else {
            t2 *= t2;
            int gi2 = permMod12[ii + 1 + perm[jj + 1]];
            n2 = t2 * t2 * dot(grad3[gi2], x2, y2);
        }
        // Scale result to [-1,1]
        return (float)(70.0 * (n0 + n1 + n2));
    }

    private static int fastfloor(double x) {
        return x > 0 ? (int)x : (int)x - 1;
    }

    private static double dot(double[] g, double x, double y) {
        return g[0] * x + g[1] * y;
    }
}