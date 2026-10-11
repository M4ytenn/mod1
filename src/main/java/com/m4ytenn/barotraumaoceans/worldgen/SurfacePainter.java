package com.m4ytenn.barotraumaoceans.worldgen;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
import com.m4ytenn.barotraumaoceans.worldgen.core.Hashing;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanSettings;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.m4ytenn.barotraumaoceans.worldgen.core.SimplexNoise;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paints materials after the terrain and the JSON surface rules ran:
 * <ul>
 *     <li>the ice crust (packed ice, blue ice veins, snow on top),</li>
 *     <li>sediment on every floor that faces water - seabed, channel floors, cavern floors - per biome,</li>
 *     <li>optional ceiling blocks,</li>
 *     <li>wavy rock strata through the crust.</li>
 * </ul>
 * Vanilla surface rules only detect floors below air, which never happens under water, so this is done here.
 */
public final class SurfacePainter {

    /** Floor/ceiling materials of a biome. {@code null} entries mean "keep the rock". */
    record Palette(BlockState top, BlockState alt1, BlockState alt2, BlockState sub, BlockState ceiling) {
    }

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();

    private static final Palette DEFAULT = p(Blocks.SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.SAND, null);
    private static final Map<ResourceLocation, Palette> PALETTES = new HashMap<>();
    private static final Map<Holder<Biome>, Palette> CACHE = new ConcurrentHashMap<>();

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

    static {
        put("abyssal_plains", p(Blocks.SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.SANDSTONE, null));
        put("spire_fields", p(Blocks.GRAVEL, Blocks.TUFF, Blocks.ANDESITE, null, null));
        put("pillar_forest", p(Blocks.GRAVEL, Blocks.MUD, Blocks.SAND, Blocks.GRAVEL, null));
        put("monolith_reach", p(Blocks.SAND, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.SANDSTONE, null));
        put("kelp_forest", p(Blocks.SAND, Blocks.GRAVEL, Blocks.MUD, Blocks.SAND, null));
        put("coral_gardens", p(Blocks.SAND, Blocks.SAND, Blocks.CALCITE, Blocks.SANDSTONE, null));
        put("europan_ridge", p(Blocks.GRAVEL, null, Blocks.ANDESITE, null, null));
        put("hydrothermal_wastes", p(Blocks.BASALT, Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK, Blocks.BASALT, Blocks.BLACKSTONE));
        put("hadal_trench", p(Blocks.MUD, Blocks.CLAY, Blocks.GRAVEL, Blocks.MUD, null));
        put("the_great_sea", p(Blocks.GRAVEL, null, Blocks.SAND, null, null));
        put("abyssal_ravine", p(Blocks.GRAVEL, Blocks.MUD, Blocks.TUFF, null, Blocks.TUFF));
        put("great_channels", p(Blocks.GRAVEL, Blocks.SAND, Blocks.CLAY, Blocks.GRAVEL, null));
        put("sunken_channels", p(Blocks.GRAVEL, Blocks.SAND, Blocks.MUD, Blocks.GRAVEL, null));
        put("narrow_passages", p(Blocks.GRAVEL, Blocks.TUFF, null, null, Blocks.TUFF));
        put("luminous_caverns", p(Blocks.CLAY, Blocks.MOSS_BLOCK, Blocks.GRAVEL, Blocks.CLAY, Blocks.CALCITE));
        put("cold_caverns", p(Blocks.PACKED_ICE, Blocks.GRAVEL, Blocks.BLUE_ICE, Blocks.PACKED_ICE, Blocks.PACKED_ICE));
        put("station_hollow", p(Blocks.GRAVEL, Blocks.SAND, Blocks.GRAVEL, Blocks.GRAVEL, null));
        put("deep_crust", p(Blocks.GRAVEL, Blocks.TUFF, null, null, null));
    }

    private static Palette p(Block top, Block alt1, Block alt2, Block sub, Block ceiling) {
        return new Palette(state(top), state(alt1), state(alt2), state(sub), state(ceiling));
    }

    private static BlockState state(Block b) {
        return b == null ? null : b.defaultBlockState();
    }

    private static void put(String name, Palette palette) {
        PALETTES.put(BarotraumaOceans.id(name), palette);
    }

    private static Palette palette(Holder<Biome> biome) {
        return CACHE.computeIfAbsent(biome, h -> {
            Optional<ResourceKey<Biome>> key = h.unwrapKey();
            return key.map(k -> PALETTES.getOrDefault(k.location(), DEFAULT)).orElse(DEFAULT);
        });
    }

