package io.github.m4ytenn.hermodoors.client.gui;

import io.github.m4ytenn.hermodoors.HermoDoors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture sheets and sprite positions. The sheets are hand-tuned pixel art; coordinates here must match
 * the PNGs in assets/hermodoors/textures/gui.
 */
public final class Art {
    public static final ResourceLocation PANEL = tex("panel");
    public static final ResourceLocation POWER = tex("stage_power");
    public static final ResourceLocation PUMP = tex("stage_pump");
    public static final ResourceLocation CLAMPS = tex("stage_clamps");
    public static final ResourceLocation WHEEL = tex("stage_wheel");
    public static final ResourceLocation PASSPORT = tex("passport");

    // panel.png (256 x 256)
    public static final int LAMP_SIZE = 10;
    public static final int LAMP_Y = 184;
    public static final int LAMP_OFF = 0;
    public static final int LAMP_RED = 10;
    public static final int LAMP_GREEN = 20;
    public static final int LAMP_AMBER = 30;
    public static final int SMALL_LAMP_SIZE = 6;
    public static final int SMALL_LAMP_Y = 184;
    public static final int SMALL_LAMP_OFF = 50;
    public static final int SMALL_LAMP_GREEN = 56;
    public static final int SMALL_LAMP_RED = 62;
    public static final int BLANK_PLATE_U = 0;
    public static final int BLANK_PLATE_V = 196;
    public static final int BLANK_PLATE_W = 64;
    public static final int BLANK_PLATE_H = 26;
    public static final int TAG_U = 100;
    public static final int TAG_V = 184;
    public static final int TAG_W = 16;
    public static final int TAG_H = 7;

    // Palette shared by runtime-drawn parts.
    public static final int INK_BLACK = 0xFF1C1A17;
    public static final int ENGRAVE_CREAM = 0xFFE6DDC4;
    public static final int ENGRAVE_DARK = 0xFF23211D;
    public static final int PAINT_WHITE = 0xFFD8D3C4;
    public static final int PENCIL = 0xFF3B4250;
    public static final int PENCIL_FAINT = 0xFF8C8E8A;
    public static final int CHALK = 0xFFE9E7DF;
    public static final int NEEDLE = 0xFF151515;
    public static final int NEEDLE_RED = 0xFFB0281C;
    public static final int STEEL = 0xFF6E7376;
    public static final int STEEL_LIGHT = 0xFFA4A9AA;
    public static final int STEEL_DARK = 0xFF3A3E40;
    public static final int RUST = 0xFF7E4122;
    public static final int RUST_LIGHT = 0xFFA4602F;
    public static final int RUST_DARK = 0xFF4E2814;
    public static final int WHEEL_RED = 0xFF8E2A1E;
    public static final int WHEEL_RED_LIGHT = 0xFFB8452F;
    public static final int WHEEL_RED_DARK = 0xFF55170F;
    public static final int GAUGE_GREEN = 0xFF2F7A3C;
    public static final int TYPE_INK = 0xFF2A2740;
    public static final int STAMP_VIOLET = 0xFF5B3A8C;
    public static final int RED_PENCIL = 0xFFA3291F;

    private Art() {}

    private static ResourceLocation tex(String name) {
        return HermoDoors.id("textures/gui/" + name + ".png");
    }

    public static void blit(GuiGraphics g, ResourceLocation sheet, int x, int y, int u, int v, int w, int h) {
        g.blit(sheet, x, y, (float) u, (float) v, w, h, 256, 256);
    }

    public static void blit(GuiGraphics g, ResourceLocation sheet, int x, int y, int u, int v, int w, int h, int sheetW, int sheetH) {
        g.blit(sheet, x, y, (float) u, (float) v, w, h, sheetW, sheetH);
    }

    public static void lamp(GuiGraphics g, int x, int y, int lampU) {
        blit(g, PANEL, x, y, lampU, LAMP_Y, LAMP_SIZE, LAMP_SIZE);
    }

    public static void smallLamp(GuiGraphics g, int x, int y, int lampU) {
        blit(g, PANEL, x, y, lampU, SMALL_LAMP_Y, SMALL_LAMP_SIZE, SMALL_LAMP_SIZE);
    }

    /**
     * Black engraved tag (16 x 7) with cream lettering, as screwed under breakers and switches.
     * Stencil glyphs have their 5 px capitals on rows 1..5 of the line, so drawing at the tag's top centres them.
     */
    public static void tag(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int x, int y) {
        blit(g, PANEL, x, y, TAG_U, TAG_V, TAG_W, TAG_H);
        Px.engraveCentered(g, font, Px.stencil(text), x + TAG_W / 2, y, ENGRAVE_CREAM);
    }
}
