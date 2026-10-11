import com.m4ytenn.barotraumaoceans.worldgen.core.*;
import java.util.*;

public class NavCheck {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345L;
        Map<String,String> m = new HashMap<>();
        for (int i = 1; i < args.length; i++) { String[] kv = args[i].split("=",2); m.put(kv[0], kv[1]); }
        OceanSettings s = OceanSettings.fromMap(m);
        OceanTerrain T = OceanTerrain.get(seed, s);
        double clear = T.network.clearRadius;
        int tunnels = 0, samples = 0, blocked = 0, blockedTunnels = 0;
        int[] cls = new int[3];
        double minR = 1e9, maxR = 0;
        int junctionsMid = 0;
        for (long i = -4; i < 4; i++) for (long j = -4; j < 4; j++) for (int dir = 0; dir < 2; dir++) {
            TunnelNetwork.Tunnel t = T.network.tunnel(i, j, dir);
            if (t == null) continue;
            tunnels++; cls[t.cls.ordinal()]++;
            boolean bad = false;
            for (int k = 0; k < t.n; k++) {
                minR = Math.min(minR, t.rh[k]); maxR = Math.max(maxR, t.rh[k]);
                int k1 = Math.min(t.n - 1, k + 1), k0 = Math.max(0, k - 1);
                double tx = t.px[k1] - t.px[k0], tz = t.pz[k1] - t.pz[k0];
                double tl = Math.sqrt(tx * tx + tz * tz);
                double nx = -tz / tl, nz = tx / tl;
                double[][] pts = {{0,0,0},{nx*clear*0.85,0,nz*clear*0.85},{-nx*clear*0.85,0,-nz*clear*0.85},{0,clear*0.85,0},{0,-clear*0.85,0}};
                for (double[] p : pts) {
                    double x = t.px[k] + p[0], y = t.py[k] + p[1], z = t.pz[k] + p[2];
                    samples++;
                    double d = interp(T, x, y, z);
                    if (d > 0) { blocked++; bad = true; if (blocked < 15) System.out.printf("blocked tunnel(%d,%d,%d) k=%d/%d at %.0f %.0f %.0f d=%.2f%n", i, j, dir, k, t.n, x, y, z, d); }
                }
            }
            if (bad) blockedTunnels++;
        }
        System.out.printf("tunnels=%d classes(spacious/standard/narrow)=%s rh range %.1f..%.1f samples=%d blocked=%d blockedTunnels=%d%n", tunnels, Arrays.toString(cls), minR, maxR, samples, blocked, blockedTunnels);
        // station floors: check flatness & water above floor
        int stations = 0, badSt = 0;
        for (long i = -4; i < 4; i++) for (long j = -4; j < 4; j++) {
            TunnelNetwork.Station st = T.network.station(i, j);
            stations++;
            int fy = (int) Math.floor(st.floorY);
            double above = interp(T, st.x, fy + 3, st.z), below = interp(T, st.x, fy - 1, st.z);
            if (!(above < 0 && below > 0)) { badSt++; System.out.printf("station %d,%d floor check above=%.2f below=%.2f y=%.0f floor=%.1f%n", i, j, above, below, st.y, st.floorY); }
            int deg = T.network.tunnelsOf(st).size();
        }
        System.out.printf("stations=%d badFloors=%d%n", stations, badSt);
        // degree histogram
        int[] deg = new int[5];
        for (long i = -4; i < 4; i++) for (long j = -4; j < 4; j++) deg[T.network.tunnelsOf(T.network.station(i, j)).size()]++;
        System.out.println("degree histogram " + Arrays.toString(deg));
        // connectivity: route from (-4,-4) to (3,3)
        List<TunnelNetwork.Station> r = T.network.route(T.network.station(-4,-4), T.network.station(3,3), 4);
        System.out.println("route len " + r.size());
    }

    // emulate MC: density at 4x4x4 cell corners, trilinear interpolation
    static double interp(OceanTerrain T, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        int x0 = Math.floorDiv(bx, 4) * 4, y0 = Math.floorDiv(by, 4) * 4, z0 = Math.floorDiv(bz, 4) * 4;
        double fx = (bx - x0) / 4.0, fy = (by - y0) / 4.0, fz = (bz - z0) / 4.0;
        double[] v = new double[8];
        int n = 0;
        for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
            OceanTerrain.Column c = T.column(x0 + dx * 4, z0 + dz * 4);
            v[n++] = T.density(c, y0);
            v[n++] = T.density(c, y0 + 4);
        }
        // v order: (x0,z0,y0),(x0,z0,y1),(x0,z1,y0),(x0,z1,y1),(x1,z0,y0),(x1,z0,y1),(x1,z1,y0),(x1,z1,y1)
        double c00 = lerp(fy, v[0], v[1]), c01 = lerp(fy, v[2], v[3]), c10 = lerp(fy, v[4], v[5]), c11 = lerp(fy, v[6], v[7]);
        return lerp(fx, lerp(fz, c00, c01), lerp(fz, c10, c11));
    }
    static double lerp(double t, double a, double b) { return a + (b - a) * t; }
}