    private static boolean isRock(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE);
    }

    private SurfacePainter() {
    }

    public static void paint(OceanTerrain terrain, OceanSettings settings, ChunkAccess chunk) {
        int sediment = settings.getInt(OceanSettings.SEDIMENT_DEPTH);
        boolean strata = settings.getBool(OceanSettings.ROCK_STRATA);
        int thickness = Math.max(2, settings.getInt(OceanSettings.STRATA_THICKNESS));
        boolean blueVeins = settings.getBool(OceanSettings.BLUE_ICE_VEINS);
        SimplexNoise noise = terrain.materialNoise;
        long strataSeed = Hashing.hash(terrain.seed, 0x57A7A);

        ChunkPos pos = chunk.getPos();
        int x0 = pos.getMinBlockX();
        int z0 = pos.getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int waterLevel = terrain.waterLevel;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = x0 + lx;
                int wz = z0 + lz;
                int top = Math.min(chunk.getMaxBuildHeight() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz));
                double iceBottom = terrain.ice ? terrain.seabed.iceBottom(wx, wz) - 3 : Double.MAX_VALUE;
                double warp = 7.0 * noise.noise2(wx / 90.0, wz / 90.0) + 3.0 * noise.noise2(wx / 23.0 + 50, wz / 23.0);
                double mixA = noise.noise2(wx / 11.0 + 300, wz / 11.0);
                double mixB = noise.noise2(wx / 17.0 - 300, wz / 17.0 + 100);

                int exposure = 0; // 0 = rock above, 1 = water above, 2 = air above
                int depth = 0;
                int lastRockY = Integer.MIN_VALUE;
                int cachedBand = Integer.MIN_VALUE;
                BlockState cachedStrata = null;
                int cachedQuart = Integer.MIN_VALUE;
                Palette pal = DEFAULT;
                LevelChunkSection section = null;
                int sectionIdx = Integer.MIN_VALUE;

                for (int y = top; y >= minY; y--) {
                    int idx = chunk.getSectionIndex(y);
                    if (idx != sectionIdx) {
                        sectionIdx = idx;
                        section = chunk.getSection(idx);
                    }
                    if (section.hasOnlyAir()) {
                        // whole air section: skip to its bottom
                        exposure = 2;
                        depth = 0;
                        lastRockY = Integer.MIN_VALUE;
                        y = (y & ~15);
                        continue;
                    }
                    BlockState s = section.getBlockState(lx, y & 15, lz);
                    if (s.isAir()) {
                        exposure = 2;
                        depth = 0;
                        lastRockY = Integer.MIN_VALUE;
                        continue;
                    }
                    if (!s.getFluidState().isEmpty()) {
                        if (lastRockY == y + 1 && y + 1 < iceBottom) {
                            Palette cp = paletteAt(chunk, wx, y + 1, wz);
                            if (cp.ceiling() != null) {
                                setAt(chunk, lx, y + 1, lz, cp.ceiling());
                            }
                        }
                        exposure = 1;
                        depth = 0;
                        lastRockY = Integer.MIN_VALUE;
                        continue;
                    }
                    if (s.is(Blocks.BEDROCK)) {
                        exposure = 0;
                        lastRockY = Integer.MIN_VALUE;
                        continue;
                    }
                    depth++;
                    if (y >= iceBottom) {
                        BlockState ice;
                        if (exposure == 2 && depth <= 2 && y >= waterLevel) {
                            ice = SNOW_BLOCK;
                        } else if (blueVeins && noise.noise3(wx / 21.0, y / 9.0, wz / 21.0) > 0.62) {
                            ice = BLUE_ICE;
                        } else {
                            ice = PACKED_ICE;
                        }
                        section.setBlockState(lx, y & 15, lz, ice, false);
                        lastRockY = Integer.MIN_VALUE;
                        continue;
                    }
                    boolean rock = isRock(s);
                    if (exposure != 0 && depth <= sediment && rock) {
                        int quart = QuartPos.fromBlock(y);
                        if (quart != cachedQuart) {
                            cachedQuart = quart;
                            pal = palette(chunk.getNoiseBiome(QuartPos.fromBlock(wx), quart, QuartPos.fromBlock(wz)));
                        }
                        BlockState floor;
                        if (depth == 1) {
                            floor = mixA > 0.35 ? pal.alt1() : (mixB < -0.45 ? pal.alt2() : pal.top());
                        } else {
                            floor = pal.sub();
                        }
                        if (floor != null) {
                            section.setBlockState(lx, y & 15, lz, floor, false);
                            lastRockY = Integer.MIN_VALUE;
                            continue;
                        }
                    }
                    if (rock) {
                        lastRockY = y;
                        if (strata) {
                            int band = Math.floorDiv((int) Math.floor(y + warp), thickness);
                            if (band != cachedBand) {
                                cachedBand = band;
                                long h = Hashing.hash(strataSeed, band);
                                double roll = Hashing.unit(h);
                                if (roll < 0.5) {
                                    cachedStrata = null;
                                } else {
                                    BlockState[] table = y >= 0 ? UPPER_STRATA : LOWER_STRATA;
                                    cachedStrata = table[(int) ((h >>> 3) % table.length)];
                                }
                            }
                            if (cachedStrata != null) {
                                section.setBlockState(lx, y & 15, lz, cachedStrata, false);
                            }
                        }
                    } else {
                        lastRockY = Integer.MIN_VALUE;
                    }
                    exposure = 0;
                }
            }
        }
    }

    private static Palette paletteAt(ChunkAccess chunk, int wx, int y, int wz) {
        return palette(chunk.getNoiseBiome(QuartPos.fromBlock(wx), QuartPos.fromBlock(y), QuartPos.fromBlock(wz)));
    }

    private static void setAt(ChunkAccess chunk, int lx, int y, int lz, BlockState state) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(lx, y & 15, lz, state, false);
    }
}
