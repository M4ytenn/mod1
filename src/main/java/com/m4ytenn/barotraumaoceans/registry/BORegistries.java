package com.m4ytenn.barotraumaoceans.registry;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
import com.m4ytenn.barotraumaoceans.worldgen.OceanBiomeSource;
import com.m4ytenn.barotraumaoceans.worldgen.OceanChunkGenerator;
import com.m4ytenn.barotraumaoceans.worldgen.SeedCarrier;
import com.m4ytenn.barotraumaoceans.worldgen.feature.GiantKelpFeature;
import com.m4ytenn.barotraumaoceans.worldgen.feature.HydrothermalVentFeature;
import com.m4ytenn.barotraumaoceans.worldgen.feature.SeabedMeadowFeature;
import com.m4ytenn.barotraumaoceans.worldgen.feature.SeabedSpikeFeature;
import com.m4ytenn.barotraumaoceans.worldgen.placement.CavityPlacement;
import com.m4ytenn.barotraumaoceans.worldgen.placement.IceUndersidePlacement;
import com.m4ytenn.barotraumaoceans.worldgen.placement.ScaledCountPlacement;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BORegistries {
    private static final String MODID = BarotraumaOceans.MODID;

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, MODID);
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS = DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENTS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, MODID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<OceanChunkGenerator>> OCEAN_GENERATOR =
            CHUNK_GENERATORS.register("ocean", () -> OceanChunkGenerator.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<OceanBiomeSource>> OCEAN_BIOMES =
            BIOME_SOURCES.register("ocean", () -> OceanBiomeSource.CODEC);
    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<SeedCarrier>> SEED_CARRIER =
            DENSITY_FUNCTIONS.register("seed_carrier", () -> SeedCarrier.DATA_CODEC);

    public static final DeferredHolder<Feature<?>, GiantKelpFeature> GIANT_KELP = FEATURES.register("giant_kelp", GiantKelpFeature::new);
    public static final DeferredHolder<Feature<?>, SeabedMeadowFeature> SEABED_MEADOW = FEATURES.register("seabed_meadow", SeabedMeadowFeature::new);
    public static final DeferredHolder<Feature<?>, HydrothermalVentFeature> HYDROTHERMAL_VENT = FEATURES.register("hydrothermal_vent", HydrothermalVentFeature::new);
    public static final DeferredHolder<Feature<?>, SeabedSpikeFeature> ROCK_SPIKE = FEATURES.register("rock_spike", SeabedSpikeFeature::new);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ScaledCountPlacement>> SCALED_COUNT =
            PLACEMENTS.register("scaled_count", () -> type(ScaledCountPlacement.CODEC));
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<CavityPlacement>> CAVITY =
            PLACEMENTS.register("cavity", () -> type(CavityPlacement.CODEC));
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<IceUndersidePlacement>> ICE_UNDERSIDE =
            PLACEMENTS.register("ice_underside", () -> type(IceUndersidePlacement.CODEC));

    private static <P extends PlacementModifier> PlacementModifierType<P> type(MapCodec<P> codec) {
        return () -> codec;
    }

    private BORegistries() {
    }

    public static void register(IEventBus bus) {
        CHUNK_GENERATORS.register(bus);
        BIOME_SOURCES.register(bus);
        DENSITY_FUNCTIONS.register(bus);
        FEATURES.register(bus);
        PLACEMENTS.register(bus);
    }
}
