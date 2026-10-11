package com.m4ytenn.barotraumaoceans.worldgen.core;

/**
 * Seabed region types. Each region has its own terrain shape; the biome inside a region is picked
 * from the matching biome slot (see {@link BiomeSlot}).
 */
public enum Province {
    ABYSSAL_PLAINS(OceanSettings.W_PLAINS, BiomeSlot.ABYSSAL_PLAINS, 0.8),
    SPIRE_FIELDS(OceanSettings.W_SPIRES, BiomeSlot.SPIRE_FIELDS, 2.0),
    PILLAR_FOREST(OceanSettings.W_PILLARS, BiomeSlot.PILLAR_FOREST, 1.2),
    MONOLITH_REACH(OceanSettings.W_MONOLITHS, BiomeSlot.MONOLITH_REACH, 4.0),
    KELP_FOREST(OceanSettings.W_KELP, BiomeSlot.KELP_FOREST, 0.8),
    CORAL_GARDENS(OceanSettings.W_GARDENS, BiomeSlot.CORAL_GARDENS, 1.5),
    EUROPAN_RIDGE(OceanSettings.W_RIDGE, BiomeSlot.EUROPAN_RIDGE, 7.0),
    HYDROTHERMAL_WASTES(OceanSettings.W_VENTS, BiomeSlot.HYDROTHERMAL_WASTES, 3.0),
    HADAL_TRENCH(OceanSettings.W_TRENCH, BiomeSlot.HADAL_TRENCH, 3.0);

    public static final Province[] VALUES = values();

    public final OceanSettings.Param weightParam;
    public final BiomeSlot slot;
    /** Amplitude (blocks) of 3D overhang noise near the seabed surface. */
    public final double overhang;

    Province(OceanSettings.Param weightParam, BiomeSlot slot, double overhang) {
        this.weightParam = weightParam;
        this.slot = slot;
        this.overhang = overhang;
    }
}
