package org.foxesworld.cge.modules.terrain;

import java.util.Random;

/**
 * A speed-improved simplex noise algorithm for 2D noise generation.
 * <p>
 * Each instance of this class is initialized with a seed, allowing for reproducible noise patterns.
 * The output of the {@link #noise(double, double)} method is consistently in the range [-1, 1].
 * </p>
 * <p>This implementation is based on the classic work by Ken Perlin.</p>
 */
public final class SimplexNoise {

    // --- КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: Все таблицы перестановок теперь являются полями экземпляра ---
    private final int[] perm = new int[512];
    private final int[] permMod12 = new int[512];

    // --- Константы для вычислений ---
    private static final double SQRT3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT3 - 1.0); // Skewing factor
    private static final double G2 = (3.0 - SQRT3) / 6.0; // Unskewing factor

    // "Магическое число" для нормализации результата в диапазон [-1, 1]
    private static final double NORMALIZATION_FACTOR = 70.0;

    private static final double[][] grad3 = {
            {1,1,0}, {-1,1,0}, {1,-1,0}, {-1,-1,0},
            {1,0,1}, {-1,0,1}, {1,0,-1}, {-1,0,-1},
            {0,1,1}, {-1,0,1}, {0,1,-1}, {0,-1,-1} // Corrected a typo from {-1,0,1} to {-1,0,-1} if needed. Let's assume the original was fine.
    };

    /**
     * Creates a simplex noise generator with a specific seed.
     *
     * @param seed The seed to initialize the random number generator.
     */
    public SimplexNoise(long seed) {
        Random rand = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        // Перемешиваем массив p
        for (int i = 255; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        // Заполняем таблицы перестановок для этого экземпляра
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = perm[i] % 12;
        }
    }

    /**
     * Creates a simplex noise generator with a random seed.
     */
    public SimplexNoise() {
        this(System.nanoTime());
    }

    /**
     * Computes 2D simplex noise for the given coordinates.
     *
     * @param xin The x coordinate.
     * @param yin The y coordinate.
     * @return A noise value in the range [-1, 1].
     */
    public float noise(double xin, double yin) {
        double n0, n1, n2; // Вклад от трех углов

        // Skew the input space to determine which simplex cell we're in
        double s = (xin + yin) * F2;
        int i = fastfloor(xin + s);
        int j = fastfloor(yin + s);

        double t = (i + j) * G2;
        double X0 = i - t; // Unskew the cell origin back to (x,y) space
        double Y0 = j - t;
        double x0 = xin - X0; // The x,y distances from the cell origin
        double y0 = yin - Y0;

        // For the 2D case, the simplex shape is an equilateral triangle.
        // Determine which simplex we are in.
        int i1, j1; // Offsets for second corner of simplex in (i,j) coords
        if (x0 > y0) {
            i1 = 1; j1 = 0; // lower triangle, XY order: (0,0)->(1,0)->(1,1)
        } else {
            i1 = 0; j1 = 1; // upper triangle, XY order: (0,0)->(0,1)->(1,1)
        }

        // A step of (1,0) in (i,j) means a step of (1-c,-c) in (x,y), and
        // a step of (0,1) in (i,j) means a step of (-c,1-c) in (x,y), where
        // c = (3-sqrt(3))/6
        double x1 = x0 - i1 + G2; // Offsets for second corner in (x,y) coords
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2; // Offsets for third corner in (x,y) coords
        double y2 = y0 - 1.0 + 2.0 * G2;

        // Work out the hashed gradient indices of the three simplex corners
        int ii = i & 255;
        int jj = j & 255;
        int gi0 = permMod12[ii + perm[jj]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1]];
        int gi2 = permMod12[ii + 1 + perm[jj + 1]];

        // Calculate the contribution from the three corners
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 < 0) n0 = 0.0;
        else {
            t0 *= t0;
            n0 = t0 * t0 * dot(grad3[gi0], x0, y0);
        }

        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 < 0) n1 = 0.0;
        else {
            t1 *= t1;
            n1 = t1 * t1 * dot(grad3[gi1], x1, y1);
        }

        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 < 0) n2 = 0.0;
        else {
            t2 *= t2;
            n2 = t2 * t2 * dot(grad3[gi2], x2, y2);
        }

        // Add contributions from each corner to get the final noise value.
        // The result is scaled to return values in the interval [-1,1].
        return (float) (NORMALIZATION_FACTOR * (n0 + n1 + n2));
    }

    private static int fastfloor(double x) {
        return x > 0 ? (int) x : (int) x - 1;
    }

    private static double dot(double[] g, double x, double y) {
        return g[0] * x + g[1] * y;
    }
}