package com.m4ytenn.barotraumaoceans.worldgen.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Solid shapes added on top of the seabed: big columns (Pillar Forest), spikes and tall spires
 * (Spire Fields) and rock formations - boulders, mesas, arches and leaning slabs (Monolith Reach).
 * Every shape is evaluated as a "solid distance" (positive = inside rock).
 */
public final class Formations {

    public interface Shape {
        /** Positive inside the rock, negative outside (roughly in blocks). */
        double solid(double x, double y, double z);

        /** Horizontal bounding box test. */
        boolean mayContain(double x, double z);

        double minY();

        double maxY();
    }

    // ------------------------------------------------------------------------------------------------

    static final class Column implements Shape {
        final double x, z, r0, base, top, leanX, leanZ, flutePhase, wobblePhase, bound;
        final int flutes;
        final boolean reachesCeiling;

        Column(double x, double z, double r0, double base, double top, double leanX, double leanZ, int flutes,
               double flutePhase, double wobblePhase, boolean reachesCeiling) {
            this.x = x;
            this.z = z;
            this.r0 = r0;
            this.base = base;
            this.top = top;
            this.leanX = leanX;
            this.leanZ = leanZ;
            this.flutes = flutes;
            this.flutePhase = flutePhase;
            this.wobblePhase = wobblePhase;
            this.reachesCeiling = reachesCeiling;
            double lean = Math.max(Math.abs(leanX), Math.abs(leanZ)) * (top - base);
            this.bound = r0 * 2.2 + lean + 2;
        }

        @Override
        public double solid(double px, double py, double pz) {
            if (py < base - 4 || py > top + 4) {
                return -1e9;
            }
            double h = py - base;
            double cx = x + leanX * h;
            double cz = z + leanZ * h;
            double dx = px - cx;
            double dz = pz - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            double r = r0 * (1 + 0.8 * Math.exp(-Math.max(0, h) / 14.0));
            if (reachesCeiling) {
                r += r0 * 0.9 * Math.exp(-Math.max(0, top - py) / 18.0);
            } else if (py > top - r0) {
                double q = (py - (top - r0)) / r0;
                r *= Math.sqrt(Math.max(0, 1 - q * q));
            }
            r *= 1 + 0.1 * Math.sin(py / 23.0 + wobblePhase);
            if (flutes > 0 && d > 0.01) {
                d *= 1 + 0.07 * Math.sin(flutes * Math.atan2(dz, dx) + flutePhase);
            }
            return r - d;
        }

        @Override
        public boolean mayContain(double px, double pz) {
            return Math.abs(px - x) < bound && Math.abs(pz - z) < bound;
        }

        @Override
        public double minY() {
            return base - 4;
        }

        @Override
        public double maxY() {
            return top + 4;
        }
    }

    static final class Spike implements Shape {
        final double x, z, base, height, rb, tiltX, tiltZ, bound;

        Spike(double x, double z, double base, double height, double rb, double tiltX, double tiltZ) {
            this.x = x;
            this.z = z;
            this.base = base;
            this.height = height;
            this.rb = rb;
            this.tiltX = tiltX;
            this.tiltZ = tiltZ;
            this.bound = rb + Math.max(Math.abs(tiltX), Math.abs(tiltZ)) * height + 1.5;
        }

        @Override
        public double solid(double px, double py, double pz) {
            double h = py - base;
            if (h < -3 || h > height) {
                return -1e9;
            }
            double r = rb * Math.pow(Math.max(0.0, 1.0 - Math.max(0, h) / height), 1.25);
            double dx = px - (x + tiltX * h);
            double dz = pz - (z + tiltZ * h);
            return r - Math.sqrt(dx * dx + dz * dz);
        }

        @Override
        public boolean mayContain(double px, double pz) {
            return Math.abs(px - x) < bound && Math.abs(pz - z) < bound;
        }

        @Override
        public double minY() {
            return base - 3;
        }

        @Override
        public double maxY() {
            return base + height;
        }
    }

    static final class Rock implements Shape {
        static final int BOULDER = 0, MESA = 1, ARCH = 2, SLAB = 3;
        final int type;
        final double x, z, base, cos, sin, a, b, c, bound, top, tiltCos, tiltSin;

