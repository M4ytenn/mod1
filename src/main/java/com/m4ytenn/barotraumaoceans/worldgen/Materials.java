package com.m4ytenn.barotraumaoceans.worldgen;

import com.m4ytenn.barotraumaoceans.worldgen.core.Hashing;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanSettings;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.m4ytenn.barotraumaoceans.worldgen.core.SimplexNoise;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chooses the block of every solid position while the terrain is filled: ice crust (packed ice, blue ice
 * veins, snow on top), bedrock floor, deepslate below Y 0 and wavy rock strata. Doing this during the fill
 * avoids a second pass over the whole 832-block-tall column.
 */
final class Materials {
    static final BlockState STONE = Blocks.STONE.defaultBlockState();
    static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();
    static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();

    private static final BlockState[] UPPER_STRATA = {
            Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.DIORITE.defaultBlockState(),
            Blocks.GRANITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.DRIPSTONE_BLOCK.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState()
    };
    private static final BlockState[] LOWER_STRATA = {
            Blocks.TUFF.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.DRIPSTONE_BLOCK.defaultBlockState(),
            Blocks.BLACKSTONE.defaultBlockState(), Blocks.TUFF.defaultBlockState()
    };

    private final OceanTerrain terrain;
    private final SimplexNoise noise;
    private final boolean ice;
    private final boolean strata;
    private final boolean blueVeins;
    private final int thickness;
    private final int minY;
    private final long seed;
    private final long strataSeed;
    private final double[] iceBottom = new double[256];
    private final double[] iceTop = new double[256];
    private final double[] warp = new double[256];
    private int x0;
    private int z0;

    Materials(OceanTerrain terrain, OceanSettings settings, int minY) {
        this.terrain = terrain;
        this.noise = terrain.materialNoise;
        this.ice = terrain.ice;
        this.strata = settings.getBool(OceanSettings.ROCK_STRATA);
        this.blueVeins = settings.getBool(OceanSettings.BLUE_ICE_VEINS);
        this.thickness = Math.max(2, settings.getInt(OceanSettings.STRATA_THICKNESS));
        this.minY = minY;
        this.seed = terrain.seed;
        this.strataSeed = Hashing.hash(terrain.seed, 0x57A7A);
    }

    void prepare(int x0, int z0) {
        this.x0 = x0;
        this.z0 = z0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lx + lz * 16;
                int wx = x0 + lx;
                int wz = z0 + lz;
                if (ice) {
                    iceBottom[i] = terrain.seabed.iceBottom(wx, wz) - 3;
                    iceTop[i] = terrain.seabed.iceTop(wx, wz);
                }
                warp[i] = 7.0 * noise.noise2(wx / 90.0, wz / 90.0) + 3.0 * noise.noise2(wx / 23.0 + 50, wz / 23.0);
            }
        }
    }

    BlockState solid(int lx, int y, int lz) {
        int i = lx + lz * 16;
        if (ice && y >= iceBottom[i]) {
            if (y >= iceTop[i] - 2.5) {
                return SNOW_BLOCK;
            }
            if (blueVeins && noise.noise3((x0 + lx) / 21.0, y / 9.0, (z0 + lz) / 21.0) > 0.62) {
                return BLUE_ICE;
            }
            return PACKED_ICE;
        }
        if (y < minY + 5) {
            if (y == minY || Hashing.unit(Hashing.hash(seed, x0 + lx, y, z0 + lz)) < (minY + 5 - y) / 5.0) {
                return BEDROCK;
            }
        }
        boolean deep = y < 0 || (y < 8 && Hashing.unit(Hashing.hash(seed, z0 + lz, y, x0 + lx)) < (8 - y) / 8.0);
        if (strata) {
            int band = Math.floorDiv((int) Math.floor(y + warp[i]), thickness);
            long h = Hashing.hash(strataSeed, band);
            if (Hashing.unit(h) >= 0.5) {
                BlockState[] table = y >= 0 ? UPPER_STRATA : LOWER_STRATA;
                return table[(int) ((h >>> 3) % table.length)];
            }
        }
        return deep ? DEEPSLATE : STONE;
    }

    double iceBottom(int lx, int lz) {
        return ice ? iceBottom[lx + lz * 16] : Double.MAX_VALUE;
    }
}
