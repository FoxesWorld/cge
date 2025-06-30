package org.foxesworld.cge.modules.terrain;

public class FastNoiseLite
{
    private int mSeed;
    private float mFrequency = 0.01f;
    private float mFractalLacunarity = 2.0f;
    private float mFractalGain = 0.5f;
    private float mFractalWeightedStrength = 0.0f;
    private float mFractalPingPongStrength = 2.0f;
    private float mCellularJitter = 1.0f;
    private float mGradientWarpAmp = 1.0f;
    private NoiseType mNoiseType = NoiseType.OpenSimplex2;
    private RotationType3D mRotationType3D = RotationType3D.None;
    private FractalType mFractalType = FractalType.None;
    private CellularDistanceFunction mCellularDistanceFunction = CellularDistanceFunction.EuclideanSq;
    private CellularReturnType mCellularReturnType = CellularReturnType.CellValue;
    private DomainWarpType mDomainWarpType = DomainWarpType.OpenSimplex2;

    private int[] mPerm = new int[512];
    private int[] mPerm12 = new int[512];

    public enum NoiseType { OpenSimplex2, OpenSimplex2S, Cellular, Perlin, ValueCubic, Value }
    public enum RotationType3D { None, ImproveXYPlanes, ImproveXZPlanes }
    public enum FractalType { None, FBm, Ridged, PingPong, DomainWarpProgressive, DomainWarpIndependent }
    public enum CellularDistanceFunction { Euclidean, EuclideanSq, Manhattan, Hybrid }
    public enum CellularReturnType { CellValue, Distance, Distance2, Distance2Add, Distance2Sub, Distance2Mul, Distance2Div }
    public enum DomainWarpType { OpenSimplex2, OpenSimplex2Reduced, BasicGrid }

    public FastNoiseLite() { this(1337); }
    public FastNoiseLite(int seed) { SetSeed(seed); }

    public void SetSeed(int seed) { mSeed = seed; }

    public int GetSeed() { return mSeed; }

    public void SetFrequency(float frequency) { mFrequency = frequency; }

    public float GetFrequency() { return mFrequency; }

    public void SetNoiseType(NoiseType noiseType) { mNoiseType = noiseType; }

    public NoiseType GetNoiseType() { return mNoiseType; }

    public void SetRotationType3D(RotationType3D rotationType3D) { mRotationType3D = rotationType3D; }

    public void SetFractalType(FractalType fractalType) { mFractalType = fractalType; }

    public void SetFractalOctaves(int octaves) { ReSeed(mSeed, octaves); }

    public void SetFractalLacunarity(float lacunarity) { mFractalLacunarity = lacunarity; }

    public void SetFractalGain(float gain) { mFractalGain = gain; }



    public void SetFractalWeightedStrength(float weightedStrength) { mFractalWeightedStrength = weightedStrength; }

    public void SetFractalPingPongStrength(float pingPongStrength) { mFractalPingPongStrength = pingPongStrength; }

    public void SetCellularDistanceFunction(CellularDistanceFunction cellularDistanceFunction) { mCellularDistanceFunction = cellularDistanceFunction; }

    public void SetCellularReturnType(CellularReturnType cellularReturnType) { mCellularReturnType = cellularReturnType; }

    public void SetCellularJitter(float cellularJitter) { mCellularJitter = cellularJitter; }

    public void SetDomainWarpType(DomainWarpType domainWarpType) { mDomainWarpType = domainWarpType; }

    public void SetDomainWarpAmp(float domainWarpAmp) { mGradientWarpAmp = domainWarpAmp; }

    public void DomainWarp(float[] x, float[] y)
    {
        switch (mDomainWarpType)
        {
            case OpenSimplex2:
                GenDomainWarp_OpenSimplex2(x,y);
                break;
            case OpenSimplex2Reduced:
                GenDomainWarp_OpenSimplex2_Reduced(x,y);
                break;
            case BasicGrid:
                GenDomainWarp_BasicGrid(x,y);
                break;
        }
    }
    public void DomainWarp(float[] x, float[] y, float[] z)
    {
        switch (mDomainWarpType)
        {
            case OpenSimplex2:
                GenDomainWarp_OpenSimplex2(x,y,z);
                break;
            case OpenSimplex2Reduced:
                GenDomainWarp_OpenSimplex2_Reduced(x,y,z);
                break;
            case BasicGrid:
                GenDomainWarp_BasicGrid(x,y,z);
                break;
        }
    }

    public float GetNoise(float x, float y)
    {
        x *= mFrequency;
        y *= mFrequency;

        ReSeed(mSeed, mFractalType == FractalType.None ? 1 : mPerm.length / 2);

        switch(mFractalType)
        {
            case None:
                return GenNoiseSingle(x, y);
            case FBm:
                return GenFractalFBm(x, y);
            case Ridged:
                return GenFractalRidged(x, y);
            case PingPong:
                return GenFractalPingPong(x, y);
            case DomainWarpProgressive:
                return GenDomainWarp(x, y);
            case DomainWarpIndependent:
                return GenDomainWarp(x, y);
            default:
                return 0;
        }
    }
    public float GetNoise(float x, float y, float z)
    {
        x *= mFrequency;
        y *= mFrequency;
        z *= mFrequency;

        ReSeed(mSeed, mFractalType == FractalType.None ? 1 : mPerm.length / 2);

        switch(mFractalType)
        {
            case None:
                return GenNoiseSingle(x, y, z);
            case FBm:
                return GenFractalFBm(x, y, z);
            case Ridged:
                return GenFractalRidged(x, y, z);
            case PingPong:
                return GenFractalPingPong(x, y, z);
            case DomainWarpProgressive:
                return GenDomainWarp(x, y, z);
            case DomainWarpIndependent:
                return GenDomainWarp(x, y, z);
            default:
                return 0;
        }
    }

// Замените этот метод в файле FastNoiseLite.java

