package com.m4ytenn.barotraumaoceans.worldgen;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** Keys of the biomes and biome tags shipped with the mod (the biomes themselves are data files). */
public final class BOBiomes {
    public static final ResourceKey<Biome> ICE_SHELF = key("ice_shelf");
    public static final ResourceKey<Biome> THE_GREAT_SEA = key("the_great_sea");
    public static final ResourceKey<Biome> ABYSSAL_PLAINS = key("abyssal_plains");
    public static final ResourceKey<Biome> SPIRE_FIELDS = key("spire_fields");
    public static final ResourceKey<Biome> PILLAR_FOREST = key("pillar_forest");
    public static final ResourceKey<Biome> MONOLITH_REACH = key("monolith_reach");
    public static final ResourceKey<Biome> KELP_FOREST = key("kelp_forest");
    public static final ResourceKey<Biome> CORAL_GARDENS = key("coral_gardens");
    public static final ResourceKey<Biome> EUROPAN_RIDGE = key("europan_ridge");
    public static final ResourceKey<Biome> HYDROTHERMAL_WASTES = key("hydrothermal_wastes");
    public static final ResourceKey<Biome> HADAL_TRENCH = key("hadal_trench");
    public static final ResourceKey<Biome> ABYSSAL_RAVINE = key("abyssal_ravine");
    public static final ResourceKey<Biome> GREAT_CHANNELS = key("great_channels");
    public static final ResourceKey<Biome> SUNKEN_CHANNELS = key("sunken_channels");
    public static final ResourceKey<Biome> NARROW_PASSAGES = key("narrow_passages");
    public static final ResourceKey<Biome> LUMINOUS_CAVERNS = key("luminous_caverns");
    public static final ResourceKey<Biome> COLD_CAVERNS = key("cold_caverns");
    public static final ResourceKey<Biome> STATION_HOLLOW = key("station_hollow");
    public static final ResourceKey<Biome> DEEP_CRUST = key("deep_crust");

    /** All biomes of the mod. */
    public static final TagKey<Biome> TAG_ALL = TagKey.create(Registries.BIOME, BarotraumaOceans.id("all"));
    /** Biomes where every water creature may spawn at any depth (see config gameplay.compat). */
    public static final TagKey<Biome> TAG_RELAXED_SPAWNING = TagKey.create(Registries.BIOME, BarotraumaOceans.id("relaxed_water_spawning"));
    /** Biomes that get the client-side depth fog. */
    public static final TagKey<Biome> TAG_DEPTH_FOG = TagKey.create(Registries.BIOME, BarotraumaOceans.id("depth_fog"));

    private BOBiomes() {
    }

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, BarotraumaOceans.id(name));
    }
}
