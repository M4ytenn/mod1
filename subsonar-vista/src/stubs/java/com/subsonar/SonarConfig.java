package com.subsonar;

import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

/** Stub: see com/subsonar/README.txt. */
public final class SonarConfig {
    public static IntValue PASSIVE_RANGE;
    public static IntValue DISPLAY_RESOLUTION;
    public static DoubleValue PING_TRAVEL_SECONDS;
    public static DoubleValue ECHO_FADE_SECONDS;
    public static DoubleValue CONTACT_FADE_SECONDS;
    public static DoubleValue PASSIVE_SWEEP_SECONDS;
    public static BooleanValue PERSISTENT_CHART;
    public static DoubleValue CHART_BRIGHTNESS;
    public static BooleanValue CRT_POWER_EFFECT;
    public static BooleanValue HEADING_UP_DEFAULT;
    public static BooleanValue SHOW_GRID;
    public static BooleanValue SHOW_SILHOUETTE;
    public static BooleanValue PROXIMITY_WARNING;
    public static DoubleValue DEFAULT_GAIN;
    public static BooleanValue RATE_AFFECTS_VISUALS;

    private SonarConfig() {}

    public static double sget(DoubleValue v) { throw new AssertionError(); }

    public static int sget(IntValue v) { throw new AssertionError(); }

    public static boolean sget(BooleanValue v) { throw new AssertionError(); }

    public static double cget(DoubleValue v) { throw new AssertionError(); }

    public static int cget(IntValue v) { throw new AssertionError(); }

    public static boolean cget(BooleanValue v) { throw new AssertionError(); }
}
