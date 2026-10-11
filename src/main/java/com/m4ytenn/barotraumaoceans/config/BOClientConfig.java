package com.m4ytenn.barotraumaoceans.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (config/barotrauma_oceans-client.toml): visuals only. */
public final class BOClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue DEPTH_FOG;
    public static final ModConfigSpec.IntValue FOG_FADE_START;
    public static final ModConfigSpec.IntValue FOG_FADE_RANGE;
    public static final ModConfigSpec.IntValue MIN_FOG_DISTANCE;
    public static final ModConfigSpec.DoubleValue DEPTH_DARKENING;
    public static final ModConfigSpec.BooleanValue ONLY_IN_MOD_WORLDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Underwater visibility in Barotrauma Oceans biomes.")
                .translation(BOConfig.LANG_PREFIX + "fog").push("fog");
        DEPTH_FOG = b.comment("Make underwater fog denser and darker the deeper you are.")
                .translation(BOConfig.LANG_PREFIX + "fog.depth_fog")
                .define("depth_fog", true);
        FOG_FADE_START = b.comment("Y level where the fog starts to get denser (default: just below the ice crust).")
                .translation(BOConfig.LANG_PREFIX + "fog.start_y")
                .defineInRange("start_y", 230, -2048, 2048);
        FOG_FADE_RANGE = b.comment("Over how many blocks of depth the fog reaches its densest value.")
                .translation(BOConfig.LANG_PREFIX + "fog.fade_range")
                .defineInRange("fade_range", 220, 1, 2048);
        MIN_FOG_DISTANCE = b.comment("Visibility distance in blocks at the greatest depth.")
                .translation(BOConfig.LANG_PREFIX + "fog.min_distance")
                .defineInRange("min_distance", 40, 4, 512);
        DEPTH_DARKENING = b.comment("How much the fog colour darkens at the greatest depth (0 = not at all, 1 = black).")
                .translation(BOConfig.LANG_PREFIX + "fog.depth_darkening")
                .defineInRange("depth_darkening", 0.7, 0.0, 1.0);
        ONLY_IN_MOD_WORLDS = b.comment("Only apply the depth fog in Barotrauma Oceans biomes (tag barotrauma_oceans:depth_fog).")
                .translation(BOConfig.LANG_PREFIX + "fog.only_mod_biomes")
                .define("only_mod_biomes", true);
        b.pop();
        SPEC = b.build();
    }

    private BOClientConfig() {
    }
}
