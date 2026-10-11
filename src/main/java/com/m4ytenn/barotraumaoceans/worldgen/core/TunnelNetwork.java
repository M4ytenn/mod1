package com.m4ytenn.barotraumaoceans.worldgen.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The deep station network.
 * <p>
 * Stations sit on a jittered grid (one per cell). Channels only ever connect grid neighbours (east/south),
 * so they never cross each other and never branch: the only junctions are inside station caverns.
 * Every station keeps one "parent" channel towards the west or north (a binary-tree spanning structure),
 * which guarantees that the whole network is connected; extra channels are added randomly on top.
 */
public final class TunnelNetwork {

    public enum TunnelClass { SPACIOUS, STANDARD, NARROW }

    public static final int DIR_EAST = 0;
    public static final int DIR_SOUTH = 1;

    public static final class Shaft {
        public final double x0, y0, z0, x1, y1, z1, radius;

        Shaft(double x0, double y0, double z0, double x1, double y1, double z1, double radius) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.radius = radius;
        }
    }

    /** A tiny diver tunnel: a two-segment capsule chain, optionally ending in a spherical grotto. */
    public static final class CrewTunnel {
        public final double[] pts; // x0,y0,z0, x1,y1,z1, x2,y2,z2
        public final double radius;
        public final double gx, gy, gz, gr; // grotto (gr == 0 means none)
        public final boolean loot;
        public final double minX, minY, minZ, maxX, maxY, maxZ;
        public final long hash;

        CrewTunnel(double[] pts, double radius, double gx, double gy, double gz, double gr, boolean loot, long hash) {
            this.pts = pts;
            this.radius = radius;
            this.gx = gx;
            this.gy = gy;
            this.gz = gz;
            this.gr = gr;
            this.loot = loot;
            this.hash = hash;
            double ax = Double.MAX_VALUE, ay = Double.MAX_VALUE, az = Double.MAX_VALUE;
            double bx = -Double.MAX_VALUE, by = -Double.MAX_VALUE, bz = -Double.MAX_VALUE;
            for (int i = 0; i < pts.length; i += 3) {
                ax = Math.min(ax, pts[i]);
                ay = Math.min(ay, pts[i + 1]);
                az = Math.min(az, pts[i + 2]);
                bx = Math.max(bx, pts[i]);
                by = Math.max(by, pts[i + 1]);
                bz = Math.max(bz, pts[i + 2]);
            }
            if (gr > 0) {
                ax = Math.min(ax, gx - gr);
                ay = Math.min(ay, gy - gr);
                az = Math.min(az, gz - gr);
                bx = Math.max(bx, gx + gr);
                by = Math.max(by, gy + gr);
                bz = Math.max(bz, gz + gr);
            }
            double m = radius + 1.5;
            minX = ax - m;
            minY = ay - m;
            minZ = az - m;
            maxX = bx + m;
            maxY = by + m;
            maxZ = bz + m;
        }

        public boolean intersects(double x0, double z0, double x1, double z1) {
            return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
        }
    }

    public static final class Station {
        public final long gx, gz;
        public final double x, y, z;
        public final double rx, rz, ry;
        public final double cosA, sinA;
        public final double floorY;
        public final boolean hall;
        public final boolean parentWest;
        public final double[] colX, colZ, colR;
        public final Shaft shaft;
        public final long hash;
        public final List<CrewTunnel> crew;
        public final double maxR;

        Station(long gx, long gz, double x, double y, double z, double rx, double rz, double ry, double angle, boolean hall,
                boolean parentWest, double[] colX, double[] colZ, double[] colR, Shaft shaft, long hash, List<CrewTunnel> crew,
                double clearRadius) {
            this.gx = gx;
            this.gz = gz;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rx = rx;
            this.rz = rz;
            this.ry = ry;
            this.cosA = Math.cos(angle);
            this.sinA = Math.sin(angle);
            this.floorY = y - Math.max(ry * 0.55, clearRadius + 2);
            this.hall = hall;
            this.parentWest = parentWest;
            this.colX = colX;
            this.colZ = colZ;
            this.colR = colR;
            this.shaft = shaft;
            this.hash = hash;
            this.crew = crew;
            this.maxR = Math.max(rx, rz);
        }

        /** Squared normalised plan distance (1 = on the cavern wall at its widest height). */
        public double planQ(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            double a = (dx * cosA + dz * sinA) / rx;
            double b = (-dx * sinA + dz * cosA) / rz;
            return a * a + b * b;
        }

        public int floorBlockY() {
            return (int) Math.floor(floorY);
        }
    }

    public static final class Pillar {
        public final double x, z, r, yLo, yHi;

        Pillar(double x, double z, double r, double yLo, double yHi) {
            this.x = x;
            this.z = z;
            this.r = r;
            this.yLo = yLo;
            this.yHi = yHi;
        }
    }

    public static final class Tunnel {
        public final Station a, b;
        public final int dir;
        public final TunnelClass cls;
        public final long hash;
        public final int n;
        public final double[] px, py, pz, rh, rv;
        public final double ax, az, ux, uz, length, segU;
        public final double minX, maxX, minZ, maxZ;
        public final List<Pillar> pillars;
        public final List<CrewTunnel> crew;

        Tunnel(Station a, Station b, int dir, TunnelClass cls, long hash, double[] px, double[] py, double[] pz, double[] rh, double[] rv,
               double ux, double uz, double length, List<Pillar> pillars, List<CrewTunnel> crew, double margin) {
            this.a = a;
            this.b = b;
            this.dir = dir;
            this.cls = cls;
            this.hash = hash;
            this.n = px.length;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.rh = rh;
            this.rv = rv;
            this.ax = a.x;
            this.az = a.z;
            this.ux = ux;
            this.uz = uz;
            this.length = length;
            this.segU = length / (n - 1);
            this.pillars = pillars;
            this.crew = crew;
            double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double r = rh[i] + margin;
                x0 = Math.min(x0, px[i] - r);
                x1 = Math.max(x1, px[i] + r);
                z0 = Math.min(z0, pz[i] - r);
                z1 = Math.max(z1, pz[i] + r);
            }
            minX = x0;
            maxX = x1;
            minZ = z0;
            maxZ = z1;
        }
    }

    /** Nearest-point information of a column relative to one tunnel. */
    public static final class TunnelHit {
        public Tunnel tunnel;
        public double dh, yc, rh, rv, t;
        public int index;
    }

    private final long seed;
    private final SeabedModel seabed;
    private final boolean enabled;
    private final double spacing;
    private final double jitter;
    private final double extraChance;
    private final double stationMinY;
    private final double stationMaxY;
    private final double cover;
    private final double floorLimit;
    private final double meander;
    private final double verticalWander;
    public final double clearRadius;
    private final double[] classWeights;
    private final double[] classMin;
    private final double[] classMax;
    private final double chamberChance;
    private final double chamberScale;
    private final double narrowsChance;
    private final double canyonChance;
    private final double tunnelPillarChance;
    private final double stationMinR;
    private final double stationMaxR;
    private final double heightRatio;
    private final double hallChance;
    private final double columnChance;
    private final double shaftChance;
    private final double shaftMinR;
    private final double shaftMaxR;
    private final boolean crewEnabled;
    private final double crewPerK;
    private final int crewPerStation;
    private final double crewMinLen;
    private final double crewMaxLen;
    private final double crewRadius;
    private final double grottoChance;
    private final double grottoMinR;
    private final double grottoMaxR;
    private final double lootChance;
    private final double wallMargin;

    private final Map<Long, Station> stations = new ConcurrentHashMap<>();
    private final Map<Long, Object> tunnels = new ConcurrentHashMap<>();
    private static final Object NONE = new Object();

    public TunnelNetwork(long seed, OceanSettings s, SeabedModel seabed) {
        this.seed = seed;
        this.seabed = seabed;
        this.enabled = s.getBool(OceanSettings.NETWORK_ENABLED);
        this.spacing = s.getInt(OceanSettings.STATION_SPACING);
        this.jitter = s.getDouble(OceanSettings.STATION_JITTER);
        this.extraChance = s.getDouble(OceanSettings.EXTRA_CONNECTION_CHANCE);
        this.stationMinY = s.getInt(OceanSettings.STATION_MIN_Y);
        this.stationMaxY = s.getInt(OceanSettings.STATION_MAX_Y);
        this.cover = s.getInt(OceanSettings.MIN_ROCK_COVER);
        this.floorLimit = s.getInt(OceanSettings.NETWORK_FLOOR);
        this.meander = s.getDouble(OceanSettings.MEANDER);
        this.verticalWander = s.getInt(OceanSettings.VERTICAL_WANDER);
        this.clearRadius = s.getInt(OceanSettings.CLEAR_RADIUS);
        this.classWeights = new double[]{s.getDouble(OceanSettings.SPACIOUS_WEIGHT), s.getDouble(OceanSettings.STANDARD_WEIGHT), s.getDouble(OceanSettings.NARROW_WEIGHT)};
        this.classMin = new double[]{s.getInt(OceanSettings.SPACIOUS_MIN), s.getInt(OceanSettings.STANDARD_MIN), s.getInt(OceanSettings.NARROW_MIN)};
        this.classMax = new double[]{s.getInt(OceanSettings.SPACIOUS_MAX), s.getInt(OceanSettings.STANDARD_MAX), s.getInt(OceanSettings.NARROW_MAX)};
        this.chamberChance = s.getDouble(OceanSettings.CHAMBER_CHANCE);
        this.chamberScale = s.getDouble(OceanSettings.CHAMBER_SCALE);
        this.narrowsChance = s.getDouble(OceanSettings.NARROWS_CHANCE);
        this.canyonChance = s.getDouble(OceanSettings.CANYON_CHANCE);
        this.tunnelPillarChance = s.getDouble(OceanSettings.TUNNEL_PILLAR_CHANCE);
        this.stationMinR = s.getInt(OceanSettings.STATION_MIN_RADIUS);
        this.stationMaxR = s.getInt(OceanSettings.STATION_MAX_RADIUS);
        this.heightRatio = s.getDouble(OceanSettings.STATION_HEIGHT_RATIO);
        this.hallChance = s.getDouble(OceanSettings.STATION_HALL_CHANCE);
        this.columnChance = s.getDouble(OceanSettings.STATION_COLUMN_CHANCE);
        this.shaftChance = s.getDouble(OceanSettings.STATION_SHAFT_CHANCE);
        this.shaftMinR = s.getInt(OceanSettings.SHAFT_MIN_RADIUS);
        this.shaftMaxR = s.getInt(OceanSettings.SHAFT_MAX_RADIUS);
        this.crewEnabled = s.getBool(OceanSettings.CREW_ENABLED);
        this.crewPerK = s.getDouble(OceanSettings.CREW_PER_TUNNEL);
        this.crewPerStation = s.getInt(OceanSettings.CREW_PER_STATION);
        this.crewMinLen = s.getInt(OceanSettings.CREW_MIN_LENGTH);
        this.crewMaxLen = s.getInt(OceanSettings.CREW_MAX_LENGTH);
        this.crewRadius = s.getDouble(OceanSettings.CREW_RADIUS);
        this.grottoChance = s.getDouble(OceanSettings.GROTTO_CHANCE);
        this.grottoMinR = s.getInt(OceanSettings.GROTTO_MIN_RADIUS);
        this.grottoMaxR = s.getInt(OceanSettings.GROTTO_MAX_RADIUS);
        this.lootChance = s.getDouble(OceanSettings.GROTTO_LOOT_CHANCE);
        this.wallMargin = s.getDouble(OceanSettings.WALL_ROUGHNESS) + 3.0;
    }

    public boolean enabled() {
        return enabled;
    }

    public double spacing() {
        return spacing;
    }

    private static long key(long i, long j) {
        return (i << 32) ^ (j & 0xFFFFFFFFL);
    }

    private void trimCaches() {
        // Channels keep their whole centre line in memory, so the caches are bounded.
        if (stations.size() > 8192) {
            stations.clear();
        }
        if (tunnels.size() > 6144) {
            tunnels.clear();
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Stations
    // ------------------------------------------------------------------------------------------------

    public long cellOf(double coord) {
        return (long) Math.floor(coord / spacing);
    }

    public Station station(long i, long j) {
        long k = key(i, j);
        Station st = stations.get(k);
        if (st == null) {
            trimCaches();
            st = buildStation(i, j);
            stations.put(k, st);
        }
        return st;
    }

    private Station buildStation(long i, long j) {
        long h = Hashing.hash(seed, i, j, 0x57A7);
        Hashing.Stream r = new Hashing.Stream(h);
        double x = (i + 0.5 + jitter * r.signed()) * spacing;
        double z = (j + 0.5 + jitter * r.signed()) * spacing;
        double radius = r.range(stationMinR, stationMaxR);
        boolean hall = r.chance(hallChance);
        double angle = r.next() * Math.PI;
        double rx = hall ? radius * 1.45 : radius;
        double rz = hall ? radius * 0.8 : radius;
        double ry = Math.max(radius * heightRatio * r.range(0.85, 1.15), clearRadius + 8);
        double y = r.range(stationMinY, stationMaxY);
        double bed = seabed.seabed(x, z);
        y = Math.min(y, bed - cover - ry);
        y = Math.max(y, floorLimit + Math.max(ry * 0.55, clearRadius + 2) + 4);
        boolean parentWest = r.chance(0.5);

        double[] colX = new double[0];
        double[] colZ = new double[0];
        double[] colR = new double[0];
        if (r.chance(columnChance)) {
            int count = r.rangeInt(3, 6);
            colX = new double[count];
            colZ = new double[count];
            colR = new double[count];
            double start = r.next() * Math.PI * 2;
            for (int c = 0; c < count; c++) {
                double th = start + c * (Math.PI * 2 / count) + r.signed() * 0.25;
                double ring = r.range(0.62, 0.8);
                double lx = Math.cos(th) * ring * rx;
                double lz = Math.sin(th) * ring * rz;
                colX[c] = x + lx * Math.cos(angle) - lz * Math.sin(angle);
                colZ[c] = z + lx * Math.sin(angle) + lz * Math.cos(angle);
                colR[c] = r.range(3.0, 6.5);
            }
        }

        Shaft shaft = null;
        if (r.chance(shaftChance)) {
            double sr = r.range(shaftMinR, shaftMaxR);
            double tx = x + r.signed() * Math.min(60, radius * 0.6);
            double tz = z + r.signed() * Math.min(60, radius * 0.6);
            double top = seabed.seabed(tx, tz) + 8;
            if (top > y + ry) {
                shaft = new Shaft(x, y + ry * 0.5, z, tx, top, tz, sr);
            }
        }

        List<CrewTunnel> crew = new ArrayList<>();
        if (crewEnabled) {
            for (int c = 0; c < crewPerStation; c++) {
                double th = r.next() * Math.PI * 2;
                double lx = Math.cos(th) * 0.92 * rx;
                double lz = Math.sin(th) * 0.92 * rz;
                double sx = x + lx * Math.cos(angle) - lz * Math.sin(angle);
                double sz = z + lx * Math.sin(angle) + lz * Math.cos(angle);
                double dx = sx - x;
                double dz = sz - z;
                double dl = Math.sqrt(dx * dx + dz * dz);
                double sy = y - Math.max(ry * 0.55, clearRadius + 2) + r.range(2.5, 6.0);
                crew.add(makeCrew(r, sx - dx / dl * 6, sy, sz - dz / dl * 6, dx / dl, dz / dl, 10));
            }
        }
        return new Station(i, j, x, y, z, rx, rz, ry, angle, hall, parentWest, colX, colZ, colR, shaft, h,
                crew.isEmpty() ? Collections.emptyList() : crew, clearRadius);
    }

    // ------------------------------------------------------------------------------------------------
    // Edges
    // ------------------------------------------------------------------------------------------------

    /** Whether a channel exists from cell (i,j) to its east (dir 0) or south (dir 1) neighbour. */
    public boolean edgeExists(long i, long j, int dir) {
        if (!enabled) {
            return false;
        }
        double roll = Hashing.unit(Hashing.hash(seed, i, j, dir, 0xED6E));
        if (roll < extraChance) {
            return true;
        }
        if (dir == DIR_EAST) {
            return station(i + 1, j).parentWest;
        } else {
            return !station(i, j + 1).parentWest;
        }
    }

    /** Returns the channel from (i,j) towards dir, or null if none. */
    public Tunnel tunnel(long i, long j, int dir) {
        long k = key(i, j) * 2 + dir;
        Object t = tunnels.get(k);
        if (t == null) {
            trimCaches();
            t = edgeExists(i, j, dir) ? buildTunnel(i, j, dir) : NONE;
            tunnels.put(k, t);
        }
        return t == NONE ? null : (Tunnel) t;
    }

    /** All channels touching a station. */
    public List<Tunnel> tunnelsOf(Station st) {
        List<Tunnel> out = new ArrayList<>(4);
        Tunnel t;
        if ((t = tunnel(st.gx, st.gz, DIR_EAST)) != null) out.add(t);
        if ((t = tunnel(st.gx, st.gz, DIR_SOUTH)) != null) out.add(t);
        if ((t = tunnel(st.gx - 1, st.gz, DIR_EAST)) != null) out.add(t);
        if ((t = tunnel(st.gx, st.gz - 1, DIR_SOUTH)) != null) out.add(t);
        return out;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private TunnelClass pickClass(double roll) {
        double total = classWeights[0] + classWeights[1] + classWeights[2];
        if (total <= 0) {
            return TunnelClass.STANDARD;
        }
        double r = roll * total;
        if (r < classWeights[0]) return TunnelClass.SPACIOUS;
        if (r < classWeights[0] + classWeights[1]) return TunnelClass.STANDARD;
        return TunnelClass.NARROW;
    }

    private Tunnel buildTunnel(long i, long j, int dir) {
        Station a = station(i, j);
        Station b = dir == DIR_EAST ? station(i + 1, j) : station(i, j + 1);
        long h = Hashing.hash(seed, i, j, dir, 0x7E11);
        Hashing.Stream r = new Hashing.Stream(h);
        TunnelClass cls = pickClass(r.next());
        int ci = cls.ordinal();
        double baseR = r.range(classMin[ci], classMax[ci]);
        double aspect = cls == TunnelClass.NARROW ? r.range(0.8, 1.0) : r.range(0.55, 0.85);

        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double length = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        double ux = dx / length;
        double uz = dz / length;
        double vx = -uz;
        double vz = ux;

        double f1 = r.range(0.6, 1.4), p1 = r.next() * Math.PI * 2;
        double f2 = r.range(1.8, 3.2), p2 = r.next() * Math.PI * 2;
        double f3 = r.range(0.8, 2.0), p3 = r.next() * Math.PI * 2;
        double f4 = r.range(1.0, 2.5), p4 = r.next() * Math.PI * 2;
        double f5 = r.range(3.0, 6.0), p5 = r.next() * Math.PI * 2;

        int chambers = r.chance(chamberChance) ? (r.chance(0.35) ? 2 : 1) : 0;
        double[] ct = new double[chambers];
        double[] cw = new double[chambers];
        for (int c = 0; c < chambers; c++) {
            ct[c] = r.range(0.22, 0.78);
            cw[c] = r.range(0.025, 0.05);
        }
        boolean narrows = r.chance(narrowsChance);
        double nt = r.range(0.2, 0.8), nw = r.range(0.04, 0.08);
        boolean canyon = r.chance(canyonChance);
        double kt = r.range(0.2, 0.8), kw = r.range(0.05, 0.1), kAspect = r.range(1.6, 2.2);

        int n = Math.max(8, (int) Math.ceil(length / 10.0)) + 1;
        double[] px = new double[n];
        double[] py = new double[n];
        double[] pz = new double[n];
        double[] rh = new double[n];
        double[] rv = new double[n];
        double[] maxY = new double[n];
        double minRh = clearRadius + 4;
        double minRv = clearRadius + 3;
        for (int k = 0; k < n; k++) {
            double t = k / (double) (n - 1);
            double env = Math.pow(Math.sin(Math.PI * t), 0.8);
            double lateral = length * meander * env * (0.7 * Math.sin(2 * Math.PI * f1 * t + p1) + 0.3 * Math.sin(2 * Math.PI * f2 * t + p2));
            double x = a.x + dx * t + vx * lateral;
            double z = a.z + dz * t + vz * lateral;
            double y = a.y + (b.y - a.y) * smooth(t) + verticalWander * env * Math.sin(2 * Math.PI * f3 * t + p3);
            double rad = baseR * (1 + 0.18 * (0.6 * Math.sin(2 * Math.PI * f4 * t + p4) + 0.4 * Math.sin(2 * Math.PI * f5 * t + p5)));
            for (int c = 0; c < chambers; c++) {
                double q = (t - ct[c]) / cw[c];
                rad *= 1 + (chamberScale - 1) * Math.exp(-q * q);
            }
            rad = Math.min(rad, classMax[ci] * Math.max(1.15, chamberScale));
            if (narrows) {
                double q = (t - nt) / nw;
                rad *= 1 - 0.35 * Math.exp(-q * q);
            }
            double asp = aspect;
            double hr = rad;
            if (canyon) {
                double q = (t - kt) / kw;
                double bump = Math.exp(-q * q);
                asp *= 1 + (kAspect - 1) * bump;
                hr *= 1 - 0.25 * bump;
            }
            hr = Math.max(hr, minRh);
            double vr = Math.max(hr * asp, minRv);
            px[k] = x;
            pz[k] = z;
            py[k] = y;
            rh[k] = hr;
            rv[k] = vr;
            maxY[k] = seabed.seabed(x, z) - cover - vr;
        }
        // Clamp below the seabed, smooth, clamp again.
        for (int k = 0; k < n; k++) {
            py[k] = Math.max(Math.min(py[k], maxY[k]), floorLimit + rv[k] + 2);
        }
        for (int pass = 0; pass < 4; pass++) {
            double prev = py[0];
            for (int k = 1; k < n - 1; k++) {
                double cur = py[k];
                py[k] = 0.25 * prev + 0.5 * cur + 0.25 * py[k + 1];
                prev = cur;
            }
        }
        for (int k = 0; k < n; k++) {
            py[k] = Math.max(Math.min(py[k], maxY[k]), floorLimit + rv[k] + 2);
        }
        // Near both stations the channel enters at the cavern's centre height and never gets taller than the
        // cavern, so the channel mouth lines up with the cavern and its flat floor.
        for (int k = 0; k < n; k++) {
            for (int end = 0; end < 2; end++) {
                Station st = end == 0 ? a : b;
                double ddx = px[k] - st.x;
                double ddz = pz[k] - st.z;
                double d = Math.sqrt(ddx * ddx + ddz * ddz) / st.maxR;
                if (d < 2.4) {
                    double w = d <= 1.15 ? 1.0 : 1.0 - (d - 1.15) / 1.25;
                    w = w * w * (3 - 2 * w);
                    py[k] += (st.y - py[k]) * w;
                    double capV = Math.max(minRv, st.ry);
                    if (rv[k] > capV) {
                        rv[k] += (capV - rv[k]) * w;
                    }
                }
            }
        }

        // Columns inside wide stretches, always outside the clear corridor.
        List<Pillar> pillars = new ArrayList<>();
        int step = Math.max(1, (int) Math.round(100.0 / (length / (n - 1))));
        for (int k = step; k < n - step; k += step) {
            if (!r.chance(tunnelPillarChance)) {
                continue;
            }
            double pr = r.range(3.0, 7.0);
            if (rh[k] < clearRadius + 2 * pr + 8) {
                continue;
            }
            double side = r.chance(0.5) ? 1 : -1;
            double off = r.range(clearRadius + pr + 2, rh[k] - pr - 3);
            double tx = px[Math.min(n - 1, k + 1)] - px[k - 1];
            double tz = pz[Math.min(n - 1, k + 1)] - pz[k - 1];
            double tl = Math.sqrt(tx * tx + tz * tz);
            double nx = -tz / tl;
            double nz = tx / tl;
            pillars.add(new Pillar(px[k] + nx * side * off, pz[k] + nz * side * off, pr, py[k] - rv[k] - 6, py[k] + rv[k] + 6));
        }

        // Crew tunnels branching into the rock.
        List<CrewTunnel> crew = new ArrayList<>();
        if (crewEnabled && crewPerK > 0) {
            double expected = crewPerK * length / 1000.0;
            int count = (int) Math.floor(expected + r.next());
            for (int c = 0; c < count; c++) {
                int k = (int) Math.round(r.range(0.1, 0.9) * (n - 1));
                k = Math.max(1, Math.min(n - 2, k));
                double tx = px[k + 1] - px[k - 1];
                double tz = pz[k + 1] - pz[k - 1];
                double tl = Math.sqrt(tx * tx + tz * tz);
                double side = r.chance(0.5) ? 1 : -1;
                double nx = -tz / tl * side;
                double nz = tx / tl * side;
                double sx = px[k] + nx * rh[k] * 0.55;
                double sz = pz[k] + nz * rh[k] * 0.55;
                double sy = py[k] - rv[k] * 0.2;
                crew.add(makeCrew(r, sx, sy, sz, nx, nz, rh[k] * 0.5));
            }
        }

        return new Tunnel(a, b, dir, cls, h, px, py, pz, rh, rv, ux, uz, length,
                pillars.isEmpty() ? Collections.emptyList() : pillars,
                crew.isEmpty() ? Collections.emptyList() : crew, wallMargin);
    }

    private CrewTunnel makeCrew(Hashing.Stream r, double sx, double sy, double sz, double nx, double nz, double inside) {
        double yaw = r.signed() * Math.toRadians(30);
        double pitch = Math.toRadians(r.range(-10, 18));
        double cy = Math.cos(yaw), sy2 = Math.sin(yaw);
        double dx = nx * cy - nz * sy2;
        double dz = nx * sy2 + nz * cy;
        double hl = Math.cos(pitch);
        double dirX = dx * hl;
        double dirY = Math.sin(pitch);
        double dirZ = dz * hl;
        double len = inside + r.range(crewMinLen, crewMaxLen);
        double ex = sx + dirX * len;
        double ey = sy + dirY * len;
        double ez = sz + dirZ * len;
        double mx = (sx + ex) * 0.5 + (-dirZ) * r.signed() * 4;
        double my = (sy + ey) * 0.5 + r.signed() * 2;
        double mz = (sz + ez) * 0.5 + dirX * r.signed() * 4;
        double radius = crewRadius * r.range(0.9, 1.15);
        double gr = 0, gx = 0, gy = 0, gz = 0;
        boolean loot = false;
        if (r.chance(grottoChance)) {
            gr = r.range(grottoMinR, grottoMaxR);
            gx = ex + dirX * gr * 0.6;
            gy = ey + dirY * gr * 0.6 + gr * 0.2;
            gz = ez + dirZ * gr * 0.6;
            loot = r.chance(lootChance);
        }
        return new CrewTunnel(new double[]{sx, sy, sz, mx, my, mz, ex, ey, ez}, radius, gx, gy, gz, gr, loot, r.nextLong());
    }

    // ------------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------------

    /**
     * Lower bound of the distance between an upright cylinder (centre x/z, radius r, from y0 to y1) and every
     * cavity of the network: channels, station caverns, entrance shafts and crew tunnels. Each cavity is
     * bounded by a box around its centre line, so the result is conservative.
     */
    public double clearance(double x, double z, double r, double y0, double y1) {
        if (!enabled) {
            return 1e9;
        }
        double best = 1e9;
        long i0 = cellOf(x - r), i1 = cellOf(x + r);
        long j0 = cellOf(z - r), j1 = cellOf(z + r);
        for (long i = i0 - 2; i <= i1 + 1; i++) {
            for (long j = j0 - 2; j <= j1 + 1; j++) {
                for (int dir = DIR_EAST; dir <= DIR_SOUTH; dir++) {
                    Tunnel t = tunnel(i, j, dir);
                    if (t != null) {
                        best = Math.min(best, clearance(t, x, z, r, y0, y1));
                    }
                }
                if (i >= i0 - 1 && i <= i1 + 1 && j >= j0 - 1 && j <= j1 + 1) {
                    best = Math.min(best, clearance(station(i, j), x, z, r, y0, y1));
                }
            }
        }
        return best;
    }

    private double clearance(Tunnel t, double x, double z, double r, double y0, double y1) {
        double far = Math.max(t.minX - x, Math.max(x - t.maxX, Math.max(t.minZ - z, z - t.maxZ))) - r;
        if (far > 64) {
            return far;
        }
        double best = 1e9;
        for (int k = 0; k < t.n - 1; k++) {
            double dh = segmentDistance(x, z, t.px[k], t.pz[k], t.px[k + 1], t.pz[k + 1]);
            double rad = Math.max(t.rh[k], t.rh[k + 1]) + wallMargin;
            double rv = Math.max(t.rv[k], t.rv[k + 1]) + wallMargin;
            double lo = Math.min(t.py[k], t.py[k + 1]) - rv;
            double hi = Math.max(t.py[k], t.py[k + 1]) + rv;
            best = Math.min(best, gap(dh - rad - r, lo, hi, y0, y1));
        }
        for (CrewTunnel ct : t.crew) {
            best = Math.min(best, boxClearance(ct, x, z, r, y0, y1));
        }
        return best;
    }

    private double clearance(Station st, double x, double z, double r, double y0, double y1) {
        double dh = Math.sqrt((x - st.x) * (x - st.x) + (z - st.z) * (z - st.z));
        double best = gap(dh - st.maxR - wallMargin - r, st.floorY - 2, st.y + st.ry + wallMargin, y0, y1);
        if (st.shaft != null) {
            Shaft sh = st.shaft;
            double d = segmentDistance(x, z, sh.x0, sh.z0, sh.x1, sh.z1);
            best = Math.min(best, gap(d - sh.radius * 1.7 - wallMargin - r, Math.min(sh.y0, sh.y1) - 2,
                    Math.max(sh.y0, sh.y1) + 2, y0, y1));
        }
        for (CrewTunnel ct : st.crew) {
            best = Math.min(best, boxClearance(ct, x, z, r, y0, y1));
        }
        return best;
    }

    private static double boxClearance(CrewTunnel ct, double x, double z, double r, double y0, double y1) {
        double dx = Math.max(0, Math.max(ct.minX - x, x - ct.maxX));
        double dz = Math.max(0, Math.max(ct.minZ - z, z - ct.maxZ));
        return gap(Math.sqrt(dx * dx + dz * dz) - r, ct.minY, ct.maxY, y0, y1);
    }

    /** Larger of the horizontal gap and the vertical gap between [lo, hi] and [y0, y1]. */
    private static double gap(double horizontal, double lo, double hi, double y0, double y1) {
        return Math.max(horizontal, Math.max(lo - y1, y0 - hi));
    }

    private static double segmentDistance(double x, double z, double ax, double az, double bx, double bz) {
        double sx = bx - ax, sz = bz - az;
        double l2 = sx * sx + sz * sz;
        double s = l2 > 0 ? ((x - ax) * sx + (z - az) * sz) / l2 : 0;
        s = s < 0 ? 0 : (s > 1 ? 1 : s);
        double qx = ax + sx * s - x;
        double qz = az + sz * s - z;
        return Math.sqrt(qx * qx + qz * qz);
    }

    /** Collects every channel whose bounding box contains the column (x, z). */
    public void tunnelsNear(double x, double z, List<Tunnel> out) {
        if (!enabled) {
            return;
        }
        long cx = cellOf(x);
        long cz = cellOf(z);
        for (long i = cx - 2; i <= cx + 1; i++) {
            for (long j = cz - 1; j <= cz + 1; j++) {
                addIfNear(tunnel(i, j, DIR_EAST), x, z, out);
            }
        }
        for (long i = cx - 1; i <= cx + 1; i++) {
            for (long j = cz - 2; j <= cz + 1; j++) {
                addIfNear(tunnel(i, j, DIR_SOUTH), x, z, out);
            }
        }
    }

    private static void addIfNear(Tunnel t, double x, double z, List<Tunnel> out) {
        if (t != null && x >= t.minX && x <= t.maxX && z >= t.minZ && z <= t.maxZ) {
            out.add(t);
        }
    }

    /** Collects stations whose cavern (or shaft) might reach the column. */
    public void stationsNear(double x, double z, List<Station> out) {
        if (!enabled) {
            return;
        }
        long cx = cellOf(x);
        long cz = cellOf(z);
        for (long i = cx - 1; i <= cx + 1; i++) {
            for (long j = cz - 1; j <= cz + 1; j++) {
                Station st = station(i, j);
                double reach = st.maxR + wallMargin + 80;
                if (Math.abs(x - st.x) < reach && Math.abs(z - st.z) < reach) {
                    out.add(st);
                }
            }
        }
    }

    /** Nearest point of a channel's centre line to the column, measured horizontally. */
    public boolean nearest(Tunnel t, double x, double z, TunnelHit out) {
        int n = t.n;
        double u = (x - t.ax) * t.ux + (z - t.az) * t.uz;
        int k0 = (int) Math.floor(u / t.segU);
        k0 = Math.max(0, Math.min(n - 2, k0));
        double best = Double.MAX_VALUE;
        int bestK = k0;
        double bestS = 0;
        // Search outwards from k0 while the along-axis distance can still beat the best distance.
        for (int dirStep = -1; dirStep <= 1; dirStep += 2) {
            int k = dirStep < 0 ? k0 : k0 + 1;
            while (k >= 0 && k <= n - 2) {
                double ax = t.px[k], az = t.pz[k];
                double bx = t.px[k + 1], bz = t.pz[k + 1];
                double sx = bx - ax, sz = bz - az;
                double l2 = sx * sx + sz * sz;
                double s = l2 > 0 ? ((x - ax) * sx + (z - az) * sz) / l2 : 0;
                s = s < 0 ? 0 : (s > 1 ? 1 : s);
                double qx = ax + sx * s - x;
                double qz = az + sz * s - z;
                double d2 = qx * qx + qz * qz;
                if (d2 < best) {
                    best = d2;
                    bestK = k;
                    bestS = s;
                }
                double alongGap = Math.abs(k - u / t.segU) * t.segU - t.segU * 2;
                if (alongGap > 0 && alongGap * alongGap > best) {
                    break;
                }
                k += dirStep;
            }
        }
        double dh = Math.sqrt(best);
        int k = bestK;
        double s = bestS;
        double rh = t.rh[k] + (t.rh[k + 1] - t.rh[k]) * s;
        if (dh > rh + wallMargin + 2) {
            return false;
        }
        out.tunnel = t;
        out.dh = dh;
        out.index = k;
        out.t = (k + s) / (n - 1);
        out.yc = t.py[k] + (t.py[k + 1] - t.py[k]) * s;
        out.rh = rh;
        out.rv = t.rv[k] + (t.rv[k + 1] - t.rv[k]) * s;
        return true;
    }

    /** Stations ordered by distance to (x, z), searching a square of cells. */
    public List<Station> nearestStations(double x, double z, int count, int cellRadius) {
        List<Station> all = new ArrayList<>();
        long cx = cellOf(x);
        long cz = cellOf(z);
        for (long i = cx - cellRadius; i <= cx + cellRadius; i++) {
            for (long j = cz - cellRadius; j <= cz + cellRadius; j++) {
                all.add(station(i, j));
            }
        }
        all.sort((p, q) -> Double.compare(dist2(p, x, z), dist2(q, x, z)));
        return all.size() > count ? new ArrayList<>(all.subList(0, count)) : all;
    }

    private static double dist2(Station s, double x, double z) {
        double dx = s.x - x;
        double dz = s.z - z;
        return dx * dx + dz * dz;
    }

    /** Neighbouring station reached through a channel. */
    public Station other(Tunnel t, Station from) {
        return t.a == from || (t.a.gx == from.gx && t.a.gz == from.gz) ? t.b : t.a;
    }

    /**
     * Breadth-first route along channels from one station to another, limited to a search window.
     * Returns the list of stations including both ends, or an empty list if none was found.
     */
    public List<Station> route(Station from, Station to, int maxCells) {
        java.util.ArrayDeque<Station> queue = new java.util.ArrayDeque<>();
        java.util.Map<Long, Station> prev = new java.util.HashMap<>();
        long minI = Math.min(from.gx, to.gx) - maxCells;
        long maxI = Math.max(from.gx, to.gx) + maxCells;
        long minJ = Math.min(from.gz, to.gz) - maxCells;
        long maxJ = Math.max(from.gz, to.gz) + maxCells;
        queue.add(from);
        prev.put(key(from.gx, from.gz), from);
        int visited = 0;
        while (!queue.isEmpty() && visited < 20000) {
            Station cur = queue.poll();
            visited++;
            if (cur.gx == to.gx && cur.gz == to.gz) {
                List<Station> path = new ArrayList<>();
                Station s = cur;
                while (true) {
                    path.add(s);
                    Station p = prev.get(key(s.gx, s.gz));
                    if (p == s) {
                        break;
                    }
                    s = p;
                }
                Collections.reverse(path);
                return path;
            }
            for (Tunnel t : tunnelsOf(cur)) {
                Station nb = other(t, cur);
                if (nb.gx < minI || nb.gx > maxI || nb.gz < minJ || nb.gz > maxJ) {
                    continue;
                }
                long k = key(nb.gx, nb.gz);
                if (!prev.containsKey(k)) {
                    prev.put(k, cur);
                    queue.add(nb);
                }
            }
        }
        return Collections.emptyList();
    }
}
