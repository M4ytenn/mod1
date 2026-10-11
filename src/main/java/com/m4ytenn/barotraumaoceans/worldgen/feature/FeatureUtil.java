package com.m4ytenn.barotraumaoceans.worldgen.feature;

import com.m4ytenn.barotraumaoceans.worldgen.TerrainAccess;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanSettings;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Small helpers shared by the underwater features. */
final class FeatureUtil {
    private FeatureUtil() {
    }

    static boolean isWaterSource(WorldGenLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.is(Blocks.WATER) && s.getFluidState().isSource();
    }

    static boolean isWater(WorldGenLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    /** Moves down from {@code start} through water to the first sturdy floor; returns the water block above it. */
    @Nullable
    static BlockPos floorBelow(WorldGenLevel level, BlockPos start, int maxSteps) {
        BlockPos.MutableBlockPos m = start.mutable();
        if (!isWater(level, m)) {
            // started inside the ground: look up a little for the surface
            for (int i = 0; i < 6 && !isWater(level, m); i++) {
                m.move(Direction.UP);
            }
            if (!isWater(level, m)) {
                return null;
            }
        }
        for (int i = 0; i < maxSteps; i++) {
            BlockPos below = m.below();
            if (!isWater(level, below)) {
                return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) ? m.immutable() : null;
            }
            m.move(Direction.DOWN);
        }
        return null;
    }

    @Nullable
    static OceanSettings settings(WorldGenLevel level) {
        OceanTerrain t = TerrainAccess.terrain(level);
        return t == null ? null : t.settings;
    }

    @Nullable
    static OceanTerrain terrain(WorldGenLevel level) {
        return TerrainAccess.terrain(level);
    }

    static boolean writable(WorldGenLevel level, BlockPos pos) {
        return level.ensureCanWrite(pos);
    }
}