        Rock(int type, double x, double z, double base, double angle, double a, double b, double c, double tilt) {
            this.type = type;
            this.x = x;
            this.z = z;
            this.base = base;
            this.cos = Math.cos(angle);
            this.sin = Math.sin(angle);
            this.a = a;
            this.b = b;
            this.c = c;
            this.tiltCos = Math.cos(tilt);
            this.tiltSin = Math.sin(tilt);
            this.bound = switch (type) {
                case BOULDER -> Math.max(a, c) + 2;
                case MESA -> a * 1.25 + 2;
                case ARCH -> a + b + 2;
                default -> Math.sqrt(a * a + c * c) + b * Math.abs(tiltSin) + 2;
            };
            this.top = switch (type) {
                case BOULDER -> base + b * 1.3 + 2;
                case MESA -> base + b + 2;
                case ARCH -> base + a + c + 2;
                default -> base + b * 2 + 2;
            };
        }

        @Override
        public double solid(double px, double py, double pz) {
            double dx = px - x;
            double dz = pz - z;
            double lu = dx * cos + dz * sin;
            double lw = -dx * sin + dz * cos;
            double ly = py - base;
            switch (type) {
                case BOULDER: {
                    double cy = ly - b * 0.3;
                    double e = Math.sqrt((lu / a) * (lu / a) + (cy / b) * (cy / b) + (lw / c) * (lw / c));
                    return (1 - e) * Math.min(a, Math.min(b, c));
                }
                case MESA: {
                    if (ly > b) {
                        return b - ly;
                    }
                    double hf = Math.max(0, ly) / b;
                    double r = a * (hf < 0.3 ? 0.72 + 0.28 * hf / 0.3 : 1.0) * (1 + 0.08 * Math.sin(ly / 5.0));
                    double d = Math.sqrt(lu * lu + lw * lw);
                    return Math.min(r - d, b - ly);
                }
                case ARCH: {
                    // Half torus standing on the seabed: a = major radius, b = tube radius, c = sink depth.
                    double vy = ly + c;
                    double q = Math.sqrt(lu * lu + vy * vy) - a;
                    double d = Math.sqrt(q * q + lw * lw) - b;
                    return -d;
                }
                default: {
                    // Leaning slab: box with half sizes (a thickness, b height, c width), tilted around its long axis.
                    double ry = ly - b * 0.8;
                    double tu = lu * tiltCos - ry * tiltSin;
                    double ty = lu * tiltSin + ry * tiltCos;
                    double qx = Math.abs(tu) - a;
                    double qy = Math.abs(ty) - b;
                    double qz = Math.abs(lw) - c;
                    double outside = Math.sqrt(Math.max(qx, 0) * Math.max(qx, 0) + Math.max(qy, 0) * Math.max(qy, 0) + Math.max(qz, 0) * Math.max(qz, 0));
                    double inside = Math.min(Math.max(qx, Math.max(qy, qz)), 0);
                    return -(outside + inside);
                }
            }
        }

        @Override
        public boolean mayContain(double px, double pz) {
            return Math.abs(px - x) < bound && Math.abs(pz - z) < bound;
        }

        @Override
        public double minY() {
            return base - Math.max(c, 6) - 4;
        }

        @Override
        public double maxY() {
            return top;
        }
    }

    // ------------------------------------------------------------------------------------------------

    private static final Object NONE = new Object();

    private final long seed;
    private final SeabedModel seabed;
    private final ProvinceMap provinces;

    private final double pillarSpacing, pillarChanceForest, pillarChanceElse, pillarMinR, pillarMaxR, pillarMaxH;
    private final double spikeDensity, spikeMaxH, spireChance, spireMaxH;
    private final double rockSpacing, rockChanceReach, rockChanceElse, rockScale;
    private final double waterLevel;

    private static final double SPIKE_CELL = 12;
    private static final double SPIRE_CELL = 48;

    private final Map<Long, Object> pillarCache = new ConcurrentHashMap<>();
    private final Map<Long, Object> spikeCache = new ConcurrentHashMap<>();
    private final Map<Long, Object> spireCache = new ConcurrentHashMap<>();
    private final Map<Long, Object> rockCache = new ConcurrentHashMap<>();

