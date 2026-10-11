package com.m4ytenn.barotraumaoceans.worldgen.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The complete Barotrauma Oceans terrain as a pure function of (seed, settings).
 * <p>
 * Usage pattern: fetch a {@link Column} for an (x, z) position once, then evaluate {@link #density}
 * or {@link #slot} for many y values. Columns are cached per thread.
 */
public final class OceanTerrain {

    private static final Map<String, OceanTerrain> INSTANCES = new ConcurrentHashMap<>();

    /**
     * @param dryPockets whether rock pockets exist (only when their biome slot resolved to at least one biome,
     *                   e.g. Alex's Caves is installed). Pockets are carved as sealed, air-filled caverns.
     */
    public static OceanTerrain get(long seed, OceanSettings settings, boolean dryPockets) {
        String key = seed + "|" + dryPockets + "|" + settings.hashCode();
        OceanTerrain t = INSTANCES.get(key);
        if (t == null || t.seed != seed || t.dryPocketsRequested != dryPockets || !t.settings.equals(settings)) {
            if (INSTANCES.size() > 8) {
                INSTANCES.clear();
            }
            t = new OceanTerrain(seed, settings, dryPockets);
            INSTANCES.put(key, t);
        }
        return t;
    }

    public final long seed;
    public final OceanSettings settings;
    public final ProvinceMap provinces;
    public final SeabedModel seabed;
    public final TunnelNetwork network;
    public final Ravines ravines;
    public final Formations formations;

    private final SimplexNoise roughNoise;
    private final SimplexNoise roughDetail;
    private final SimplexNoise overhangNoise;
    private final SimplexNoise crewNoise;
    /** Noise for material painting (strata, sediment mixing, ice veins). */
    public final SimplexNoise materialNoise;
    /** General purpose decoration noise. */
    public final SimplexNoise decorationNoise;
    public final boolean ice;
    public final int waterLevel;
    private final double roughness;
    private final double floorCut;
    private final double clear;
    private final double overhangMul;
    private final double seabedBiomeHeight;
    private final double holeChance;
    private final double pocketSize;
    private final double pocketChance;
    public final boolean pocketsActive;
    /** Columns with {@link Column#pocketQ} below this may contain a dry pocket cavern. */
    public static final double POCKET_REACH = 0.95;
    private final boolean dryPocketsRequested;
    private final Map<Long, double[]> pocketCache = new ConcurrentHashMap<>();
    private static final double[] NO_POCKET = new double[0];

    private final ThreadLocal<Column[]> columnCache = ThreadLocal.withInitial(() -> new Column[256]);

    private OceanTerrain(long seed, OceanSettings s, boolean dryPockets) {
        this.seed = seed;
        this.settings = s;
        this.provinces = new ProvinceMap(Hashing.hash(seed, 1), s);
        this.seabed = new SeabedModel(Hashing.hash(seed, 2), s, provinces);
        this.network = new TunnelNetwork(Hashing.hash(seed, 3), s, seabed);
        this.ravines = new Ravines(Hashing.hash(seed, 4), s, seabed);
        this.formations = new Formations(Hashing.hash(seed, 5), s, seabed);
        this.roughNoise = new SimplexNoise(Hashing.hash(seed, 6));
        this.roughDetail = new SimplexNoise(Hashing.hash(seed, 7));
        this.overhangNoise = new SimplexNoise(Hashing.hash(seed, 8));
        this.crewNoise = new SimplexNoise(Hashing.hash(seed, 9));
        this.materialNoise = new SimplexNoise(Hashing.hash(seed, 10));
        this.decorationNoise = new SimplexNoise(Hashing.hash(seed, 11));
        this.ice = s.getBool(OceanSettings.ICE_ENABLED);
        this.waterLevel = s.getInt(OceanSettings.WATER_LEVEL);
        this.roughness = s.getDouble(OceanSettings.WALL_ROUGHNESS);
        this.floorCut = s.getDouble(OceanSettings.FLOOR_FLATNESS);
        this.clear = s.getInt(OceanSettings.CLEAR_RADIUS);
        this.overhangMul = s.getDouble(OceanSettings.OVERHANGS);
        this.seabedBiomeHeight = s.getInt(OceanSettings.SEABED_BIOME_HEIGHT);
        this.holeChance = s.getDouble(OceanSettings.ICE_HOLE_CHANCE);
        this.pocketSize = s.getInt(OceanSettings.POCKET_SIZE);
        this.pocketChance = s.getDouble(OceanSettings.POCKET_CHANCE);
        this.dryPocketsRequested = dryPockets;
        this.pocketsActive = dryPockets && pocketChance > 0 && !s.getList(OceanSettings.B_POCKETS).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------
    // Columns
    // ------------------------------------------------------------------------------------------------

    public static final class StationHit {
        public TunnelNetwork.Station station;
        public double q;
        public double[] colDist = new double[8];
        public double shaftReach;
    }

    public static final class Column {
        public int x;
        public int z;
        boolean valid;
        public final ProvinceMap.Sample ps = new ProvinceMap.Sample();
        public double seabed;
        public double iceBottom;
        public double iceTop;
        public double overhang;
        public double holeSdf = 1e9;
        public double pocketQ = 1e9;
        public double pocketCy;
        public double pocketRv;
        public long pocketHash;

        public final List<TunnelNetwork.TunnelHit> tunnelHits = new ArrayList<>();
        public final List<double[]> tunnelPillars = new ArrayList<>();
        private final List<TunnelNetwork.TunnelHit> hitPool = new ArrayList<>();
        public final List<StationHit> stationHits = new ArrayList<>();
        private final List<StationHit> stationPool = new ArrayList<>();
        public final List<Ravines.RavineHit> ravineHits = new ArrayList<>();
        private final List<Ravines.RavineHit> ravinePool = new ArrayList<>();
        public final List<Formations.Shape> shapes = new ArrayList<>();
        public double carveMinY;
        public double carveMaxY;

        final List<TunnelNetwork.Tunnel> tmpTunnels = new ArrayList<>();
        final List<TunnelNetwork.Station> tmpStations = new ArrayList<>();
        final List<Ravines.Ravine> tmpRavines = new ArrayList<>();
    }

    public Column column(int x, int z) {
        Column[] cache = columnCache.get();
        int idx = (int) ((Hashing.mix(((long) x << 32) ^ (z & 0xFFFFFFFFL)) >>> 7) & 255);
        Column c = cache[idx];
        if (c == null) {
            c = new Column();
            cache[idx] = c;
        } else if (c.valid && c.x == x && c.z == z) {
            return c;
        }
        fill(c, x, z);
        return c;
    }

    private void fill(Column c, int x, int z) {
        c.x = x;
        c.z = z;
        c.valid = true;
        provinces.sample(x, z, c.ps);
        c.seabed = seabed.seabed(x, z, c.ps);
        double oh = 0;
        for (Province p : Province.VALUES) {
            oh += c.ps.weights[p.ordinal()] * p.overhang;
        }
        c.overhang = oh * overhangMul;
        if (ice) {
            c.iceBottom = seabed.iceBottom(x, z);
            c.iceTop = seabed.iceTop(x, z);
            c.holeSdf = holeChance > 0 ? holeSdf(x, z) : 1e9;
        } else {
            c.iceBottom = Double.MAX_VALUE;
            c.iceTop = Double.MAX_VALUE;
            c.holeSdf = 1e9;
        }

        c.shapes.clear();
        formations.near(x, z, c.shapes);

        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        double margin = roughness + 3;

        // Channels
        c.tunnelHits.clear();
        c.tunnelPillars.clear();
        c.tmpTunnels.clear();
        network.tunnelsNear(x, z, c.tmpTunnels);
        int used = 0;
        for (TunnelNetwork.Tunnel t : c.tmpTunnels) {
            if (used >= c.hitPool.size()) {
                c.hitPool.add(new TunnelNetwork.TunnelHit());
            }
            TunnelNetwork.TunnelHit hit = c.hitPool.get(used);
            if (network.nearest(t, x, z, hit)) {
                used++;
                c.tunnelHits.add(hit);
                lo = Math.min(lo, hit.yc - hit.rv - margin);
                hi = Math.max(hi, hit.yc + hit.rv + margin);
                for (TunnelNetwork.Pillar p : t.pillars) {
                    double dx = x - p.x;
                    double dz = z - p.z;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d < p.r * 2.2 + 1) {
                        c.tunnelPillars.add(new double[]{d, p.r, p.yLo, p.yHi});
                    }
                }
            }
        }

        // Stations and shafts
        c.stationHits.clear();
        c.tmpStations.clear();
        network.stationsNear(x, z, c.tmpStations);
        int usedS = 0;
        for (TunnelNetwork.Station st : c.tmpStations) {
            double q = st.planQ(x, z);
            boolean inCavern = q < 1.6;
            double shaftReach = 1e9;
            if (st.shaft != null) {
                TunnelNetwork.Shaft sh = st.shaft;
                double dx = Math.max(0, Math.max(Math.min(sh.x0, sh.x1) - x, x - Math.max(sh.x0, sh.x1)));
                double dz = Math.max(0, Math.max(Math.min(sh.z0, sh.z1) - z, z - Math.max(sh.z0, sh.z1)));
                shaftReach = Math.sqrt(dx * dx + dz * dz) - sh.radius * 1.7 - margin;
            }
            if (!inCavern && shaftReach > 0) {
                continue;
            }
            if (usedS >= c.stationPool.size()) {
                c.stationPool.add(new StationHit());
            }
            StationHit sh = c.stationPool.get(usedS++);
            sh.station = st;
            sh.q = q;
            sh.shaftReach = shaftReach;
            if (sh.colDist.length < st.colX.length) {
                sh.colDist = new double[st.colX.length];
            }
            for (int k = 0; k < st.colX.length; k++) {
                double dx = x - st.colX[k];
                double dz = z - st.colZ[k];
                sh.colDist[k] = Math.sqrt(dx * dx + dz * dz);
            }
            c.stationHits.add(sh);
            if (inCavern) {
                lo = Math.min(lo, st.floorY - 2);
                hi = Math.max(hi, st.y + st.ry + margin);
            }
            if (shaftReach <= 0) {
                lo = Math.min(lo, st.shaft.y0 - 2);
                hi = Math.max(hi, st.shaft.y1 + 2);
            }
        }

        // Ravines
        c.ravineHits.clear();
        c.tmpRavines.clear();
        ravines.near(x, z, c.tmpRavines);
        int usedR = 0;
        for (Ravines.Ravine rv : c.tmpRavines) {
            if (usedR >= c.ravinePool.size()) {
                c.ravinePool.add(new Ravines.RavineHit());
            }
            Ravines.RavineHit rh = c.ravinePool.get(usedR);
            if (ravines.hit(rv, x, z, c.seabed, rh)) {
                usedR++;
                c.ravineHits.add(rh);
                lo = Math.min(lo, rh.bottom - 4);
                hi = Math.max(hi, rh.top + 2);
            }
        }

        c.carveMinY = lo;
        c.carveMaxY = hi;

        c.pocketQ = 1e9;
        if (pocketsActive) {
            pocket(c, x, z);
        }
    }

    private double holeSdf(int x, int z) {
        double cell = 512;
        long ci = (long) Math.floor(x / cell);
        long cj = (long) Math.floor(z / cell);
        double best = 1e9;
        for (long i = ci - 1; i <= ci + 1; i++) {
            for (long j = cj - 1; j <= cj + 1; j++) {
                Hashing.Stream r = new Hashing.Stream(Hashing.hash(seed, i, j, 0x401E));
                if (!r.chance(holeChance)) {
                    continue;
                }
                double hx = (i + 0.5 + 0.6 * r.signed() * 0.5) * cell;
                double hz = (j + 0.5 + 0.6 * r.signed() * 0.5) * cell;
                double hr = r.range(8, 28);
                double dx = x - hx;
                double dz = z - hz;
                best = Math.min(best, Math.sqrt(dx * dx + dz * dz) - hr);
            }
        }
        return best;
    }

    /** Pocket of a grid cell: {x, z, horizontal radius, centre y, vertical radius, hash} or empty. */
    private double[] pocketOf(long i, long j) {
        long key = (i << 32) ^ (j & 0xFFFFFFFFL);
        double[] p = pocketCache.get(key);
        if (p == null) {
            if (pocketCache.size() > 20000) {
                pocketCache.clear();
            }
            long h = Hashing.hash(seed, i, j, 0x90C7);
            Hashing.Stream r = new Hashing.Stream(h);
            if (!r.chance(pocketChance)) {
                p = NO_POCKET;
            } else {
                double px = (i + 0.5 + 0.4 * r.signed()) * pocketSize;
                double pz = (j + 0.5 + 0.4 * r.signed()) * pocketSize;
                double rh = pocketSize * r.range(0.22, 0.32);
                double rv = r.range(45, 90);
                double top = Math.min(seabed.seabed(px, pz) - 90 - rv * 0.7, -150);
                double bottom = settings.getInt(OceanSettings.NETWORK_FLOOR) + 30 + rv * 0.7;
                double cy = top <= bottom ? (top + bottom) * 0.5 : bottom + (top - bottom) * r.next();
                p = new double[]{px, pz, rh, cy, rv, h};
            }
            pocketCache.put(key, p);
        }
        return p;
    }

    private void pocket(Column c, int x, int z) {
        long ci = (long) Math.floor(x / pocketSize);
        long cj = (long) Math.floor(z / pocketSize);
        for (long i = ci - 1; i <= ci + 1; i++) {
            for (long j = cj - 1; j <= cj + 1; j++) {
                double[] p = pocketOf(i, j);
                if (p.length == 0) {
                    continue;
                }
                double dx = (x - p[0]) / p[2];
                double dz = (z - p[1]) / p[2];
                double q = dx * dx + dz * dz;
                if (q < c.pocketQ) {
                    c.pocketQ = q;
                    c.pocketCy = p[3];
                    c.pocketRv = p[4];
                    c.pocketHash = (long) p[5];
                }
            }
        }
    }

    /**
     * Signed distance of the dry cavern carved inside a rock pocket (negative inside). Kept at least
     * ~28 blocks away from every network cavity so the pocket stays sealed.
     */
    public double pocketSdf(Column c, double y) {
        if (!pocketsActive || c.pocketQ >= POCKET_REACH) {
            return 1e9;
        }
        double dy = (y - c.pocketCy) / (c.pocketRv * 0.7);
        double e = Math.sqrt(c.pocketQ / 0.49 + dy * dy);
        double sdf = (e - 1) * c.pocketRv * 0.5;
        double floor = c.pocketCy - c.pocketRv * 0.42;
        if (floor - y > sdf) {
            sdf = floor - y;
        }
        // Stay well below the local seabed (trench walls can drop far below the seabed at the pocket centre).
        double roof = y - (c.seabed - 40);
        if (roof > sdf) {
            sdf = roof;
        }
        if (sdf > 8) {
            return sdf;
        }
        sdf += 4.0 * roughNoise.noise3(c.x / 20.0, y / 14.0, c.z / 20.0);
        if (y >= c.carveMinY - 40 && y <= c.carveMaxY + 40) {
            double network = carve(c, y, false);
            if (28 - network > sdf) {
                sdf = 28 - network;
            }
        }
        return sdf;
    }

    // ------------------------------------------------------------------------------------------------
    // Density
    // ------------------------------------------------------------------------------------------------

    private static double sq(double v) {
        return v * v;
    }

    /** Solid-ness at height y in a column: positive = rock/ice, negative = water/air. Roughly in blocks. */
    public double density(Column c, double y) {
        double x = c.x;
        double z = c.z;
        double s = c.seabed - y;
        if (c.overhang > 0.05 && s > -c.overhang * 2 && s < c.overhang * 2) {
            s += c.overhang * overhangNoise.noise3(x / 24.0, y / 16.0, z / 24.0);
        }
        for (int i = 0, n = c.shapes.size(); i < n; i++) {
            Formations.Shape sh = c.shapes.get(i);
            if (y >= sh.minY() && y <= sh.maxY()) {
                double v = sh.solid(x, y, z);
                if (v > -2.5 && v < 2.5) {
                    v += 1.3 * roughDetail.noise3(x / 5.0, y / 5.0, z / 5.0);
                }
                if (v > s) {
                    s = v;
                }
            }
        }
        if (ice) {
            double crust = Math.min(y - c.iceBottom, c.iceTop - y);
            if (c.holeSdf < 30) {
                crust = Math.min(crust, c.holeSdf);
            }
            if (crust > s) {
                s = crust;
            }
            if (y > c.iceBottom - 2) {
                return s;
            }
        }
        if (y >= c.carveMinY && y <= c.carveMaxY) {
            double carve = carve(c, y, true);
            if (carve < s) {
                s = carve;
            }
        }
        if (pocketsActive && c.pocketQ < POCKET_REACH) {
            double pocket = pocketSdf(c, y);
            if (pocket < s) {
                s = pocket;
            }
        }
        return s;
    }

    /** Combined carve distance (negative inside any cavity). */
    private double carve(Column c, double y, boolean noise) {
        double x = c.x;
        double z = c.z;
        double best = 1e9;
        boolean floorMode = false;
        double corridor = 1e9;

        // Inside (and just around) a station cavern, channels are not allowed to dig below the cavern's
        // flat floor, so stations keep one level building surface.
        double stationFloor = -1e9;
        double stationW = 0;
        for (int i = 0, n = c.stationHits.size(); i < n; i++) {
            StationHit sh = c.stationHits.get(i);
            if (sh.q < 1.3) {
                double w = sh.q <= 1.0 ? 1.0 : 1.0 - (sh.q - 1.0) / 0.3;
                w = w * w * (3 - 2 * w);
                if (w > stationW) {
                    stationW = w;
                    stationFloor = sh.station.floorY;
                }
            }
        }

        for (int i = 0, n = c.tunnelHits.size(); i < n; i++) {
            TunnelNetwork.TunnelHit h = c.tunnelHits.get(i);
            double dy = y - h.yc;
            double e = Math.sqrt(sq(h.dh / h.rh) + sq(dy / h.rv));
            double sdf = (e - 1) * Math.min(h.rh, h.rv);
            double floor = h.yc - Math.max(h.rv * floorCut, clear + 1);
            if (stationW > 0 && stationFloor > floor) {
                floor += (stationFloor - floor) * stationW;
            }
            boolean fm = false;
            if (floor - y > sdf) {
                sdf = floor - y;
                fm = true;
            }
            if (sdf < best) {
                best = sdf;
                floorMode = fm;
            }
            double cd = Math.sqrt(h.dh * h.dh + dy * dy) - clear;
            if (stationW > 0) {
                cd = Math.max(cd, floor - y);
            }
            if (cd < corridor) {
                corridor = cd;
            }
        }
        // Columns inside wide channels.
        if (best < 6 && !c.tunnelPillars.isEmpty()) {
            for (double[] p : c.tunnelPillars) {
                if (y < p[2] || y > p[3]) {
                    continue;
                }
                double flare = 1 + 0.6 * Math.exp(-(y - p[2] - 6) / 6.0) + 0.6 * Math.exp(-(p[3] - 6 - y) / 6.0);
                double v = p[1] * flare - p[0];
                if (v > best) {
                    best = v;
                    floorMode = false;
                }
            }
        }

        for (int i = 0, n = c.stationHits.size(); i < n; i++) {
            StationHit sh = c.stationHits.get(i);
            TunnelNetwork.Station st = sh.station;
            if (sh.q < 1.6) {
                double dy = y - st.y;
                double e = Math.sqrt(sh.q + sq(dy / st.ry));
                double sdf = (e - 1) * Math.min(st.maxR, st.ry);
                boolean fm = false;
                if (st.floorY - y > sdf) {
                    sdf = st.floorY - y;
                    fm = true;
                }
                for (int k = 0; k < st.colX.length; k++) {
                    if (y < st.floorY - 2 || y > st.y + st.ry + 2) {
                        continue;
                    }
                    double flare = 1 + 0.7 * Math.exp(-(y - st.floorY) / 5.0) + 0.7 * Math.exp(-((st.y + st.ry * 0.9) - y) / 6.0);
                    double v = st.colR[k] * flare - sh.colDist[k];
                    if (v > sdf) {
                        sdf = v;
                        fm = false;
                    }
                }
                if (sdf < best) {
                    best = sdf;
                    floorMode = fm;
                }
            }
            if (st.shaft != null && sh.shaftReach <= 0) {
                TunnelNetwork.Shaft sf = st.shaft;
                if (y >= sf.y0 - 2 && y <= sf.y1) {
                    double t = (y - sf.y0) / (sf.y1 - sf.y0);
                    t = t < 0 ? 0 : (t > 1 ? 1 : t);
                    double cx = sf.x0 + (sf.x1 - sf.x0) * t;
                    double cz = sf.z0 + (sf.z1 - sf.z0) * t;
                    double r = sf.radius * (1 + 0.6 * Math.exp(-(sf.y1 - y) / 10.0));
                    double sdf = Math.sqrt(sq(x - cx) + sq(z - cz)) - r;
                    if (sdf < best) {
                        best = sdf;
                        floorMode = false;
                    }
                }
            }
        }

        for (int i = 0, n = c.ravineHits.size(); i < n; i++) {
            double sdf = Ravines.sdf(c.ravineHits.get(i), y);
            if (sdf < best) {
                best = sdf;
                floorMode = false;
            }
        }

        if (noise && roughness > 0 && !floorMode && best < roughness * 2 && best > -roughness * 2) {
            best += roughness * (0.75 * roughNoise.noise3(x / 18.0, y / 14.0, z / 18.0) + 0.25 * roughDetail.noise3(x / 6.0, y / 6.0, z / 6.0));
        } else if (noise && floorMode && best < 2) {
            best += 0.6 * roughDetail.noise2(x / 9.0, z / 9.0);
        }
        if (corridor < best) {
            best = corridor;
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------------
    // Biome slots
    // ------------------------------------------------------------------------------------------------

    /** Biome slot plus a stable hash used to pick a weighted biome variant inside that slot. */
    public static final class SlotResult {
        public BiomeSlot slot;
        public long variant;
    }

    public void slot(Column c, double y, SlotResult out) {
        if (ice && y >= c.iceBottom - 3) {
            out.slot = BiomeSlot.ICE_SHELF;
            out.variant = coarse(c.x, c.z, 0x1CE);
            return;
        }
        if (y > c.seabed + seabedBiomeHeight) {
            out.slot = BiomeSlot.GREAT_SEA;
            out.variant = coarse(c.x, c.z, 0x6EA);
            return;
        }
        if (y >= c.carveMinY - 4 && y <= c.carveMaxY + 4) {
            if (classifyCavity(c, y, out)) {
                return;
            }
        }
        if (y > c.seabed - 12) {
            out.slot = c.ps.dominant.slot;
            out.variant = c.ps.cellId();
            return;
        }
        if (c.pocketQ < 1 && Math.abs(y - c.pocketCy) < c.pocketRv * Math.sqrt(1 - c.pocketQ)) {
            double carve = (y >= c.carveMinY - 30 && y <= c.carveMaxY + 30) ? carve(c, y, false) : 1e9;
            if (carve > 24) {
                out.slot = BiomeSlot.ROCK_POCKET;
                out.variant = c.pocketHash;
                return;
            }
        }
        out.slot = BiomeSlot.DEEP_CRUST;
        out.variant = coarse(c.x, c.z, 0xC20);
    }

    private boolean classifyCavity(Column c, double y, SlotResult out) {
        // Station caverns win over the channels that run into them.
        for (StationHit sh : c.stationHits) {
            TunnelNetwork.Station st = sh.station;
            if (sh.q < 1.15 && y >= st.floorY - 6) {
                double e = Math.sqrt(sh.q + sq((y - st.y) / st.ry));
                if (e < 1.08) {
                    out.slot = BiomeSlot.STATION;
                    out.variant = st.hash;
                    return true;
                }
            }
        }
        double best = 3.0;
        BiomeSlot slot = null;
        long variant = 0;
        for (TunnelNetwork.TunnelHit h : c.tunnelHits) {
            double e = Math.sqrt(sq(h.dh / h.rh) + sq((y - h.yc) / h.rv));
            double sdf = (e - 1) * Math.min(h.rh, h.rv);
            if (sdf < best) {
                best = sdf;
                slot = switch (h.tunnel.cls) {
                    case SPACIOUS -> BiomeSlot.SPACIOUS_CHANNEL;
                    case STANDARD -> BiomeSlot.STANDARD_CHANNEL;
                    case NARROW -> BiomeSlot.NARROW_CHANNEL;
                };
                variant = h.tunnel.hash;
            }
        }
        for (StationHit sh : c.stationHits) {
            TunnelNetwork.Station st = sh.station;
            if (sh.q < 1.6) {
                double e = Math.sqrt(sh.q + sq((y - st.y) / st.ry));
                double sdf = (e - 1) * Math.min(st.maxR, st.ry);
                if (y < st.floorY - 6) {
                    sdf = Math.max(sdf, st.floorY - 6 - y);
                }
                if (sdf < best) {
                    best = sdf;
                    slot = BiomeSlot.STATION;
                    variant = st.hash;
                }
            }
            if (st.shaft != null && sh.shaftReach <= 0 && y >= st.shaft.y0 && y <= st.shaft.y1 - 6) {
                TunnelNetwork.Shaft sf = st.shaft;
                double t = (y - sf.y0) / (sf.y1 - sf.y0);
                double cx = sf.x0 + (sf.x1 - sf.x0) * t;
                double cz = sf.z0 + (sf.z1 - sf.z0) * t;
                double sdf = Math.sqrt(sq(c.x - cx) + sq(c.z - cz)) - sf.radius;
                if (sdf < best) {
                    best = sdf;
                    slot = BiomeSlot.STATION;
                    variant = st.hash;
                }
            }
        }
        for (Ravines.RavineHit rh : c.ravineHits) {
            if (y > c.seabed - 4) {
                continue;
            }
            double sdf = Ravines.sdf(rh, y);
            if (sdf < best) {
                best = sdf;
                slot = BiomeSlot.RAVINE;
                variant = rh.ravine.hash;
            }
        }
        if (slot == null) {
            return false;
        }
        out.slot = slot;
        out.variant = variant;
        return true;
    }

    private long coarse(int x, int z, long salt) {
        return Hashing.hash(seed, Math.floorDiv(x, 2048), Math.floorDiv(z, 2048), salt);
    }

    // ------------------------------------------------------------------------------------------------
    // Cavity spans (for decoration placement)
    // ------------------------------------------------------------------------------------------------

    /** Vertical spans [lo, hi] of network cavities crossing the column, flattened into out as pairs. */
    public void cavitySpans(Column c, List<double[]> out) {
        for (TunnelNetwork.TunnelHit h : c.tunnelHits) {
            double k = 1 - sq(h.dh / h.rh);
            if (k <= 0) {
                continue;
            }
            double ext = h.rv * Math.sqrt(k);
            double floor = h.yc - Math.max(h.rv * floorCut, clear + 1);
            out.add(new double[]{Math.max(h.yc - ext, floor), h.yc + ext, 0});
        }
        for (StationHit sh : c.stationHits) {
            if (sh.q < 1) {
                TunnelNetwork.Station st = sh.station;
                double ext = st.ry * Math.sqrt(1 - sh.q);
                out.add(new double[]{Math.max(st.y - ext, st.floorY), st.y + ext, 1});
            }
        }
        for (Ravines.RavineHit rh : c.ravineHits) {
            if (Math.abs(rh.lateral) < rh.hwTop) {
                out.add(new double[]{rh.bottom, Math.min(rh.top, c.seabed), 2});
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Crew tunnels (carved per block after the coarse terrain fill)
    // ------------------------------------------------------------------------------------------------

    public void crewTunnelsIn(int minX, int minZ, int maxX, int maxZ, List<TunnelNetwork.CrewTunnel> out) {
        if (!network.enabled()) {
            return;
        }
        double cx = (minX + maxX) * 0.5;
        double cz = (minZ + maxZ) * 0.5;
        List<TunnelNetwork.Tunnel> ts = new ArrayList<>();
        network.tunnelsNear(cx, cz, ts);
        for (TunnelNetwork.Tunnel t : ts) {
            for (TunnelNetwork.CrewTunnel ct : t.crew) {
                if (ct.intersects(minX, minZ, maxX, maxZ)) {
                    out.add(ct);
                }
            }
        }
        List<TunnelNetwork.Station> ss = new ArrayList<>();
        network.stationsNear(cx, cz, ss);
        for (TunnelNetwork.Station st : ss) {
            for (TunnelNetwork.CrewTunnel ct : st.crew) {
                if (ct.intersects(minX, minZ, maxX, maxZ)) {
                    out.add(ct);
                }
            }
        }
    }

    /** Negative inside the crew tunnel or its grotto. */
    public double crewSdf(TunnelNetwork.CrewTunnel ct, double x, double y, double z) {
        double best = 1e9;
        double[] p = ct.pts;
        for (int i = 0; i + 5 < p.length; i += 3) {
            double ax = p[i], ay = p[i + 1], az = p[i + 2];
            double bx = p[i + 3], by = p[i + 4], bz = p[i + 5];
            double sx = bx - ax, sy = by - ay, sz = bz - az;
            double l2 = sx * sx + sy * sy + sz * sz;
            double t = l2 > 0 ? ((x - ax) * sx + (y - ay) * sy + (z - az) * sz) / l2 : 0;
            t = t < 0 ? 0 : (t > 1 ? 1 : t);
            double d = Math.sqrt(sq(ax + sx * t - x) + sq(ay + sy * t - y) + sq(az + sz * t - z));
            best = Math.min(best, d - ct.radius);
        }
        if (ct.gr > 0) {
            double dy = y - ct.gy;
            double g = Math.sqrt(sq(x - ct.gx) + sq(dy * 1.25) + sq(z - ct.gz)) - ct.gr;
            if (dy < -ct.gr * 0.55) {
                g = Math.max(g, -ct.gr * 0.55 - dy);
            }
            best = Math.min(best, g);
        }
        return best + 0.35 * crewNoise.noise3(x / 4.0, y / 4.0, z / 4.0);
    }
}
