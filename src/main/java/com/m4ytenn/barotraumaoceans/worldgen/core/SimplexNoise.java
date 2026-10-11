package com.m4ytenn.barotraumaoceans.worldgen.core;

/**
 * Seeded 2D/3D simplex noise (classic Perlin/Gustavson formulation), output roughly in [-1, 1].
 * Self-contained so the terrain core does not depend on Minecraft classes.
 */
public final class SimplexNoise {
    private static final double[] GX = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0};
    private static final double[] GY = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1};
    private static final double[] GZ = {0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1};

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    private final short[] perm = new short[512];
    private final short[] permMod12 = new short[512];
    private final double ox;
    private final double oy;
    private final double oz;

    public SimplexNoise(long seed) {
        short[] p = new short[256];
        for (int i = 0; i < 256; i++) {
            p[i] = (short) i;
        }
        Hashing.Stream random = new Hashing.Stream(seed);
        for (int i = 255; i > 0; i--) {
            int j = (int) (random.next() * (i + 1));
            short t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = (short) (perm[i] % 12);
        }
        ox = random.next() * 1024.0;
        oy = random.next() * 1024.0;
        oz = random.next() * 1024.0;
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    public double noise2(double xin, double yin) {
        xin += ox;
        yin += oy;
        double s = (xin + yin) * F2;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);
        double t = (i + j) * G2;
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);
        int i1;
        int j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;
        int ii = i & 255;
        int jj = j & 255;
        double n = 0.0;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 > 0) {
            int g = permMod12[ii + perm[jj]];
            t0 *= t0;
            n += t0 * t0 * (GX[g] * x0 + GY[g] * y0);
        }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 > 0) {
            int g = permMod12[ii + i1 + perm[jj + j1]];
            t1 *= t1;
            n += t1 * t1 * (GX[g] * x1 + GY[g] * y1);
        }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 > 0) {
            int g = permMod12[ii + 1 + perm[jj + 1]];
            t2 *= t2;
            n += t2 * t2 * (GX[g] * x2 + GY[g] * y2);
        }
        return 70.0 * n;
    }

    public double noise3(double xin, double yin, double zin) {
        xin += ox;
        yin += oy;
        zin += oz;
        double s = (xin + yin + zin) * F3;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);
        int k = fastFloor(zin + s);
        double t = (i + j + k) * G3;
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);
        double z0 = zin - (k - t);
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1;
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1;
            }
        } else {
            if (y0 < z0) {
                i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1;
            } else if (x0 < z0) {
                i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1;
            } else {
                i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            }
        }
        double x1 = x0 - i1 + G3;
        double y1 = y0 - j1 + G3;
        double z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3;
        double y2 = y0 - j2 + 2.0 * G3;
        double z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3;
        double y3 = y0 - 1.0 + 3.0 * G3;
        double z3 = z0 - 1.0 + 3.0 * G3;
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        double n = 0.0;
        double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 > 0) {
            int g = permMod12[ii + perm[jj + perm[kk]]];
            t0 *= t0;
            n += t0 * t0 * (GX[g] * x0 + GY[g] * y0 + GZ[g] * z0);
        }
        double t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 > 0) {
            int g = permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]];
            t1 *= t1;
            n += t1 * t1 * (GX[g] * x1 + GY[g] * y1 + GZ[g] * z1);
        }
        double t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 > 0) {
            int g = permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]];
            t2 *= t2;
            n += t2 * t2 * (GX[g] * x2 + GY[g] * y2 + GZ[g] * z2);
        }
        double t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 > 0) {
            int g = permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]];
            t3 *= t3;
            n += t3 * t3 * (GX[g] * x3 + GY[g] * y3 + GZ[g] * z3);
        }
        return 32.0 * n;
    }

    /** Fractal sum of 2D octaves normalised to roughly [-1, 1]. */
    public double fbm2(double x, double z, int octaves) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double freq = 1.0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise2(x * freq + o * 17.31, z * freq - o * 9.77);
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / norm;
    }

    /** Fractal 3D noise normalised to roughly [-1, 1]. */
    public double fbm3(double x, double y, double z, int octaves) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double freq = 1.0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise3(x * freq + o * 11.13, y * freq + o * 5.71, z * freq - o * 7.29);
            norm += amp;
            amp *= 0.5;
            freq *= 2.01;
        }
        return sum / norm;
    }

    /** Ridged fractal noise in [0, 1], 1 on the ridge lines. */
    public double ridged2(double x, double z, int octaves) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double freq = 1.0;
        double weight = 1.0;
        for (int o = 0; o < octaves; o++) {
            double n = 1.0 - Math.abs(noise2(x * freq + o * 31.7, z * freq + o * 13.3));
            n *= n;
            n *= weight;
            weight = Math.min(1.0, Math.max(0.0, n * 1.6));
            sum += amp * n;
            norm += amp;
            amp *= 0.5;
            freq *= 2.07;
        }
        return sum / norm;
    }
}