    public Formations(long seed, OceanSettings s, SeabedModel seabed) {
        this.seed = seed;
        this.seabed = seabed;
        this.provinces = seabed.provinces();
        this.pillarSpacing = s.getInt(OceanSettings.PILLAR_SPACING);
        this.pillarChanceForest = s.getDouble(OceanSettings.PILLAR_CHANCE_FOREST);
        this.pillarChanceElse = s.getDouble(OceanSettings.PILLAR_CHANCE_ELSEWHERE);
        this.pillarMinR = s.getInt(OceanSettings.PILLAR_MIN_RADIUS);
        this.pillarMaxR = s.getInt(OceanSettings.PILLAR_MAX_RADIUS);
        this.pillarMaxH = s.getInt(OceanSettings.PILLAR_MAX_HEIGHT);
        this.spikeDensity = s.getDouble(OceanSettings.SPIKE_DENSITY);
        this.spikeMaxH = s.getInt(OceanSettings.SPIKE_MAX_HEIGHT);
        this.spireChance = s.getDouble(OceanSettings.SPIRE_CHANCE);
        this.spireMaxH = s.getInt(OceanSettings.SPIRE_MAX_HEIGHT);
        this.rockSpacing = s.getInt(OceanSettings.ROCK_SPACING);
        this.rockChanceReach = s.getDouble(OceanSettings.ROCK_CHANCE_REACH);
        this.rockChanceElse = s.getDouble(OceanSettings.ROCK_CHANCE_ELSEWHERE);
        this.rockScale = s.getDouble(OceanSettings.ROCK_SCALE);
        this.waterLevel = s.getInt(OceanSettings.WATER_LEVEL);
    }

    private static long key(long i, long j) {
        return (i << 32) ^ (j & 0xFFFFFFFFL);
    }

    private static Object getOrBuild(Map<Long, Object> cache, long i, long j, java.util.function.Supplier<Shape> builder) {
        long k = key(i, j);
        Object o = cache.get(k);
        if (o == null) {
            if (cache.size() > 40000) {
                cache.clear();
            }
            Shape s = builder.get();
            o = s == null ? NONE : s;
            cache.put(k, o);
        }
        return o;
    }

    private ProvinceMap.Sample sampleAt(double x, double z) {
        ProvinceMap.Sample ps = new ProvinceMap.Sample();
        provinces.sample(x, z, ps);
        return ps;
    }

    private Shape buildPillar(long i, long j) {
        Hashing.Stream r = new Hashing.Stream(Hashing.hash(seed, i, j, 0xC011));
        double x = (i + 0.5 + 0.7 * (r.next() - 0.5)) * pillarSpacing;
        double z = (j + 0.5 + 0.7 * (r.next() - 0.5)) * pillarSpacing;
        ProvinceMap.Sample ps = sampleAt(x, z);
        double w = ps.weight(Province.PILLAR_FOREST);
        double chance = pillarChanceElse + (pillarChanceForest - pillarChanceElse) * w;
        if (!r.chance(chance)) {
            return null;
        }
        double r0 = pillarMinR + (pillarMaxR - pillarMinR) * Math.pow(r.next(), 1.5);
        double base = seabed.seabed(x, z, ps) - 6;
        double top;
        boolean ceiling = seabed.iceEnabled;
        if (ceiling) {
            top = seabed.iceBottom(x, z) + 6;
        } else {
            double room = Math.max(10, waterLevel - 6 - base);
            top = base + Math.min(pillarMaxH, room) * r.range(0.45, 1.0);
        }
        if (top - base < 12) {
            return null;
        }
        int flutes = r.chance(0.5) ? r.rangeInt(5, 9) : 0;
        return new Column(x, z, r0, base, top, r.signed() * 0.08, r.signed() * 0.08, flutes,
                r.next() * Math.PI * 2, r.next() * Math.PI * 2, ceiling);
    }

    private Shape buildSpike(long i, long j) {
        Hashing.Stream r = new Hashing.Stream(Hashing.hash(seed, i, j, 0x5B1C));
        double x = (i + 0.5 + 0.8 * (r.next() - 0.5)) * SPIKE_CELL;
        double z = (j + 0.5 + 0.8 * (r.next() - 0.5)) * SPIKE_CELL;
        double roll = r.next();
        if (roll > spikeDensity) {
            return null;
        }
        ProvinceMap.Sample ps = sampleAt(x, z);
        double w = ps.weight(Province.SPIRE_FIELDS) + 0.15 * ps.weight(Province.HYDROTHERMAL_WASTES) + 0.1 * ps.weight(Province.EUROPAN_RIDGE);
        if (r.next() > w) {
            return null;
        }
        double h = 6 + (spikeMaxH - 6) * Math.pow(r.next(), 1.6);
        double rb = Math.max(2.5, Math.min(9.0, h * r.range(0.13, 0.24)));
        double base = seabed.seabed(x, z, ps) - 2;
        return new Spike(x, z, base, h, rb, r.signed() * 0.12, r.signed() * 0.12);
    }

