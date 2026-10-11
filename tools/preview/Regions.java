import com.m4ytenn.barotraumaoceans.worldgen.core.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
public class Regions {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        OceanTerrain T = OceanTerrain.get(seed, OceanSettings.defaults());
        Preview.T = T;
        ProvinceMap.Sample ps = new ProvinceMap.Sample();
        for (Province p : Province.VALUES) {
            // find a spot deep inside this province
            int fx = 0, fz = 0; boolean found = false;
            outer:
            for (int r = 0; r < 6000 && !found; r += 64) for (int ang = 0; ang < 16; ang++) {
                int x = (int) (r * Math.cos(ang * Math.PI / 8)), z = (int) (r * Math.sin(ang * Math.PI / 8));
                T.provinces.sample(x, z, ps);
                if (ps.weight(p) > 0.999) { fx = x; fz = z; found = true; break outer; }
            }
            if (!found) { System.out.println("no " + p); continue; }
            System.out.println(p + " at " + fx + "," + fz);
            String n = p.name().toLowerCase();
            relief(T, a[1] + "/region_" + n + ".png", fx, fz, 320);
            Preview.sectionZoom(a[1] + "/region_" + n + "_side.png", fx - 200, fx + 200, fz, true, -260, 280);
        }
    }
    static void relief(OceanTerrain T, String file, int cx, int cz, int size) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) {
            int x = cx - size / 2 + i, z = cz - size / 2 + j;
            OceanTerrain.Column c = T.column(x, z);
            double top = c.seabed - 20;
            int lim = (int) Math.min(T.waterLevel, T.ice ? c.iceBottom - 1 : 300);
            for (int y = lim; y > c.seabed - 30; y--) { if (T.density(c, y) > 0) { top = y; break; } }
            double f = Math.max(0, Math.min(1, (top - c.seabed + 30) / 160.0));
            int g = (int) (40 + 215 * f);
            int col = (g << 16) | (g << 8) | g;
            if (top >= lim - 1) col = 0xFFFFFF;
            img.setRGB(i, j, col);
        }
        ImageIO.write(img, "png", new File(file));
    }
}
