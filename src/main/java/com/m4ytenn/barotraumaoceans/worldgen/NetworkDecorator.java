package com.m4ytenn.barotraumaoceans.worldgen;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
import com.m4ytenn.barotraumaoceans.worldgen.core.Hashing;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanSettings;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.m4ytenn.barotraumaoceans.worldgen.core.TunnelNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;

/** Places the small things that belong to the network itself: supply chests and glow in crew grottoes. */
public final class NetworkDecorator {
    private NetworkDecorator() {
    }

    public static void decorate(WorldGenLevel level, ChunkAccess chunk, OceanTerrain terrain, OceanSettings settings) {
        if (!terrain.network.enabled() || !settings.getBool(OceanSettings.CREW_ENABLED)) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        int x0 = pos.getMinBlockX();
        int z0 = pos.getMinBlockZ();
        List<TunnelNetwork.CrewTunnel> list = new ArrayList<>();
        terrain.crewTunnelsIn(x0, z0, x0 + 15, z0 + 15, list);
        ResourceKey<LootTable> loot = null;
        for (TunnelNetwork.CrewTunnel ct : list) {
            if (ct.gr <= 0) {
                continue;
            }
            int gx = (int) Math.floor(ct.gx);
            int gz = (int) Math.floor(ct.gz);
            if (gx < x0 || gx > x0 + 15 || gz < z0 || gz > z0 + 15) {
                continue;
            }
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(gx, (int) Math.floor(ct.gy), gz);
            int limit = (int) Math.ceil(ct.gr) + 3;
            int steps = 0;
            while (steps++ < limit && level.getFluidState(m).is(FluidTags.WATER)) {
                m.move(Direction.DOWN);
            }
            if (level.getFluidState(m).is(FluidTags.WATER) || level.getBlockState(m).isAir()) {
                continue;
            }
            BlockPos floorTop = m.above().immutable();
            if (!level.getFluidState(floorTop).is(FluidTags.WATER)) {
                continue;
            }
            Hashing.Stream r = new Hashing.Stream(ct.hash);
            if (ct.loot) {
                if (loot == null) {
                    ResourceLocation id = ResourceLocation.tryParse(settings.getString(OceanSettings.GROTTO_LOOT_TABLE));
                    if (id == null) {
                        BarotraumaOceans.LOGGER.warn("Invalid grotto loot table '{}'", settings.getString(OceanSettings.GROTTO_LOOT_TABLE));
                        id = ResourceLocation.withDefaultNamespace("chests/shipwreck_supply");
                    }
                    loot = ResourceKey.create(Registries.LOOT_TABLE, id);
                }
                Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom());
                BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.WATERLOGGED, true);
                level.setBlock(floorTop, chest, 2);
                if (level.getBlockEntity(floorTop) instanceof RandomizableContainerBlockEntity be) {
                    be.setLootTable(loot);
                    be.setLootTableSeed(r.nextLong());
                }
            }
            // A few sea pickles so the grotto glows a little.
            int pickles = r.rangeInt(1, 3);
            for (int i = 0; i < pickles; i++) {
                int px = floorTop.getX() + r.rangeInt(-2, 2);
                int pz = floorTop.getZ() + r.rangeInt(-2, 2);
                if (px < x0 || px > x0 + 15 || pz < z0 || pz > z0 + 15) {
                    continue;
                }
                BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(px, floorTop.getY() + 2, pz);
                for (int k = 0; k < 6 && level.getFluidState(p.below()).is(FluidTags.WATER); k++) {
                    p.move(Direction.DOWN);
                }
                BlockState here = level.getBlockState(p);
                BlockState below = level.getBlockState(p.below());
                if (here.is(Blocks.WATER) && below.isFaceSturdy(level, p.below(), Direction.UP)) {
                    level.setBlock(p, Blocks.SEA_PICKLE.defaultBlockState()
                            .setValue(SeaPickleBlock.PICKLES, r.rangeInt(1, 4))
                            .setValue(SeaPickleBlock.WATERLOGGED, true), 2);
                }
            }
        }
    }
}