    private Shape buildSpire(long i, long j) {
        Hashing.Stream r = new Hashing.Stream(Hashing.hash(seed, i, j, 0x5B12));
        double x = (i + 0.5 + 0.7 * (r.next() - 0.5)) * SPIRE_CELL;
        double z = (j + 0.5 + 0.7 * (r.next() - 0.5)) * SPIRE_CELL;
        ProvinceMap.Sample ps = sampleAt(x, z);
        double chance = spireChance * ps.weight(Province.SPIRE_FIELDS) + 0.04 * ps.weight(Province.MONOLITH_REACH)
                + 0.03 * ps.weight(Province.EUROPAN_RIDGE);
        if (!r.chance(chance)) {
            return null;
        }
        double h = r.range(Math.min(40, spireMaxH * 0.5), spireMaxH);
        if (seabed.iceEnabled) {
            h = Math.min(h, seabed.iceBottom(x, z) - seabed.seabed(x, z, ps) - 6);
        }
        if (h < 16) {
            return null;
        }
        double rb = r.range(8, 18);
        double base = seabed.seabed(x, z, ps) - 3;
        return new Spike(x, z, base, h, rb, r.signed() * 0.1, r.signed() * 0.1);
    }

    private Shape buildRock(long i, long j) {
        Hashing.Stream r = new Hashing.Stream(Hashing.hash(seed, i, j, 0x80C7));
        double x = (i + 0.5 + 0.7 * (r.next() - 0.5)) * rockSpacing;
        double z = (j + 0.5 + 0.7 * (r.next() - 0.5)) * rockSpacing;
        ProvinceMap.Sample ps = sampleAt(x, z);
        double w = ps.weight(Province.MONOLITH_REACH);
        double chance = rockChanceElse + (rockChanceReach - rockChanceElse) * w;
        if (!r.chance(chance)) {
            return null;
        }
        double base = seabed.seabed(x, z, ps);
        double k = rockScale;
        double roll = r.next();
        double angle = r.next() * Math.PI;
        if (roll < 0.35) {
            return new Rock(Rock.BOULDER, x, z, base, angle, k * r.range(8, 20), k * r.range(6, 15), k * r.range(8, 20), 0);
        } else if (roll < 0.6) {
            return new Rock(Rock.MESA, x, z, base, angle, k * r.range(8, 20), k * r.range(24, 70), 0, 0);
        } else if (roll < 0.8) {
            return new Rock(Rock.ARCH, x, z, base, angle, k * r.range(14, 34), k * r.range(4, 9), k * r.range(2, 6), 0);
        } else {
            return new Rock(Rock.SLAB, x, z, base, angle, k * r.range(3, 6), k * r.range(15, 40), k * r.range(10, 20),
                    Math.toRadians(r.range(10, 30)) * (r.chance(0.5) ? 1 : -1));
        }
    }

    /** Collects shapes that may reach the column (x, z). */
    public void near(double x, double z, List<Shape> out) {
        gather(pillarCache, pillarSpacing, pillarMaxR * 2.2 + 0.08 * 700 + 2, x, z, this::buildPillar, out, pillarChanceForest > 0 || pillarChanceElse > 0);
        gather(spikeCache, SPIKE_CELL, 9.0 + 0.12 * spikeMaxH + 1.5, x, z, this::buildSpike, out, spikeDensity > 0);
        gather(spireCache, SPIRE_CELL, 18 + 0.1 * spireMaxH + 1.5, x, z, this::buildSpire, out, spireChance > 0);
        gather(rockCache, rockSpacing, rockScale * 45 + 2, x, z, this::buildRock, out, rockChanceReach > 0 || rockChanceElse > 0);
    }

    private interface CellBuilder {
        Shape build(long i, long j);
    }

    private static void gather(Map<Long, Object> cache, double cell, double maxBound, double x, double z, CellBuilder builder, List<Shape> out, boolean active) {
        if (!active) {
            return;
        }
        long ci = (long) Math.floor(x / cell);
        long cj = (long) Math.floor(z / cell);
        int reach = Math.max(1, (int) Math.ceil(maxBound / cell + 0.4));
        for (long i = ci - reach; i <= ci + reach; i++) {
            for (long j = cj - reach; j <= cj + reach; j++) {
                final long fi = i, fj = j;
                Object o = getOrBuild(cache, i, j, () -> builder.build(fi, fj));
                if (o != NONE) {
                    Shape s = (Shape) o;
                    if (s.mayContain(x, z)) {
                        out.add(s);
                    }
                }
            }
        }
    }
}
