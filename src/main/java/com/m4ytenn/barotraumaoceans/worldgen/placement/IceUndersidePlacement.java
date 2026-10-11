package com.m4ytenn.barotraumaoceans.worldgen.placement;

import com.m4ytenn.barotraumaoceans.registry.BORegistries;
import com.m4ytenn.barotraumaoceans.worldgen.TerrainAccess;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/** Moves a position to the water block right below the underside of the ice crust. */
public class IceUndersidePlacement extends PlacementModifier {
    public static final IceUndersidePlacement INSTANCE = new IceUndersidePlacement();
    public static final MapCodec<IceUndersidePlacement> CODEC = MapCodec.unit(() -> INSTANCE);

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        OceanTerrain t = TerrainAccess.terrain(context.getLevel());
        if (t == null || !t.ice) {
            return Stream.empty();
        }
        int y = (int) Math.floor(t.seabed.iceBottom(pos.getX(), pos.getZ())) - 8;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(pos.getX(), y, pos.getZ());
        if (!context.getBlockState(m).getFluidState().is(FluidTags.WATER)) {
            return Stream.empty();
        }
        for (int i = 0; i < 40; i++) {
            m.move(0, 1, 0);
            if (!context.getBlockState(m).getFluidState().is(FluidTags.WATER)) {
                return Stream.of(m.below().immutable());
            }
        }
        return Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return BORegistries.ICE_UNDERSIDE.get();
    }
}