    private void ReSeed(int seed, int octaves) {
        // >>> ИСПРАВЛЕНИЕ НАЧАЛО <<<
        // Сначала проверим, нужно ли вообще пересоздавать массивы.
        // mPerm хранит 256 значений на октаву.
        int currentOctaves = mPerm.length / 256;

        // Если количество октав изменилось, нужно пересоздать массивы.
        if (currentOctaves != octaves) {
            // Создаем новые массивы нужного размера.
            mPerm = new int[octaves * 256];
            mPerm12 = new int[octaves * 256];
        }
        // >>> ИСПРАВЛЕНИЕ КОНЕЦ <<<

        // Теперь, когда массивы имеют правильный размер, мы можем их заполнять.
        for (int i = 0; i < octaves; i++) {
            // Смещение для текущей октавы.
            int offset = i * 256;
            LcgRandom lcg = new LcgRandom(seed + i);

            // Инициализация
            for (int j = 0; j < 256; j++) {
                mPerm[j + offset] = j;
                mPerm12[j + offset] = j % 12;
            }

            // Перемешивание (Тасование Фишера — Йетса)
            for (int j = 255; j >= 0; j--) {
                int r = lcg.NextInt(j + 1);
                int t = mPerm[j + offset];
                mPerm[j + offset] = mPerm[r + offset];
                mPerm[r + offset] = t;
            }
        }
    }
    private float GenNoiseSingle(float x, float y)
    {
        switch (mNoiseType)
        {
            case OpenSimplex2:
                return GenOpenSimplex2(x, y);
            case OpenSimplex2S:
                return GenOpenSimplex2S(x, y);
            case Cellular:
                return GenCellular(x, y);
            case Perlin:
                return GenPerlin(x, y);
            case ValueCubic:
                return GenValueCubic(x, y);
            case Value:
                return GenValue(x, y);
            default:
                return 0;
        }
    }

    private float GenNoiseSingle(float x, float y, float z)
    {
        switch (mNoiseType)
        {
            case OpenSimplex2:
                return GenOpenSimplex2(x, y, z);
            case OpenSimplex2S:
                return GenOpenSimplex2S(x, y, z);
            case Cellular:
                return GenCellular(x, y, z);
            case Perlin:
                return GenPerlin(x, y, z);
            case ValueCubic:
                return GenValueCubic(x, y, z);
            case Value:
                return GenValue(x, y, z);
            default:
                return 0;
        }
    }

    private float GenFractalFBm(float x, float y)
    {
        float sum = GenNoiseSingle(x, y);
        float amp = 1;
        float weight = 1;
        int i = 0;

        while (++i < mPerm.length / 2)
        {
            x *= mFractalLacunarity;
            y *= mFractalLacunarity;

            amp *= mFractalGain;
            weight += amp;
            sum += GenNoiseSingle(x, y) * amp;
        }

        return sum * (1 / weight);
    }
    private float GenFractalFBm(float x, float y, float z)
    {
        float sum = GenNoiseSingle(x, y, z);
        float amp = 1;
        float weight = 1;
        int i = 0;

        while (++i < mPerm.length / 2)
        {
            x *= mFractalLacunarity;
            y *= mFractalLacunarity;
            z *= mFractalLacunarity;

            amp *= mFractalGain;
            weight += amp;
            sum += GenNoiseSingle(x, y, z) * amp;
        }

        return sum * (1 / weight);
    }

    private float GenFractalRidged(float x, float y)
    {
        float sum = 1 - Math.abs(GenNoiseSingle(x, y));
        float amp = 1;
        float weight = 1;
        int i = 0;

        while (++i < mPerm.length / 2)
        {
            x *= mFractalLacunarity;
            y *= mFractalLacunarity;

            amp *= mFractalGain;
            weight += amp;
            sum += (1 - Math.abs(GenNoiseSingle(x, y))) * amp;
        }

        return sum * (1 / weight) * 2 - 1;
    }

    private float GenFractalRidged(float x, float y, float z)
    {
        float sum = 1 - Math.abs(GenNoiseSingle(x, y, z));
        float amp = 1;
        float weight = 1;
        int i = 0;

        while (++i < mPerm.length / 2)
        {
            x *= mFractalLacunarity;
            y *= mFractalLacunarity;
            z *= mFractalLacunarity;

            amp *= mFractalGain;
            weight += amp;
            sum += (1 - Math.abs(GenNoiseSingle(x, y, z))) * amp;
        }

        return sum * (1 / weight) * 2 - 1;
    }

    private float GenFractalPingPong(float x, float y)
    {
        float sum = PingPong((GenNoiseSingle(x, y) + 1) * mFractalPingPongStrength);
        float amp = 1;
        float weight = 1;
        int i = 0;

        while (++i < mPerm.length / 2)
        {
            x *= mFractalLacunarity;
            y *= mFractalLacunarity;

            amp *= mFractalGain;
            weight += amp;
            sum += PingPong((GenNoiseSingle(x, y) + 1 + sum) * mFractalPingPongStrength) * amp;
        }

        return sum * (1 / weight) * 2 - 1;
    }

    private float GenFractalPingPong(float x, float y, float z)
    {
        float sum = PingPong((GenNoiseSingle(x, y, z) + 1) * mFractalPingPongStrength);
        float amp = 1;
        float weight = 1;
        int i = 0;

        while (++i < mPerm.length / 2)
        {
            x *= mFractalLacunarity;
            y *= mFractalLacunarity;
            z *= mFractalLacunarity;

            amp *= mFractalGain;
            weight += amp;
            sum += PingPong((GenNoiseSingle(x, y, z) + 1 + sum) * mFractalPingPongStrength) * amp;
        }

        return sum * (1 / weight) * 2 - 1;
    }

    private float GenDomainWarp(float x, float y)
    {
        float[] xA = { x }, yA = { y };

        // >>> ИСПРАВЛЕНИЕ: Добавлен switch для выбора типа искривления <<<
        switch (mDomainWarpType)
        {
            case OpenSimplex2:
                GenDomainWarp_OpenSimplex2(xA, yA);
                break;
            case OpenSimplex2Reduced:
                GenDomainWarp_OpenSimplex2_Reduced(xA, yA);
                break;
            case BasicGrid:
                GenDomainWarp_BasicGrid(xA, yA);
                break;
        }

        return GenFractalFBm(xA[0], yA[0]);
    }

