package com.m4ytenn.barotraumaoceans.worldgen.core;

/**
 * Splits the seabed into warped Voronoi regions. Every region cell gets a {@link Province} picked by the
 * configured weights. Terrain blends smoothly between neighbouring regions (soft-min weights over the
 * nine closest cells), while biomes use the hard nearest cell.
 */
public final class ProvinceMap {
    private final long seed;
    private final double size;
    private final double tau;
    private final double warp;
    private final double warpScale;
    private final SimplexNoise warpX;
    private final SimplexNoise warpZ;
    private final double[] cumulative;
    private final double totalWeight;

    public ProvinceMap(long seed, OceanSettings s) {
        this.seed = seed;
        this.size = s.getInt(OceanSettings.PROVINCE_SIZE);
        this.tau = Math.max(2.0, s.getInt(OceanSettings.PROVINCE_BLEND) / 2.5);
        this.warp = s.getInt(OceanSettings.PROVINCE_WARP);
        this.warpScale = Math.max(64.0, size * 0.6);
        this.warpX = new SimplexNoise(Hashing.hash(seed, 0x51A1));
        this.warpZ = new SimplexNoise(Hashing.hash(seed, 0x51A2));
        Province[] ps = Province.VALUES;
        cumulative = new double[ps.length];
        double t = 0;
        for (int i = 0; i < ps.length; i++) {
            t += Math.max(0.0, s.getDouble(ps[i].weightParam));
            cumulative[i] = t;
        }
        if (t <= 0) {
            // Everything disabled: fall back to plains everywhere.
            cumulative[0] = 1.0;
            for (int i = 1; i < ps.length; i++) {
                cumulative[i] = 1.0;
            }
            t = 1.0;
        }
        totalWeight = t;
    }

    public Province provinceOfCell(long cx, long cz) {
        double r = Hashing.unit(Hashing.hash(seed, cx, cz, 0x9A0)) * totalWeight;
        for (int i = 0; i < cumulative.length; i++) {
            if (r < cumulative[i]) {
                return Province.VALUES[i];
            }
        }
        return Province.VALUES[Province.VALUES.length - 1];
    }

    /** Result of a region lookup. Weights are indexed by {@link Province#ordinal()} and sum to 1. */
    public static final class Sample {
        public final double[] weights = new double[Province.VALUES.length];
        public Province dominant = Province.ABYSSAL_PLAINS;
        public long cellX;
        public long cellZ;

        public double weight(Province p) {
            return weights[p.ordinal()];
        }

        public long cellId() {
            return Hashing.hash(cellX, cellZ);
        }
    }

    public void sample(double x, double z, Sample out) {
        double wx = x;
        double wz = z;
        if (warp > 0) {
            wx += warp * warpX.fbm2(x / warpScale, z / warpScale, 2);
            wz += warp * warpZ.fbm2(x / warpScale, z / warpScale, 2);
        }
        long cx = (long) Math.floor(wx / size);
        long cz = (long) Math.floor(wz / size);
        double[] dist = new double[9];
        long[] ids = new long[18];
        double best = Double.MAX_VALUE;
        int bestIdx = 0;
        int n = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                long gx = cx + i;
                long gz = cz + j;
                long h = Hashing.hash(seed, gx, gz, 0x7C3);
                double px = (gx + 0.5 + 0.8 * (Hashing.unit(h) - 0.5)) * size;
                double pz = (gz + 0.5 + 0.8 * (Hashing.unit(Hashing.mix(h)) - 0.5)) * size;
                double dx = wx - px;
                double dz = wz - pz;
                double d = Math.sqrt(dx * dx + dz * dz);
                dist[n] = d;
                ids[2 * n] = gx;
                ids[2 * n + 1] = gz;
                if (d < best) {
                    best = d;
                    bestIdx = n;
                }
                n++;
            }
        }
        java.util.Arrays.fill(out.weights, 0.0);
        double total = 0;
        for (int k = 0; k < 9; k++) {
            double w = Math.exp(-(dist[k] - best) / tau);
            if (w < 1e-4) {
                continue;
            }
            Province p = provinceOfCell(ids[2 * k], ids[2 * k + 1]);
            out.weights[p.ordinal()] += w;
            total += w;
        }
        for (int k = 0; k < out.weights.length; k++) {
            out.weights[k] /= total;
        }
        out.cellX = ids[2 * bestIdx];
        out.cellZ = ids[2 * bestIdx + 1];
        out.dominant = provinceOfCell(out.cellX, out.cellZ);
    }
}
