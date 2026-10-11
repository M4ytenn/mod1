package com.m4ytenn.barotraumaoceans.spawn;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
import com.m4ytenn.barotraumaoceans.config.BOConfig;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Builds the spawn point of a Barotrauma Oceans world: a small air pocket carved into the underside of the ice
 * crust with a moon pool down into the ocean, or a raft floating on the open surface.
 */
public final class SpawnStructure {
    private SpawnStructure() {
    }

    /** Builds the structure and returns the block position players should stand on. */
    public static BlockPos build(ServerLevel level, OceanTerrain terrain, BOConfig.SpawnMode mode, int radius) {
        boolean pocket = terrain.ice && mode != BOConfig.SpawnMode.VANILLA;
        int cx = 8;
        int cz = 8;
        // make sure the area is generated
        for (int x = cx - radius - 2; x <= cx + radius + 2; x += 16) {
            for (int z = cz - radius - 2; z <= cz + radius + 2; z += 16) {
                level.getChunk(x >> 4, z >> 4);
            }
        }
        level.getChunk((cx + radius + 2) >> 4, (cz + radius + 2) >> 4);
        level.getChunk((cx - radius - 2) >> 4, (cz - radius - 2) >> 4);
        BlockPos spawn = pocket ? buildPocket(level, terrain, cx, cz, radius) : buildRaft(level, terrain, cx, cz, radius);
        BarotraumaOceans.LOGGER.info("Barotrauma Oceans: built {} spawn at {}", pocket ? "ice pocket" : "raft", spawn);
        return spawn;
    }

    private static BlockPos buildPocket(ServerLevel level, OceanTerrain terrain, int cx, int cz, int r) {
        // The floor must sit above the underside of the ice everywhere in the footprint.
        double highestUnderside = -Double.MAX_VALUE;
        double lowestTop = Double.MAX_VALUE;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                highestUnderside = Math.max(highestUnderside, terrain.seabed.iceBottom(cx + dx, cz + dz));
                lowestTop = Math.min(lowestTop, terrain.seabed.iceTop(cx + dx, cz + dz));
            }
        }
        int floorY = (int) Math.ceil(highestUnderside) + 3;
        int height = 5;
        if (floorY + height + 2 > lowestTop) {
            floorY = (int) Math.floor(lowestTop) - height - 3;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState wall = Blocks.PACKED_ICE.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();

        // Shell and room
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                for (int dy = -1; dy <= height; dy++) {
                    BlockPos p = new BlockPos(cx + dx, floorY + dy, cz + dz);
                    boolean edge = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1 || dy == height;
                    if (dy == -1) {
                        level.setBlock(p, floor, 3);
                    } else if (edge) {
                        level.setBlock(p, (dy == height && (dx + dz) % 3 == 0 && Math.abs(dx) <= r && Math.abs(dz) <= r) ? light : wall, 3);
                    } else {
                        level.setBlock(p, air, 3);
                    }
                }
            }
        }
        // Moon pool: a 3x3 shaft from the floor down into the ocean, filled with water up to floor level.
        int bottom = floorY - 1;
        for (int y = floorY - 1; y > floorY - 120; y--) {
            boolean waterBelow = level.getFluidState(new BlockPos(cx, y - 1, cz)).isSource();
            bottom = y;
            if (waterBelow) {
                break;
            }
        }
        for (int y = bottom; y <= floorY - 1; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    level.setBlock(p, rim ? (y == floorY - 1 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : wall) : water, 3);
                }
            }
        }
        BlockPos spawn = new BlockPos(cx + Math.min(3, r - 1), floorY, cz);
        placeChest(level, new BlockPos(cx - r, floorY, cz - r + 1));
        level.setBlock(new BlockPos(cx + r, floorY, cz + r), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 3);
        level.setBlock(new BlockPos(cx - r, floorY, cz + r), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 3);
        return spawn;
    }

    private static BlockPos buildRaft(ServerLevel level, OceanTerrain terrain, int cx, int cz, int r) {
        int y = terrain.waterLevel - 1;
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + r) {
                    continue;
                }
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), planks, 3);
                for (int up = 1; up <= 3; up++) {
                    BlockPos p = new BlockPos(cx + dx, y + up, cz + dz);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        level.setBlock(new BlockPos(cx + r - 1, y + 1, cz), Blocks.LANTERN.defaultBlockState(), 3);
        placeChest(level, new BlockPos(cx - r + 1, y + 1, cz));
        return new BlockPos(cx, y + 1, cz);
    }

    private static void placeChest(ServerLevel level, BlockPos pos) {
        List<? extends String> items = BOConfig.STARTER_ITEMS.get();
        if (items.isEmpty()) {
            return;
        }
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), 3);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return;
        }
        int slot = 0;
        for (String entry : items) {
            if (slot >= chest.getContainerSize()) {
                break;
            }
            String id = entry;
            int count = 1;
            int star = entry.lastIndexOf('*');
            if (star > 0) {
                id = entry.substring(0, star).trim();
                try {
                    count = Integer.parseInt(entry.substring(star + 1).trim());
                } catch (NumberFormatException ignored) {
                    count = 1;
                }
            }
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                BarotraumaOceans.LOGGER.warn("Barotrauma Oceans: unknown starter item '{}'", id);
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            while (count > 0 && slot < chest.getContainerSize()) {
                int n = Math.min(count, item.getDefaultMaxStackSize());
                chest.setItem(slot++, new ItemStack(item, n));
                count -= n;
            }
        }
    }
}
