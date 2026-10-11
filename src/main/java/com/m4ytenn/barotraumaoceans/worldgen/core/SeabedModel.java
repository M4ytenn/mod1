package com.m4ytenn.barotraumaoceans.worldgen.core;

/**
 * Height fields: the seabed surface (blended between regions) and the ice crust (underside and top).
 */
public final class SeabedModel {
    private final ProvinceMap provinces;
    private final double seabedLevel;
    private final SimplexNoise base;
    private final SimplexNoise detailA;
    private final SimplexNoise detailB;
    private final SimplexNoise ridge;
    private final SimplexNoise mounds;
    private final SimplexNoise iceBottomNoise;
    private final SimplexNoise iceKeelNoise;
    private final SimplexNoise iceTopNoise;

    public final boolean iceEnabled;
    private final double iceBottom;
    private final double iceBottomVar;
    private final double iceTop;
    private final double iceTopVar;
    private final double keelDepth;
    private final double keelScale;

    public SeabedModel(long seed, OceanSettings s, ProvinceMap provinces) {
        this.provinces = provinces;
        this.seabedLevel = s.getInt(OceanSettings.SEABED_LEVEL);
        this.base = new SimplexNoise(Hashing.hash(seed, 0x5EA1));
        this.detailA = new SimplexNoise(Hashing.hash(seed, 0x5EA2));
        this.detailB = new SimplexNoise(Hashing.hash(seed, 0x5EA3));
        this.ridge = new SimplexNoise(Hashing.hash(seed, 0x5EA4));
        this.mounds = new SimplexNoise(Hashing.hash(seed, 0x5EA5));
        this.iceBottomNoise = new SimplexNoise(Hashing.hash(seed, 0x1CE1));
        this.iceKeelNoise = new SimplexNoise(Hashing.hash(seed, 0x1CE2));
        this.iceTopNoise = new SimplexNoise(Hashing.hash(seed, 0x1CE3));
        this.iceEnabled = s.getBool(OceanSettings.ICE_ENABLED);
        this.iceBottom = s.getInt(OceanSettings.ICE_BOTTOM);
        this.iceBottomVar = s.getInt(OceanSettings.ICE_BOTTOM_VARIATION);
        this.iceTop = s.getInt(OceanSettings.ICE_TOP);
        this.iceTopVar = s.getInt(OceanSettings.ICE_TOP_VARIATION);
        this.keelDepth = s.getInt(OceanSettings.ICE_KEEL_DEPTH);
        this.keelScale = s.getInt(OceanSettings.ICE_KEEL_SCALE);
    }

    public ProvinceMap provinces() {
        return provinces;
    }

    /** Seabed height using a pre-computed region sample. */
    public double seabed(double x, double z, ProvinceMap.Sample ps) {
        double h = seabedLevel + 10.0 * base.fbm2(x / 420.0, z / 420.0, 3);
        double[] w = ps.weights;
        for (Province p : Province.VALUES) {
            double weight = w[p.ordinal()];
            if (weight < 1e-3) {
                continue;
            }
            h += weight * regionHeight(p, x, z);
        }
        return h;
    }

    /** Seabed height computing the region sample internally (slower, for one-off queries). */
    public double seabed(double x, double z) {
        ProvinceMap.Sample ps = new ProvinceMap.Sample();
        provinces.sample(x, z, ps);
        return seabed(x, z, ps);
    }

    private double regionHeight(Province p, double x, double z) {
        return switch (p) {
            case ABYSSAL_PLAINS -> 2.5 * detailA.fbm2(x / 160.0, z / 160.0, 2) + 1.0 * detailB.noise2(x / 38.0, z / 38.0);
            case SPIRE_FIELDS -> 8.0 + 6.0 * ridge.ridged2(x / 70.0, z / 70.0, 3);
            case PILLAR_FOREST -> -10.0 + 6.0 * detailA.fbm2(x / 120.0, z / 120.0, 3);
            case MONOLITH_REACH -> 4.0 + 10.0 * detailB.fbm2(x / 150.0, z / 150.0, 3);
            case KELP_FOREST -> 16.0 + 7.0 * detailA.fbm2(x / 130.0, z / 130.0, 3);
            case CORAL_GARDENS -> 26.0 + 5.0 * detailB.fbm2(x / 100.0, z / 100.0, 3)
                    + 7.0 * Math.max(0.0, mounds.fbm2(x / 42.0, z / 42.0, 2));
            case EUROPAN_RIDGE -> {
                double rr = ridge.ridged2(x / 300.0, z / 300.0, 4);
                double t = Math.max(0.0, Math.min(1.0, (rr - 0.3) / 0.5));
                yield 20.0 + 150.0 * t * t * (3 - 2 * t) + 6.0 * detailB.fbm2(x / 60.0, z / 60.0, 2);
            }
            case HYDROTHERMAL_WASTES -> {
                // Terraced basalt flows.
                double hh = 18.0 * detailA.fbm2(x / 110.0, z / 110.0, 3);
                double terraced = Math.floor(hh / 5.0) * 5.0 + 1.5 * (hh / 5.0 - Math.floor(hh / 5.0));
                yield -16.0 + terraced;
            }
            case HADAL_TRENCH -> -150.0 + 26.0 * detailB.fbm2(x / 200.0, z / 200.0, 3);
        };
    }

    public double iceBottom(double x, double z) {
        double h = iceBottom + iceBottomVar * iceBottomNoise.fbm2(x / 180.0, z / 180.0, 3);
        if (keelDepth > 0) {
            double r = iceKeelNoise.ridged2(x / keelScale, z / keelScale, 2);
            h -= keelDepth * r * r * r * r;
        }
        return h;
    }

    public double iceTop(double x, double z) {
        return iceTop + iceTopVar * iceTopNoise.fbm2(x / 240.0, z / 240.0, 3);
    }
}
