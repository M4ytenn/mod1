package com.m4ytenn.barotraumaoceans.worldgen.core;

/**
 * Logical "places" of the world. The biome source maps each slot to a weighted list of biomes
 * configured in {@link OceanSettings} (section "biomes").
 */
public enum BiomeSlot {
    ICE_SHELF(OceanSettings.B_ICE, "barotrauma_oceans:ice_shelf"),
    GREAT_SEA(OceanSettings.B_GREAT_SEA, "barotrauma_oceans:the_great_sea"),
    ABYSSAL_PLAINS(OceanSettings.B_PLAINS, "barotrauma_oceans:abyssal_plains"),
    SPIRE_FIELDS(OceanSettings.B_SPIRES, "barotrauma_oceans:spire_fields"),
    PILLAR_FOREST(OceanSettings.B_PILLARS, "barotrauma_oceans:pillar_forest"),
    MONOLITH_REACH(OceanSettings.B_MONOLITHS, "barotrauma_oceans:monolith_reach"),
    KELP_FOREST(OceanSettings.B_KELP, "barotrauma_oceans:kelp_forest"),
    CORAL_GARDENS(OceanSettings.B_GARDENS, "barotrauma_oceans:coral_gardens"),
    EUROPAN_RIDGE(OceanSettings.B_RIDGE, "barotrauma_oceans:europan_ridge"),
    HYDROTHERMAL_WASTES(OceanSettings.B_VENTS, "barotrauma_oceans:hydrothermal_wastes"),
    HADAL_TRENCH(OceanSettings.B_TRENCH, "barotrauma_oceans:hadal_trench"),
    RAVINE(OceanSettings.B_RAVINE, "barotrauma_oceans:abyssal_ravine"),
    SPACIOUS_CHANNEL(OceanSettings.B_SPACIOUS, "barotrauma_oceans:great_channels"),
    STANDARD_CHANNEL(OceanSettings.B_STANDARD, "barotrauma_oceans:sunken_channels"),
    NARROW_CHANNEL(OceanSettings.B_NARROW, "barotrauma_oceans:narrow_passages"),
    STATION(OceanSettings.B_STATION, "barotrauma_oceans:station_hollow"),
    DEEP_CRUST(OceanSettings.B_CRUST, "barotrauma_oceans:deep_crust"),
    ROCK_POCKET(OceanSettings.B_POCKETS, null);

    public static final BiomeSlot[] VALUES = values();

    public final OceanSettings.Param listParam;
    /** Biome used when the configured list resolves to nothing. Null means "fall back to DEEP_CRUST". */
    public final String fallbackBiome;

    BiomeSlot(OceanSettings.Param listParam, String fallbackBiome) {
        this.listParam = listParam;
        this.fallbackBiome = fallbackBiome;
    }
}
