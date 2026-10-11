package com.m4ytenn.barotraumaoceans.worldgen.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Long, deep, V-shaped cracks in the seabed. */
public final class Ravines {

    public static final class Ravine {
        public final double x0, z0, ux, uz, length;
        public final double halfWidth, depth;
        public final double meanderAmp, meanderFreq, meanderPhase, ledgePhase;
        public final double minX, maxX, minZ, maxZ;
        public final long hash;

        Ravine(double x0, double z0, double ux, double uz, double length, double halfWidth, double depth,
               double meanderAmp, double meanderFreq, double meanderPhase, double ledgePhase, long hash) {
            this.x0 = x0;
            this.z0 = z0;
            this.ux = ux;
            this.uz = uz;
            this.length = length;
            this.halfWidth = halfWidth;
            this.depth = depth;
            this.meanderAmp = meanderAmp;
            this.meanderFreq = meanderFreq;
            this.meanderPhase = meanderPhase;
            this.ledgePhase = ledgePhase;
            this.hash = hash;
            double x1 = x0 + ux * length;
            double z1 = z0 + uz * length;
            double m = halfWidth + meanderAmp + 8;
            minX = Math.min(x0, x1) - m;
            maxX = Math.max(x0, x1) + m;
            minZ = Math.min(z0, z1) - m;
            maxZ = Math.max(z0, z1) + m;
        }
    }

    /** Column information relative to one ravine. */
    public static final class RavineHit {
        public Ravine ravine;
        public double lateral;
        public double hwTop;
        public double bottom;
        public double top;
    }

    private static final Object NONE = new Object();

    private final long seed;
    private final SeabedModel seabed;
    private final boolean enabled;
    private final double spacing;
    private final double chance;
    private final double minLen, maxLen, minW, maxW, minD, maxD;
    private final Map<Long, Object> cache = new ConcurrentHashMap<>();

    public Ravines(long seed, OceanSettings s, SeabedModel seabed) {
        this.seed = seed;
        this.seabed = seabed;
        this.enabled = s.getBool(OceanSettings.RAVINES_ENABLED);
        this.spacing = s.getInt(OceanSettings.RAVINE_SPACING);
        this.chance = s.getDouble(OceanSettings.RAVINE_CHANCE);
        this.minLen = s.getInt(OceanSettings.RAVINE_MIN_LENGTH);
        this.maxLen = s.getInt(OceanSettings.RAVINE_MAX_LENGTH);
        this.minW = s.getInt(OceanSettings.RAVINE_MIN_WIDTH);
        this.maxW = s.getInt(OceanSettings.RAVINE_MAX_WIDTH);
        this.minD = s.getInt(OceanSettings.RAVINE_MIN_DEPTH);
        this.maxD = s.getInt(OceanSettings.RAVINE_MAX_DEPTH);
    }

    private Ravine cell(long i, long j) {
        long k = (i << 32) ^ (j & 0xFFFFFFFFL);
        Object o = cache.get(k);
        if (o == null) {
            if (cache.size() > 20000) {
                cache.clear();
            }
            o = build(i, j);
            if (o == null) {
                o = NONE;
            }
            cache.put(k, o);
        }
        return o == NONE ? null : (Ravine) o;
    }

    private Ravine build(long i, long j) {
        long h = Hashing.hash(seed, i, j, 0x2A71);
        Hashing.Stream r = new Hashing.Stream(h);
        if (!r.chance(chance)) {
            return null;
        }
        double cx = (i + 0.5 + 0.3 * r.signed()) * spacing;
        double cz = (j + 0.5 + 0.3 * r.signed()) * spacing;
        double angle = r.next() * Math.PI;
        double len = r.range(minLen, maxLen);
        double ux = Math.cos(angle);
        double uz = Math.sin(angle);
        double hw = r.range(minW, maxW) * 0.5;
        double depth = r.range(minD, maxD);
        return new Ravine(cx - ux * len * 0.5, cz - uz * len * 0.5, ux, uz, len, hw, depth,
                r.range(8, 30), r.range(1.0, 2.5), r.next() * Math.PI * 2, r.next() * Math.PI * 2, h);
    }

    public void near(double x, double z, List<Ravine> out) {
        if (!enabled) {
            return;
        }
        long ci = (long) Math.floor(x / spacing);
        long cj = (long) Math.floor(z / spacing);
        for (long i = ci - 1; i <= ci + 1; i++) {
            for (long j = cj - 1; j <= cj + 1; j++) {
                Ravine rv = cell(i, j);
                if (rv != null && x >= rv.minX && x <= rv.maxX && z >= rv.minZ && z <= rv.maxZ) {
                    out.add(rv);
                }
            }
        }
    }

    /** Fills the hit for a column; returns false if the column is outside the ravine footprint. */
    public boolean hit(Ravine rv, double x, double z, double localSeabed, RavineHit out) {
        double dx = x - rv.x0;
        double dz = z - rv.z0;
        double u = dx * rv.ux + dz * rv.uz;
        double s = u / rv.length;
        if (s <= 0 || s >= 1) {
            return false;
        }
        double profile = Math.sqrt(Math.max(0.0, 1.0 - Math.pow(2 * s - 1, 4)));
        double meander = rv.meanderAmp * Math.sin(2 * Math.PI * rv.meanderFreq * s + rv.meanderPhase) * Math.sin(Math.PI * s);
        double lateral = -dx * rv.uz + dz * rv.ux - meander;
        double hwTop = rv.halfWidth * profile;
        if (Math.abs(lateral) > hwTop + 6) {
            return false;
        }
        double cxl = rv.x0 + rv.ux * u - rv.uz * meander;
        double czl = rv.z0 + rv.uz * u + rv.ux * meander;
        double centreBed = seabed.seabed(cxl, czl);
        out.ravine = rv;
        out.lateral = lateral;
        out.hwTop = hwTop;
        out.top = Math.max(localSeabed, centreBed) + 4;
        out.bottom = centreBed - rv.depth * Math.sqrt(profile);
        return true;
    }

    /** Signed distance-like value at height y (negative = inside the ravine). */
    public static double sdf(RavineHit h, double y) {
        if (y > h.top) {
            return 1e9;
        }
        double span = h.top - h.bottom;
        double frac = span <= 0 ? 0 : (y - h.bottom) / span;
        if (frac < 0) {
            return h.bottom - y + Math.abs(h.lateral) * 0.2;
        }
        double hw = h.hwTop * Math.pow(Math.min(1.0, frac), 0.45);
        hw += 1.5 * Math.sin(y * 0.35 + h.ravine.ledgePhase);
        return Math.abs(h.lateral) - hw;
    }
}