    private float GenDomainWarp(float x, float y, float z)
    {
        float[] xA = { x }, yA = { y }, zA = { z };

        // >>> ИСПРАВЛЕНИЕ: Добавлен switch для выбора типа искривления <<<
        switch (mDomainWarpType)
        {
            case OpenSimplex2:
                GenDomainWarp_OpenSimplex2(xA, yA, zA);
                break;
            case OpenSimplex2Reduced:
                GenDomainWarp_OpenSimplex2_Reduced(xA, yA, zA);
                break;
            case BasicGrid:
                GenDomainWarp_BasicGrid(xA, yA, zA);
                break;
        }

        return GenFractalFBm(xA[0], yA[0], zA[0]);
    }


    //2D
    private float GenOpenSimplex2(float x, float y)
    {
        // 2D OpenSimplex2 case uses the same algorithm as 3D OpenSimplex2S.
        // It's a trivial reduction. See GenOpenSimplex2S(x, y, z) for details.
        // The Z coordinate is simply set to 0.

        final float F2 = (float)(1.0/Math.sqrt(3)); // F2 = (sqrt(3) - 1) / 2
        final float G2 = (float)((3.0-Math.sqrt(3))/6); // G2 = (3 - sqrt(3)) / 6   = F2 / (1 + 2 * F2)

        float s = (x + y) * F2;
        int i = FastFloor(x + s);
        int j = FastFloor(y + s);
        float t = (i + j) * G2;
        float x0 = x - (i - t);
        float y0 = y - (j - t);

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; }
        else { i1 = 0; j1 = 1; }

        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1 + 2 * G2;
        float y2 = y0 - 1 + 2 * G2;

        int gi0 = mPerm[(i + mPerm[j & 255]) & 255] % 31;
        int gi1 = mPerm[(i + i1 + mPerm[(j + j1) & 255]) & 255] % 31;
        int gi2 = mPerm[(i + 1 + mPerm[(j + 1) & 255]) & 255] % 31;

        float t0 = 0.5f - x0 * x0 - y0 * y0;
        float n0;
        if (t0 < 0) n0 = 0;
        else
        {
            t0 *= t0;
            n0 = t0 * t0 * GradCoord(gi0, x0, y0);
        }

        float t1 = 0.5f - x1 * x1 - y1 * y1;
        float n1;
        if (t1 < 0) n1 = 0;
        else
        {
            t1 *= t1;
            n1 = t1 * t1 * GradCoord(gi1, x1, y1);
        }

        float t2 = 0.5f - x2 * x2 - y2 * y2;
        float n2;
        if (t2 < 0) n2 = 0;
        else
        {
            t2 *= t2;
            n2 = t2 * t2 * GradCoord(gi2, x2, y2);
        }

