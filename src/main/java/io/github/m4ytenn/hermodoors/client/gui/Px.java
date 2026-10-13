package io.github.m4ytenn.hermodoors.client.gui;

import io.github.m4ytenn.hermodoors.HermoDoors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Small pixel-art drawing kit for the panels. Moving parts (needles, levers, spokes) are rasterised pixel by
 * pixel instead of rotating sprites, so they stay as crisp as the painted panel behind them.
 */
public final class Px {
    public static final ResourceLocation STENCIL_FONT = HermoDoors.id("stencil");

    private Px() {}

    public static void dot(GuiGraphics g, int x, int y, int argb) {
        g.fill(x, y, x + 1, y + 1, argb);
    }

    /** One pixel wide line (Bresenham). */
    public static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int argb) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            dot(g, x0, y0, argb);
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** Dotted line: every other pixel, for faded pencil on the schematic. */
    public static void dotted(GuiGraphics g, int x0, int y0, int x1, int y1, int argb, int seed) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            if (((i + seed) & 3) < 2) {
                float t = steps == 0 ? 0 : i / (float) steps;
                dot(g, Math.round(x0 + (x1 - x0) * t), Math.round(y0 + (y1 - y0) * t), argb);
            }
        }
    }

    /**
     * A shaded bar with round ends between two points, rasterised per pixel. Pixels on the upper-left edge get
     * {@code light}, the lower-right edge {@code dark}, the rest {@code base}.
     */
    public static void capsule(GuiGraphics g, float x0, float y0, float x1, float y1, float radius, int base, int light, int dark) {
        int minX = (int) Math.floor(Math.min(x0, x1) - radius - 1);
        int maxX = (int) Math.ceil(Math.max(x0, x1) + radius + 1);
        int minY = (int) Math.floor(Math.min(y0, y1) - radius - 1);
        int maxY = (int) Math.ceil(Math.max(y0, y1) + radius + 1);
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        float len = (float) Math.sqrt(len2);
        // Unit normal pointing up-left, used to decide which edge catches the light.
        float nx = len == 0 ? 0 : -dy / len;
        float ny = len == 0 ? -1 : dx / len;
        if (nx + ny > 0) {
            nx = -nx;
            ny = -ny;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float t = len2 == 0 ? 0 : ((px - x0) * dx + (py - y0) * dy) / len2;
                t = Math.max(0, Math.min(1, t));
                float cx = x0 + dx * t;
                float cy = y0 + dy * t;
                float ox = px - cx;
                float oy = py - cy;
                float dist = (float) Math.sqrt(ox * ox + oy * oy);
                if (dist > radius) {
                    continue;
                }
                float side = ox * nx + oy * ny;
                int color = base;
                if (dist > radius - 1.1f) {
                    color = side > 0 ? light : dark;
                }
                dot(g, x, y, color);
            }
        }
    }

    /** Filled circle around a point given in pixel-corner coordinates. */
    public static void disc(GuiGraphics g, float cx, float cy, float radius, int argb) {
        arc(g, cx, cy, 0, radius, 0, 360, argb);
    }

    /**
     * Ring segment between two radii and two angles (degrees, screen space: 0 = right, 90 = down). A pixel is
     * filled when its centre falls inside.
     */
    public static void arc(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1, int argb) {
        float lo = Math.min(a0, a1);
        float hi = Math.max(a0, a1);
        boolean full = hi - lo >= 360f;
        int minX = (int) Math.floor(cx - r1);
        int maxX = (int) Math.ceil(cx + r1);
        int minY = (int) Math.floor(cy - r1);
        int maxY = (int) Math.ceil(cy + r1);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f - cx;
                float py = y + 0.5f - cy;
                float d2 = px * px + py * py;
                if (d2 < r0 * r0 || d2 > r1 * r1) {
                    continue;
                }
                if (full || inSweep((float) Math.toDegrees(Math.atan2(py, px)), lo, hi)) {
                    dot(g, x, y, argb);
                }
            }
        }
    }

    private static boolean inSweep(float angle, float lo, float hi) {
        for (int k = -1; k <= 2; k++) {
            float a = angle + 360f * k;
            if (a >= lo && a <= hi) {
                return true;
            }
        }
        return false;
    }

    /** Point on a circle, screen-space degrees. */
    public static float px(float cx, float r, float deg) {
        return cx + r * (float) Math.cos(Math.toRadians(deg));
    }

    public static float py(float cy, float r, float deg) {
        return cy + r * (float) Math.sin(Math.toRadians(deg));
    }

    // ---------------------------------------------------------------- text

    public static MutableComponent stencil(String text) {
        return Component.literal(text).withStyle(s -> s.withFont(STENCIL_FONT));
    }

    public static MutableComponent stencilKey(String key, Object... args) {
        return Component.translatable(key, args).withStyle(s -> s.withFont(STENCIL_FONT));
    }

    /** Engraved or painted panel lettering (no drop shadow). */
    public static void engrave(GuiGraphics g, Font font, Component text, int x, int y, int argb) {
        g.drawString(font, text, x, y, argb, false);
    }

    public static void engraveCentered(GuiGraphics g, Font font, Component text, int cx, int y, int argb) {
        g.drawString(font, text, cx - font.width(text) / 2, y, argb, false);
    }

    /**
     * Typewritten text: every letter struck separately in ribbon ink, some a pixel low and some fainter,
     * deterministic per {@code seed} so it does not crawl from frame to frame. Uses the regular font, which
     * stays crisp at every GUI scale.
     */
    public static int typewrite(GuiGraphics g, Font font, String text, int x, int y, int ink, int seed, boolean jitter) {
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int h = hash(seed, i);
            int dy = jitter && (h & 7) == 0 ? 1 : 0;
            int color = jitter && ((h >> 3) & 3) == 0 ? fade(ink, 0.78f) : ink;
            g.drawString(font, ch, cx, y + dy, color, false);
            cx += font.width(ch);
        }
        return cx;
    }

    public static int hash(int seed, int i) {
        int h = seed * 0x9E3779B1 + i * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Blend a colour toward transparent-white paper, keeping alpha. */
    public static int fade(int argb, float strength) {
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int gr = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) (r * strength + 236 * (1 - strength));
        gr = (int) (gr * strength + 228 * (1 - strength));
        b = (int) (b * strength + 206 * (1 - strength));
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    public static int withAlpha(int rgb, float alpha) {
        return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- sound

    public static void sound(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
