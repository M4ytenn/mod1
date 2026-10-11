import com.m4ytenn.barotraumaoceans.worldgen.core.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

public class Preview {
    static OceanTerrain T;
    static final int MINY = -512, MAXY = 320;

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345L;
        String out = args.length > 1 ? args[1] : "out";
        Map<String,String> m = new HashMap<>();
        for (int i = 2; i < args.length; i++) { String[] kv = args[i].split("=",2); m.put(kv[0], kv[1]); }
        OceanSettings s = OceanSettings.fromMap(m);
        T = OceanTerrain.get(seed, s);
        long t0 = System.nanoTime();
        topDown(out + "/top.png", 0, 0, 4096, 4);
        System.out.println("top " + (System.nanoTime()-t0)/1e6 + "ms"); t0 = System.nanoTime();
        TunnelNetwork.Station st = T.network.station(0, 0);
        System.out.printf("station(0,0) at %.0f %.0f %.0f r=%.0f/%.0f ry=%.0f floor=%.0f hall=%s shaft=%s%n", st.x, st.y, st.z, st.rx, st.rz, st.ry, st.floorY, st.hall, st.shaft != null);
        slice(out + "/slice_station.png", (int) st.x, (int) st.z, (int) st.floorY + 10, 1024, 1);
        System.out.println("slice " + (System.nanoTime()-t0)/1e6 + "ms"); t0 = System.nanoTime();
        section(out + "/section_x.png", (int) st.x - 600, (int) st.x + 600, (int) st.z, true);
        section(out + "/section_z.png", (int) st.z - 600, (int) st.z + 600, (int) st.x, false);
        System.out.println("sections " + (System.nanoTime()-t0)/1e6 + "ms");
        // a cross-section across the middle of the east tunnel of station 0,0
        TunnelNetwork.Tunnel tn = T.network.tunnel(0, 0, TunnelNetwork.DIR_EAST);
        if (tn == null) tn = T.network.tunnel(0, 0, TunnelNetwork.DIR_SOUTH);
        if (tn == null) tn = T.network.tunnel(-1, 0, TunnelNetwork.DIR_EAST);
        if (tn != null) {
            int k = tn.n / 2;
            System.out.printf("tunnel %s dir=%d len=%.0f mid=(%.0f,%.0f,%.0f) rh=%.1f rv=%.1f%n", tn.cls, tn.dir, tn.length, tn.px[k], tn.py[k], tn.pz[k], tn.rh[k], tn.rv[k]);
            // perpendicular section
            if (tn.dir == TunnelNetwork.DIR_EAST) sectionZoom(out + "/tunnel_cross.png", (int) tn.pz[k] - 80, (int) tn.pz[k] + 80, (int) tn.px[k], false, (int) tn.py[k] - 70, (int) tn.py[k] + 70);
            else sectionZoom(out + "/tunnel_cross.png", (int) tn.px[k] - 80, (int) tn.px[k] + 80, (int) tn.pz[k], true, (int) tn.py[k] - 70, (int) tn.py[k] + 70);
            // slice at tunnel depth along whole tunnel
            slice(out + "/slice_tunnel.png", (int) tn.px[k], (int) tn.pz[k], (int) tn.py[k], 1400, 2);
        }
        // seabed sections through each province type: sample along a long line
        section(out + "/section_long.png", -2048, 2048, 300, true);
        perf();
    }

    static int provColor(Province p) {
        switch (p) {
            case ABYSSAL_PLAINS: return 0xC8B98A;
            case SPIRE_FIELDS: return 0x8A8A8A;
            case PILLAR_FOREST: return 0x6E5A4A;
            case MONOLITH_REACH: return 0xA07850;
            case KELP_FOREST: return 0x3C8C3C;
            case CORAL_GARDENS: return 0xE07AA0;
            case EUROPAN_RIDGE: return 0xB0B0D0;
            case HYDROTHERMAL_WASTES: return 0xC0402A;
            case HADAL_TRENCH: return 0x203070;
        }
        return 0;
    }

    static int shade(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    static void topDown(String file, int cx, int cz, int size, int step) throws Exception {
        int n = size / step;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            int x = cx - size / 2 + i * step, z = cz - size / 2 + j * step;
            OceanTerrain.Column c = T.column(x, z);
            int col = provColor(c.ps.dominant);
            double h = c.seabed;
            // find top solid above seabed (formations)
            double top = h;
            for (int y = (int) h + 1; y < Math.min(T.waterLevel, (int) (T.ice ? c.iceBottom : 300)); y += 2) {
                if (T.density(c, y) > 0) top = y; else if (y > h + 130) break;
            }
            double f = 0.55 + (top + 200) / 500.0;
            col = shade(col, f);
            if (top > h + 6) col = shade(0xFFFFFF, 0.5 + Math.min(0.5, (top - h) / 300.0));
            // network overlay: any cavity in column
            boolean cav = false; boolean station = false;
            for (double[] sp : cavs(c)) { cav = true; if (sp[2] == 1) station = true; }
            if (cav) col = station ? 0xFFFF00 : (col & 0xFEFEFE) / 2 + 0x00407F;
            img.setRGB(i, j, col);
        }
        ImageIO.write(img, "png", new File(file));
    }

    static List<double[]> cavs(OceanTerrain.Column c) { List<double[]> l = new ArrayList<>(); T.cavitySpans(c, l); return l; }

    static void slice(String file, int cx, int cz, int y, int size, int step) throws Exception {
        int n = size / step;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            int x = cx - size / 2 + i * step, z = cz - size / 2 + j * step;
            OceanTerrain.Column c = T.column(x, z);
            double d = T.density(c, y);
            int col = d > 0 ? 0x505050 : 0x1E4A8C;
            img.setRGB(i, j, col);
        }
        ImageIO.write(img, "png", new File(file));
    }

    static int slotColor(BiomeSlot s) {
        switch (s) {
            case ICE_SHELF: return 0xBFE6FF;
            case GREAT_SEA: return 0x10306A;
            case SPACIOUS_CHANNEL: return 0x2E86C1;
            case STANDARD_CHANNEL: return 0x48C9B0;
            case NARROW_CHANNEL: return 0xA569BD;
            case STATION: return 0xF4D03F;
            case RAVINE: return 0xE67E22;
            case DEEP_CRUST: return 0x2A2A2A;
            case ROCK_POCKET: return 0x8E44AD;
            default: return provColor(Province.valueOf(s.name()));
        }
    }

    static void section(String file, int a0, int a1, int fixed, boolean alongX) throws Exception {
        sectionZoom(file, a0, a1, fixed, alongX, MINY, MAXY);
    }

    static void sectionZoom(String file, int a0, int a1, int fixed, boolean alongX, int y0, int y1) throws Exception {
        int w = a1 - a0, h = y1 - y0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        OceanTerrain.SlotResult sr = new OceanTerrain.SlotResult();
        for (int i = 0; i < w; i++) {
            int x = alongX ? a0 + i : fixed, z = alongX ? fixed : a0 + i;
            OceanTerrain.Column c = T.column(x, z);
            for (int yy = 0; yy < h; yy++) {
                int y = y1 - 1 - yy;
                double d = T.density(c, y);
                T.slot(c, y, sr);
                int col;
                if (d > 0) {
                    col = (T.ice && y >= c.iceBottom - 1) ? 0xDDEEFF : shade(slotColor(sr.slot), 0.35);
                } else {
                    col = y >= T.waterLevel ? 0xFFFFFF : shade(slotColor(sr.slot), 0.9);
                }
                img.setRGB(i, yy, col);
            }
        }
        ImageIO.write(img, "png", new File(file));
    }

    static void perf() {
        // emulate a chunk: 5x5 corner columns x 209 samples
        long t0 = System.nanoTime();
        int chunks = 0;
        double sink = 0;
        for (int cx = 0; cx < 16; cx++) for (int cz = 0; cz < 16; cz++) {
            for (int i = 0; i <= 4; i++) for (int j = 0; j <= 4; j++) {
                OceanTerrain.Column c = T.column(cx * 16 + i * 4 + 5000, cz * 16 + j * 4 + 7000);
                for (int y = MINY; y <= MAXY; y += 4) sink += T.density(c, y);
            }
            chunks++;
        }
        System.out.printf("perf: %.2f ms/chunk (%d chunks) %s%n", (System.nanoTime() - t0) / 1e6 / chunks, chunks, sink > 0 ? "" : " ");
    }
}