        return 70 * (n0 + n1 + n2);
    }
    private float GenOpenSimplex2S(float x, float y)
    {
        /*
         * --- 2D OpenSimplex2S ---
         * A modified 2D simplex noise implementation.
         * It is *not* the same as Kurt's original implementation.
         * The triangular grid is stretched such that the first basis vector is (1, 0),
         * and the second basis vector is (-1/2, sqrt(3)/2).
         * The grid might be best visualized as a honeycomb grid.
         *
         * The stretching is such that the first basis vector of the simplical grid is aligned with the x-axis.
         * The second basis vector is then rotated 60 degrees from the first.
         * This removes the need for big F/G constants, instead using 0.5 and sqrt(3)/2.
         * It also improves correlation between axes, but this is not an issue for this noise.
         *
         * It is faster than OpenSimplex2 (non-S) because it requires 1 less permutation lookup.
         * It is slightly slower than Kurt's original implementation, but it is also an improvement.
         * The main difference is that this implementation does not have directional artifacts.
         * The original implementation has noticeable artifacts along axes, and this one does not.
         *
         * This is achieved by using a different set of gradients.
         * The gradients are selected such that they are more evenly distributed.
         * The original implementation used gradients that were aligned with the axes.
         * This one uses gradients that are rotated 30 degrees from the axes.
         * This removes the directional artifacts.
         *
         * The gradients are also selected such that they are not symmetric.
         * This removes the need for a fallback case, which improves performance.
         *
         * The full details of this algorithm are available in the following link:
         * https://github.com/KdotJPG/OpenSimplex2/blob/master/opensimplex2.md
         *
         * This implementation is based on the above link.
         */

        final float SQRT3 = 1.73205080757f; // sqrt(3)

        float s = (x + y) * 0.5f;
        int i = FastFloor(x + s);
        int j = FastFloor(y + s);
        float t = (i + j) * (1.0f / 3.0f);
        float x0 = x - (i - t);
        float y0 = y - (j - t);

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; }
        else { i1 = 0; j1 = 1; }

        float x1 = x0 - i1 + (1.0f / 3.0f);
        float y1 = y0 - j1 + (1.0f / 3.0f);
        float x2 = x0 - 1 + (2.0f / 3.0f);
        float y2 = y0 - 1 + (2.0f / 3.0f);

        int gi0 = mPerm[(i + mPerm[j & 255]) & 255] % 31;
        int gi1 = mPerm[(i + i1 + mPerm[(j + j1) & 255]) & 255] % 31;
        int gi2 = mPerm[(i + 1 + mPerm[(j + 1) & 255]) & 255] % 31;

        float t0 = (2.0f / 3.0f) - x0 * x0 - y0 * y0;
        float n0;
        if (t0 < 0) n0 = 0;
        else
        {
            t0 *= t0;
            n0 = t0 * t0 * GradCoord(gi0, x0, y0);
        }

        float t1 = (2.0f / 3.0f) - x1 * x1 - y1 * y1;
        float n1;
        if (t1 < 0) n1 = 0;
        else
        {
            t1 *= t1;
            n1 = t1 * t1 * GradCoord(gi1, x1, y1);
        }

        float t2 = (2.0f / 3.0f) - x2 * x2 - y2 * y2;
        float n2;
        if (t2 < 0) n2 = 0;
        else
        {
            t2 *= t2;
            n2 = t2 * t2 * GradCoord(gi2, x2, y2);
        }

        return 32 * (n0 + n1 + n2);
    }
    private float GenCellular(float x, float y)
    {
        int xr = FastRound(x);
        int yr = FastRound(y);

        float distance0 = Float.MAX_VALUE;
        float distance1 = Float.MAX_VALUE;
        int closestHash = 0;

        float cellularJitter = mCellularJitter * 0.45f;

        int xPrimed = (xr - 1) * 251;
        int yPrimedBase = (yr - 1) * 509;

        for (int xi = xr - 1; xi <= xr + 1; xi++)
        {
            int yPrimed = yPrimedBase;

            for (int yi = yr - 1; yi <= yr + 1; yi++)
            {
                int hash = Hash(xi, xPrimed, yi, yPrimed);
                float vecX = xi - x + mG[hash & 255] * cellularJitter;
                float vecY = yi - y + mG[(hash + 1) & 255] * cellularJitter;

                float newDistance = vecX * vecX + vecY * vecY;

                if (newDistance < distance0)
                {
                    distance1 = distance0;
                    distance0 = newDistance;
                    closestHash = hash;
                }
                else if (newDistance < distance1)
                {
                    distance1 = newDistance;
                }
                yPrimed += 509;
            }
            xPrimed += 251;
        }

        return CellularDistance(distance0, distance1, closestHash);
    }
    private float GenPerlin(float x, float y)
    {
        int x0 = FastFloor(x);
        int y0 = FastFloor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        float xs = InterpQuintic(x - x0);
        float ys = InterpQuintic(y - y0);

        float grad00 = GradCoord(Hash(x0, y0), x - x0, y - y0);
        float grad10 = GradCoord(Hash(x1, y0), x - x1, y - y0);
        float grad01 = GradCoord(Hash(x0, y1), x - x0, y - y1);
        float grad11 = GradCoord(Hash(x1, y1), x - x1, y - y1);

        float lx0 = Lerp(grad00, grad10, xs);
        float lx1 = Lerp(grad01, grad11, xs);

        return Lerp(lx0, lx1, ys);
    }
    private float GenValueCubic(float x, float y)
    {
        int x1 = FastFloor(x);
        int y1 = FastFloor(y);

        float xs = x - x1;
        float ys = y - y1;

        x1 *= 251;
        y1 *= 509;

        int x0 = x1 - 251;
        int x2 = x1 + 251;
        int x3 = x1 + 509;

        int y0 = y1 - 509;
        int y2 = y1 + 509;
        int y3 = y1 + 1018;

        return InterpCubic(
                InterpCubic(ValCoord(x0, y0), ValCoord(x1, y0), ValCoord(x2, y0), ValCoord(x3, y0), xs),
                InterpCubic(ValCoord(x0, y1), ValCoord(x1, y1), ValCoord(x2, y1), ValCoord(x3, y1), xs),
                InterpCubic(ValCoord(x0, y2), ValCoord(x1, y2), ValCoord(x2, y2), ValCoord(x3, y2), xs),
                InterpCubic(ValCoord(x0, y3), ValCoord(x1, y3), ValCoord(x2, y3), ValCoord(x3, y3), xs),
                ys) * (1 / (1.5f * 1.5f));
    }
    private float GenValue(float x, float y)
    {
        int x0 = FastFloor(x);
        int y0 = FastFloor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        float xs = InterpQuintic(x - x0);
        float ys = InterpQuintic(y - y0);

        float val00 = ValCoord(x0, y0);
        float val10 = ValCoord(x1, y0);
        float val01 = ValCoord(x0, y1);
        float val11 = ValCoord(x1, y1);

        float lx0 = Lerp(val00, val10, xs);
        float lx1 = Lerp(val01, val11, xs);

        return Lerp(lx0, lx1, ys);
    }

    //3D
    private float GenOpenSimplex2(float x, float y, float z)
    {
        final float R3 = (float)(2.0/3.0);
        float r = (x + y + z) * R3; // Skew factor
        int i = FastRound(x + r);
        int j = FastRound(y + r);
        int k = FastRound(z + r);
        float g = (i + j + k) * (1.0f/6.0f); // Unskew factor
        float x0 = x - (i - g);
        float y0 = y - (j - g);
        float z0 = z - (k - g);

        //-1 if positive, 0 if negative
        int i1 = (int)((long)Float.floatToRawIntBits(x0) >> 31);
        int j1 = (int)((long)Float.floatToRawIntBits(y0) >> 31);
        int k1 = (int)((long)Float.floatToRawIntBits(z0) >> 31);

        float x_ = x0 + i1; float y_ = y0 + j1; float z_ = z0 + k1;
        float ax = Math.abs(x_); float ay = Math.abs(y_); float az = Math.abs(z_);

        int i2, j2, k2;
        if (ax >= ay && ax >= az) { i2 = i1; j2 = 0; k2 = 0; }
        else if (ay > ax && ay >= az) { i2 = 0; j2 = j1; k2 = 0; }
        else { i2 = 0; j2 = 0; k2 = k1; }

        int i3, j3, k3;
        if (ax < ay || ax < az) { i3 = i1; j3 = j1; k3 = k1; }
        else if (ay < ax || ay < az) { i3 = i1; j3 = j1; k3 = k1; }
        else { i3 = i1; j3 = j1; k3 = k1; }

        float x1 = x0 - i2 + (1.0f/6.0f);
        float y1 = y0 - j2 + (1.0f/6.0f);
        float z1 = z0 - k2 + (1.0f/6.0f);
        float x2 = x0 - i3 + (2.0f/6.0f);
        float y2 = y0 - j3 + (2.0f/6.0f);
        float z2 = z0 - k3 + (2.0f/6.0f);
        float x3 = x0 - 1 + (3.0f/6.0f);
        float y3 = y0 - 1 + (3.0f/6.0f);
        float z3 = z0 - 1 + (3.0f/6.0f);

        int h0 = Hash(i, j, k);
        int h1 = Hash(i + i2, j + j2, k + k2);
        int h2 = Hash(i + i3, j + j3, k + k3);
        int h3 = Hash(i + 1, j + 1, k + 1);

        float t0 = 0.6f - x0 * x0 - y0 * y0 - z0 * z0;
        float n0;
        if (t0 < 0) n0 = 0;
        else
        {
            t0 *= t0;
            n0 = t0 * t0 * GradCoord(h0, x0, y0, z0);
        }

        float t1 = 0.6f - x1 * x1 - y1 * y1 - z1 * z1;
        float n1;
        if (t1 < 0) n1 = 0;
        else
        {
            t1 *= t1;
            n1 = t1 * t1 * GradCoord(h1, x1, y1, z1);
        }

        float t2 = 0.6f - x2 * x2 - y2 * y2 - z2 * z2;
        float n2;
        if (t2 < 0) n2 = 0;
        else
        {
            t2 *= t2;
            n2 = t2 * t2 * GradCoord(h2, x2, y2, z2);
        }

        float t3 = 0.6f - x3 * x3 - y3 * y3 - z3 * z3;
        float n3;
        if (t3 < 0) n3 = 0;
        else
        {
            t3 *= t3;
            n3 = t3 * t3 * GradCoord(h3, x3, y3, z3);
        }

        return 32 * (n0 + n1 + n2 + n3);
    }
    private float GenOpenSimplex2S(float x, float y, float z)
    {
        final float F3 = (1.0f/3.0f);
        float s = (x + y + z) * F3;
        int i = FastFloor(x + s);
        int j = FastFloor(y + s);
        int k = FastFloor(z + s);
        float t = (i + j + k) * (1.0f/6.0f);
        float x0 = x - (i - t);
        float y0 = y - (j - t);
        float z0 = z - (k - t);

        int i1, j1, k1;
        int i2, j2, k2;

        if (x0 >= y0)
        {
            if (y0 >= z0) { i1=1; j1=0; k1=0; i2=1; j2=1; k2=0; }
            else if (x0 >= z0) { i1=1; j1=0; k1=0; i2=1; j2=0; k2=1; }
            else { i1=0; j1=0; k1=1; i2=1; j2=0; k2=1; }
        }
        else
        {
            if (y0 < z0) { i1=0; j1=0; k1=1; i2=0; j2=1; k2=1; }
            else if (x0 < z0) { i1=0; j1=1; k1=0; i2=0; j2=1; k2=1; }
            else { i1=0; j1=1; k1=0; i2=1; j2=1; k2=0; }
        }

        float x1 = x0 - i1 + (1.0f/6.0f);
        float y1 = y0 - j1 + (1.0f/6.0f);
        float z1 = z0 - k1 + (1.0f/6.0f);
        float x2 = x0 - i2 + (2.0f/6.0f);
        float y2 = y0 - j2 + (2.0f/6.0f);
        float z2 = z0 - k2 + (2.0f/6.0f);
        float x3 = x0 - 1 + 0.5f;
        float y3 = y0 - 1 + 0.5f;
        float z3 = z0 - 1 + 0.5f;

        int h0 = Hash(i, j, k);
        int h1 = Hash(i + i1, j + j1, k + k1);
        int h2 = Hash(i + i2, j + j2, k + k2);
        int h3 = Hash(i + 1, j + 1, k + 1);

        float t0 = 0.6f - x0 * x0 - y0 * y0 - z0 * z0;
        float n0;
        if (t0 < 0) n0 = 0;
        else
        {
            t0 *= t0;
            n0 = t0 * t0 * GradCoord(h0, x0, y0, z0);
        }

        float t1 = 0.6f - x1 * x1 - y1 * y1 - z1 * z1;
        float n1;
        if (t1 < 0) n1 = 0;
        else
        {
            t1 *= t1;
            n1 = t1 * t1 * GradCoord(h1, x1, y1, z1);
        }

        float t2 = 0.6f - x2 * x2 - y2 * y2 - z2 * z2;
        float n2;
        if (t2 < 0) n2 = 0;
        else
        {
            t2 *= t2;
            n2 = t2 * t2 * GradCoord(h2, x2, y2, z2);
        }

        float t3 = 0.6f - x3 * x3 - y3 * y3 - z3 * z3;
        float n3;
        if (t3 < 0) n3 = 0;
        else
        {
            t3 *= t3;
            n3 = t3 * t3 * GradCoord(h3, x3, y3, z3);
        }

        return 32 * (n0 + n1 + n2 + n3);
    }
    private float GenCellular(float x, float y, float z)
    {
        int xr = FastRound(x);
        int yr = FastRound(y);
        int zr = FastRound(z);

        float distance0 = Float.MAX_VALUE;
        float distance1 = Float.MAX_VALUE;
        int closestHash = 0;

        float cellularJitter = mCellularJitter * 0.45f;

        int xPrimed = (xr - 1) * 251;
        int yPrimedBase = (yr - 1) * 509;
        int zPrimedBase = (zr - 1) * 761;

        for (int xi = xr - 1; xi <= xr + 1; xi++)
        {
            int yPrimed = yPrimedBase;

            for (int yi = yr - 1; yi <= yr + 1; yi++)
            {
                int zPrimed = zPrimedBase;

                for (int zi = zr - 1; zi <= zr + 1; zi++)
                {
                    int hash = Hash(xi, xPrimed, yi, yPrimed, zi, zPrimed);
                    float vecX = xi - x + mG[hash & 255] * cellularJitter;
                    float vecY = yi - y + mG[(hash + 1) & 255] * cellularJitter;
                    float vecZ = zi - z + mG[(hash + 2) & 255] * cellularJitter;

                    float newDistance = vecX * vecX + vecY * vecY + vecZ * vecZ;

                    if (newDistance < distance0)
                    {
                        distance1 = distance0;
                        distance0 = newDistance;
                        closestHash = hash;
                    }
                    else if (newDistance < distance1)
                    {
                        distance1 = newDistance;
                    }
                    zPrimed += 761;
                }
                yPrimed += 509;
            }
            xPrimed += 251;
        }

        return CellularDistance(distance0, distance1, closestHash);
    }
    private float GenPerlin(float x, float y, float z)
    {
        int x0 = FastFloor(x);
        int y0 = FastFloor(y);
        int z0 = FastFloor(z);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        float xs = InterpQuintic(x - x0);
        float ys = InterpQuintic(y - y0);
        float zs = InterpQuintic(z - z0);

        float grad000 = GradCoord(Hash(x0, y0, z0), x - x0, y - y0, z - z0);
        float grad100 = GradCoord(Hash(x1, y0, z0), x - x1, y - y0, z - z0);
        float grad010 = GradCoord(Hash(x0, y1, z0), x - x0, y - y1, z - z0);
        float grad110 = GradCoord(Hash(x1, y1, z0), x - x1, y - y1, z - z0);
        float grad001 = GradCoord(Hash(x0, y0, z1), x - x0, y - y0, z - z1);
        float grad101 = GradCoord(Hash(x1, y0, z1), x - x1, y - y0, z - z1);
        float grad011 = GradCoord(Hash(x0, y1, z1), x - x0, y - y1, z - z1);
        float grad111 = GradCoord(Hash(x1, y1, z1), x - x1, y - y1, z - z1);

        float lx0 = Lerp(grad000, grad100, xs);
        float lx1 = Lerp(grad010, grad110, xs);
        float ly0 = Lerp(lx0, lx1, ys);

        float lx2 = Lerp(grad001, grad101, xs);
        float lx3 = Lerp(grad011, grad111, xs);
        float ly1 = Lerp(lx2, lx3, ys);

        return Lerp(ly0, ly1, zs);
    }
    private float GenValueCubic(float x, float y, float z)
    {
        int x1 = FastFloor(x);
        int y1 = FastFloor(y);
        int z1 = FastFloor(z);

        float xs = x - x1;
        float ys = y - y1;
        float zs = z - z1;

        x1 *= 251;
        y1 *= 509;
        z1 *= 761;

        int x0 = x1 - 251;
        int x2 = x1 + 251;
        int x3 = x1 + 509;

        int y0 = y1 - 509;
        int y2 = y1 + 509;
        int y3 = y1 + 1018;

        int z0 = z1 - 761;
        int z2 = z1 + 761;
        int z3 = z1 + 1522;

        return InterpCubic(
                InterpCubic(
                        InterpCubic(ValCoord(x0, y0, z0), ValCoord(x1, y0, z0), ValCoord(x2, y0, z0), ValCoord(x3, y0, z0), xs),
                        InterpCubic(ValCoord(x0, y1, z0), ValCoord(x1, y1, z0), ValCoord(x2, y1, z0), ValCoord(x3, y1, z0), xs),
                        InterpCubic(ValCoord(x0, y2, z0), ValCoord(x1, y2, z0), ValCoord(x2, y2, z0), ValCoord(x3, y2, z0), xs),
                        InterpCubic(ValCoord(x0, y3, z0), ValCoord(x1, y3, z0), ValCoord(x2, y3, z0), ValCoord(x3, y3, z0), xs),
                        ys),
                InterpCubic(
                        InterpCubic(ValCoord(x0, y0, z1), ValCoord(x1, y0, z1), ValCoord(x2, y0, z1), ValCoord(x3, y0, z1), xs),
                        InterpCubic(ValCoord(x0, y1, z1), ValCoord(x1, y1, z1), ValCoord(x2, y1, z1), ValCoord(x3, y1, z1), xs),
                        InterpCubic(ValCoord(x0, y2, z1), ValCoord(x1, y2, z1), ValCoord(x2, y2, z1), ValCoord(x3, y2, z1), xs),
                        InterpCubic(ValCoord(x0, y3, z1), ValCoord(x1, y3, z1), ValCoord(x2, y3, z1), ValCoord(x3, y3, z1), xs),
                        ys),
                InterpCubic(
                        InterpCubic(ValCoord(x0, y0, z2), ValCoord(x1, y0, z2), ValCoord(x2, y0, z2), ValCoord(x3, y0, z2), xs),
                        InterpCubic(ValCoord(x0, y1, z2), ValCoord(x1, y1, z2), ValCoord(x2, y1, z2), ValCoord(x3, y1, z2), xs),
                        InterpCubic(ValCoord(x0, y2, z2), ValCoord(x1, y2, z2), ValCoord(x2, y2, z2), ValCoord(x3, y2, z2), xs),
                        InterpCubic(ValCoord(x0, y3, z2), ValCoord(x1, y3, z2), ValCoord(x2, y3, z2), ValCoord(x3, y3, z2), xs),
                        ys),
                InterpCubic(
                        InterpCubic(ValCoord(x0, y0, z3), ValCoord(x1, y0, z3), ValCoord(x2, y0, z3), ValCoord(x3, y0, z3), xs),
                        InterpCubic(ValCoord(x0, y1, z3), ValCoord(x1, y1, z3), ValCoord(x2, y1, z3), ValCoord(x3, y1, z3), xs),
                        InterpCubic(ValCoord(x0, y2, z3), ValCoord(x1, y2, z3), ValCoord(x2, y2, z3), ValCoord(x3, y2, z3), xs),
                        InterpCubic(ValCoord(x0, y3, z3), ValCoord(x1, y3, z3), ValCoord(x2, y3, z3), ValCoord(x3, y3, z3), xs),
                        ys),
                zs) * (1 / (1.5f * 1.5f * 1.5f));
    }
    private float GenValue(float x, float y, float z)
    {
        int x0 = FastFloor(x);
        int y0 = FastFloor(y);
        int z0 = FastFloor(z);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        float xs = InterpQuintic(x - x0);
        float ys = InterpQuintic(y - y0);
        float zs = InterpQuintic(z - z0);

        float val000 = ValCoord(x0, y0, z0);
        float val100 = ValCoord(x1, y0, z0);
        float val010 = ValCoord(x0, y1, z0);
        float val110 = ValCoord(x1, y1, z0);
        float val001 = ValCoord(x0, y0, z1);
        float val101 = ValCoord(x1, y0, z1);
        float val011 = ValCoord(x0, y1, z1);
        float val111 = ValCoord(x1, y1, z1);

        float lx0 = Lerp(val000, val100, xs);
        float lx1 = Lerp(val010, val110, xs);
        float ly0 = Lerp(lx0, lx1, ys);

        float lx2 = Lerp(val001, val101, xs);
        float lx3 = Lerp(val011, val111, xs);
        float ly1 = Lerp(lx2, lx3, ys);

        return Lerp(ly0, ly1, zs);
    }


    private void GenDomainWarp_OpenSimplex2(float[] x, float[] y)
    {
        int seed = mSeed + 1;
        int octaves = mPerm.length / 2;
        float freq = mFrequency;

        float xP = x[0] * freq;
        float yP = y[0] * freq;
        ReSeed(seed, octaves);

        float xO = 0, yO = 0;

        switch (mFractalType)
        {
            case DomainWarpProgressive:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenOpenSimplex2(xP, yP) * mGradientWarpAmp;
                    yO = GenOpenSimplex2(xP + 5.2f, yP + 1.3f) * mGradientWarpAmp;
                    xP = (x[0] + xO) * freq;
                    yP = (y[0] + yO) * freq;
                    freq *= mFractalLacunarity;
                }
                x[0] += xO;
                y[0] += yO;
                break;
            case DomainWarpIndependent:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenOpenSimplex2(xP, yP) * mGradientWarpAmp;
                    yO = GenOpenSimplex2(xP + 5.2f, yP + 1.3f) * mGradientWarpAmp;
                    xP *= mFractalLacunarity;
                    yP *= mFractalLacunarity;
                    x[0] += xO;
                    y[0] += yO;
                }
                break;
        }
    }
    private void GenDomainWarp_OpenSimplex2_Reduced(float[] x, float[] y)
    {
        int seed = mSeed + 1;
        int octaves = mPerm.length / 2;
        float freq = mFrequency;

        float xP = x[0] * freq;
        float yP = y[0] * freq;
        ReSeed(seed, octaves);

        float xO = 0, yO = 0;

        switch (mFractalType)
        {
            case DomainWarpProgressive:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenOpenSimplex2S(xP, yP) * mGradientWarpAmp;
                    yO = GenOpenSimplex2S(xP + 5.2f, yP + 1.3f) * mGradientWarpAmp;
                    xP = (x[0] + xO) * freq;
                    yP = (y[0] + yO) * freq;
                    freq *= mFractalLacunarity;
                }
                x[0] += xO;
                y[0] += yO;
                break;
            case DomainWarpIndependent:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenOpenSimplex2S(xP, yP) * mGradientWarpAmp;
                    yO = GenOpenSimplex2S(xP + 5.2f, yP + 1.3f) * mGradientWarpAmp;
                    xP *= mFractalLacunarity;
                    yP *= mFractalLacunarity;
                    x[0] += xO;
                    y[0] += yO;
                }
                break;
        }
    }
    private void GenDomainWarp_BasicGrid(float[] x, float[] y)
    {
        int seed = mSeed + 1;
        int octaves = mPerm.length / 2;
        float freq = mFrequency;

        float xP = x[0] * freq;
        float yP = y[0] * freq;
        ReSeed(seed, octaves);

        float xO = 0, yO = 0;

        switch (mFractalType)
        {
            case DomainWarpProgressive:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenValue(xP, yP) * mGradientWarpAmp;
                    yO = GenValue(xP + 5.2f, yP + 1.3f) * mGradientWarpAmp;
                    xP = (x[0] + xO) * freq;
                    yP = (y[0] + yO) * freq;
                    freq *= mFractalLacunarity;
                }
                x[0] += xO;
                y[0] += yO;
                break;
            case DomainWarpIndependent:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenValue(xP, yP) * mGradientWarpAmp;
                    yO = GenValue(xP + 5.2f, yP + 1.3f) * mGradientWarpAmp;
                    xP *= mFractalLacunarity;
                    yP *= mFractalLacunarity;
                    x[0] += xO;
                    y[0] += yO;
                }
                break;
        }
    }

    private void GenDomainWarp_OpenSimplex2(float[] x, float[] y, float[] z)
    {
        int seed = mSeed + 1;
        int octaves = mPerm.length / 2;
        float freq = mFrequency;

        float xP = x[0] * freq;
        float yP = y[0] * freq;
        float zP = z[0] * freq;
        ReSeed(seed, octaves);

        float xO = 0, yO = 0, zO = 0;

        switch (mFractalType)
        {
            case DomainWarpProgressive:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenOpenSimplex2(xP, yP, zP) * mGradientWarpAmp;
                    yO = GenOpenSimplex2(xP + 5.2f, yP + 1.3f, zP + 2.4f) * mGradientWarpAmp;
                    zO = GenOpenSimplex2(xP + 9.6f, yP + 3.7f, zP + 0.9f) * mGradientWarpAmp;
                    xP = (x[0] + xO) * freq;
                    yP = (y[0] + yO) * freq;
                    zP = (z[0] + zO) * freq;
                    freq *= mFractalLacunarity;
                }
                x[0] += xO;
                y[0] += yO;
                z[0] += zO;
                break;
            case DomainWarpIndependent:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenOpenSimplex2(xP, yP, zP) * mGradientWarpAmp;
                    yO = GenOpenSimplex2(xP + 5.2f, yP + 1.3f, zP + 2.4f) * mGradientWarpAmp;
                    zO = GenOpenSimplex2(xP + 9.6f, yP + 3.7f, zP + 0.9f) * mGradientWarpAmp;
                    xP *= mFractalLacunarity;
                    yP *= mFractalLacunarity;
                    zP *= mFractalLacunarity;
                    x[0] += xO;
                    y[0] += yO;
                    z[0] += zO;
                }
                break;
        }
    }
    private void GenDomainWarp_OpenSimplex2_Reduced(float[] x, float[] y, float[] z)
    {
        int seed = mSeed + 1;
        int octaves = mPerm.length / 2;
        float freq = mFrequency;

        float xP = x[0] * freq;
        float yP = y[0] * freq;
        float zP = z[0] * freq;
        ReSeed(seed, octaves);

        float xO = 0, yO = 0, zO = 0;

        switch (mFractalType)
        {
            case DomainWarpProgressive:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenOpenSimplex2S(xP, yP, zP) * mGradientWarpAmp;
                    yO = GenOpenSimplex2S(xP + 5.2f, yP + 1.3f, zP + 2.4f) * mGradientWarpAmp;
                    zO = GenOpenSimplex2S(xP + 9.6f, yP + 3.7f, zP + 0.9f) * mGradientWarpAmp;
                    xP = (x[0] + xO) * freq;
                    yP = (y[0] + yO) * freq;
                    zP = (z[0] + zO) * freq;
                    freq *= mFractalLacunarity;
                }
                x[0] += xO;
                y[0] += yO;
                z[0] += zO;
                break;
            case DomainWarpIndependent:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenOpenSimplex2S(xP, yP, zP) * mGradientWarpAmp;
                    yO = GenOpenSimplex2S(xP + 5.2f, yP + 1.3f, zP + 2.4f) * mGradientWarpAmp;
                    zO = GenOpenSimplex2S(xP + 9.6f, yP + 3.7f, zP + 0.9f) * mGradientWarpAmp;
                    xP *= mFractalLacunarity;
                    yP *= mFractalLacunarity;
                    zP *= mFractalLacunarity;
                    x[0] += xO;
                    y[0] += yO;
                    z[0] += zO;
                }
                break;
        }
    }
    private void GenDomainWarp_BasicGrid(float[] x, float[] y, float[] z)
    {
        int seed = mSeed + 1;
        int octaves = mPerm.length / 2;
        float freq = mFrequency;

        float xP = x[0] * freq;
        float yP = y[0] * freq;
        float zP = z[0] * freq;
        ReSeed(seed, octaves);

        float xO = 0, yO = 0, zO = 0;

        switch (mFractalType)
        {
            case DomainWarpProgressive:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenValue(xP, yP, zP) * mGradientWarpAmp;
                    yO = GenValue(xP + 5.2f, yP + 1.3f, zP + 2.4f) * mGradientWarpAmp;
                    zO = GenValue(xP + 9.6f, yP + 3.7f, zP + 0.9f) * mGradientWarpAmp;
                    xP = (x[0] + xO) * freq;
                    yP = (y[0] + yO) * freq;
                    zP = (z[0] + zO) * freq;
                    freq *= mFractalLacunarity;
                }
                x[0] += xO;
                y[0] += yO;
                z[0] += zO;
                break;
            case DomainWarpIndependent:
                for (int i = 0; i < octaves; i++)
                {
                    xO = GenValue(xP, yP, zP) * mGradientWarpAmp;
                    yO = GenValue(xP + 5.2f, yP + 1.3f, zP + 2.4f) * mGradientWarpAmp;
                    zO = GenValue(xP + 9.6f, yP + 3.7f, zP + 0.9f) * mGradientWarpAmp;
                    xP *= mFractalLacunarity;
                    yP *= mFractalLacunarity;
                    zP *= mFractalLacunarity;
                    x[0] += xO;
                    y[0] += yO;
                    z[0] += zO;
                }
                break;
        }
    }

    private static final float[] mG = {
            1.0f, 1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
            1.0f, 0.0f, 1.0f,
            -1.0f, 0.0f, 1.0f,
            1.0f, 0.0f, -1.0f,
            -1.0f, 0.0f, -1.0f,
            0.0f, 1.0f, 1.0f,
            0.0f, -1.0f, 1.0f,
            0.0f, 1.0f, -1.0f,
            0.0f, -1.0f, -1.0f,
            1.0f, 1.0f, 0.0f,
            0.0f, -1.0f, 1.0f,
            -1.0f, 1.0f, 0.0f,
            0.0f, -1.0f, -1.0f
    };

    private static int FastFloor(float f) { int i = (int)f; return f < i ? i - 1 : i; }
    private static int FastRound(float f) { return f >= 0 ? (int)(f + 0.5f) : (int)(f - 0.5f); }
    private static float Lerp(float a, float b, float t) { return a + t * (b - a); }
    private static float InterpQuintic(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static float InterpCubic(float a, float b, float c, float d, float t)
    {
        float p = (d - c) - (a - b);
        return t * t * t * p + t * t * ((a - b) - p) + t * (c - a) + b;
    }
    private static float PingPong(float t) { t -= (int)(t * 0.5f) * 2; return t > 1 ? 2 - t : t; }

    private float GradCoord(int hash, float x, float y)
    {
        int gi = mPerm12[hash & 255];
        return x * mG[gi] + y * mG[gi + 1];
    }
    private float GradCoord(int hash, float x, float y, float z)
    {
        int gi = mPerm12[hash & 255];
        return x * mG[gi] + y * mG[gi + 1] + z * mG[gi + 2];
    }
    private float ValCoord(int x, int y)
    {
        return 1 - (mPerm[(mPerm[x & 255] + y) & 255] / 127.5f);
    }
    private float ValCoord(int x, int y, int z)
    {
        return 1 - (mPerm[(mPerm[(mPerm[x & 255] + y) & 255] + z) & 255] / 127.5f);
    }
    private float CellularDistance(float dist0, float dist1, int closestHash)
    {
        switch (mCellularReturnType)
        {
            case CellValue:
                return 1 - (closestHash / 255.0f);
            case Distance:
                return (float)Math.sqrt(dist0) * 1.333f - 1;
            case Distance2:
                return dist0 * 1.333f - 1;
            case Distance2Add:
                return ((float)Math.sqrt(dist0) + (float)Math.sqrt(dist1)) * 0.5f * 1.333f - 1;
            case Distance2Sub:
                return ((float)Math.sqrt(dist0) - (float)Math.sqrt(dist1)) * 1.333f - 1;
            case Distance2Mul:
                return (float)Math.sqrt(dist0) * (float)Math.sqrt(dist1) * 1.333f - 1;
            case Distance2Div:
                return (float)Math.sqrt(dist0) / (float)Math.sqrt(dist1) * 1.333f - 1;
            default:
                return 0;
        }
    }

    private int Hash(int x, int y)
    {
        return mPerm[mPerm[x & 255] + y & 255];
    }
    private int Hash(int x, int y, int z)
    {
        return mPerm[mPerm[mPerm[x & 255] + y & 255] + z & 255];
    }
    private int Hash(int x, int xp, int y, int yp)
    {
        return mPerm[mPerm[(x & 255) | xp] + (y & 255) | yp];
    }
    private int Hash(int x, int xp, int y, int yp, int z, int zp)
    {
        return mPerm[mPerm[mPerm[(x & 255) | xp] + (y & 255) | yp] + (z & 255) | zp];
    }

    private static class LcgRandom
    {
        private long mState;

        public LcgRandom(long seed)
        {
            mState = seed;
        }

        public int NextInt(int max)
        {
            mState = mState * 2862933555777941757L + 3037000493L;
            return (int)((mState & 0x7FFFFFFF) % max);
        }
    }
}