package com.m4ytenn.barotraumaoceans.worldgen;

import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

/** Helpers to reach the terrain of a Barotrauma Oceans level from game objects. */
public final class TerrainAccess {
    private TerrainAccess() {
    }

    /** Terrain seed carried by our noise settings (0 when the random state was built from other settings). */
    public static long seedOf(RandomState state) {
        if (state.sampler().temperature() instanceof SeedCarrier carrier && carrier.isSeeded()) {
            return carrier.terrainSeed();
        }
        if (state.router().temperature() instanceof SeedCarrier carrier && carrier.isSeeded()) {
            return carrier.terrainSeed();
        }
        return 0L;
    }

    @Nullable
    public static OceanChunkGenerator generator(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof OceanChunkGenerator gen ? gen : null;
    }

    public static boolean isOceanLevel(ServerLevel level) {
        return generator(level) != null;
    }

    @Nullable
    public static OceanTerrain terrain(ServerLevel level) {
        OceanChunkGenerator gen = generator(level);
        return gen == null ? null : gen.terrain(level.getChunkSource().randomState());
    }

    @Nullable
    public static OceanTerrain terrain(WorldGenLevel level) {
        return terrain(level.getLevel());
    }
}
