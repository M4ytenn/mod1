package com.m4ytenn.barotraumaoceans.compat;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
import com.m4ytenn.barotraumaoceans.config.BOConfig;
import com.m4ytenn.barotraumaoceans.worldgen.BOBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vanilla and most modded sea creatures (Hybrid Aquatic, Alex's Caves...) only spawn within a band below sea
 * level. In a world where the playable ocean is hundreds of blocks deep that leaves the abyss empty, so inside
 * our biomes every water creature may additionally spawn at any depth as long as it is in water.
 */
public final class SpawnRules {
    private SpawnRules() {
    }

    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        boolean enabled = !BOConfig.SPEC.isLoaded() || BOConfig.RELAX_WATER_MOB_DEPTH.get();
        if (!enabled) {
            return;
        }
        Set<String> extra = new HashSet<>();
        if (BOConfig.SPEC.isLoaded()) {
            List<? extends String> list = BOConfig.RELAXED_EXTRA_MOBS.get();
            extra.addAll(list);
        }
        int count = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            MobCategory cat = type.getCategory();
            boolean water = cat == MobCategory.WATER_CREATURE || cat == MobCategory.WATER_AMBIENT || cat == MobCategory.UNDERGROUND_WATER_CREATURE;
            if (!water && !extra.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())) {
                continue;
            }
            if (relax(event, type)) {
                count++;
            }
        }
        BarotraumaOceans.LOGGER.info("Barotrauma Oceans: relaxed depth spawn rules for {} water mob types", count);
    }

    private static <T extends Entity> boolean relax(RegisterSpawnPlacementsEvent event, EntityType<T> type) {
        try {
            event.register(type, SpawnRules::relaxed, RegisterSpawnPlacementsEvent.Operation.OR);
            return true;
        } catch (RuntimeException e) {
            // The entity type has no spawn placement at all (it never spawns naturally) - nothing to relax.
            return false;
        }
    }

    private static boolean relaxed(EntityType<?> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        if (!level.getBiome(pos).is(BOBiomes.TAG_RELAXED_SPAWNING)) {
            return false;
        }
        if (type.getCategory() == MobCategory.MONSTER) {
            return level.getDifficulty() != Difficulty.PEACEFUL;
        }
        return true;
    }
}
