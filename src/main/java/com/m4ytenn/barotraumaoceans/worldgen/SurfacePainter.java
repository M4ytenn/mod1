package com.m4ytenn.barotraumaoceans.worldgen;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puts sediment on every floor that faces water or air - seabed, channel and cavern floors - and optional
 * ceiling blocks, per biome. Vanilla surface rules only detect floors below air, which never happens under
 * water, so this is done here. Sections that are completely solid or completely open are skipped.
 */
public final class SurfacePainter {

    /**
     * Floor/ceiling materials of a biome. {@code null} entries mean "keep the rock". Biomes of other mods use
     * {@link #DEFAULT} under water and keep their own floors in dry caves (their surface rules handle those).
     */
    record Palette(BlockState top, BlockState alt1, BlockState alt2, BlockState sub, BlockState ceiling, boolean foreign) {
    }

    private static final Palette DEFAULT = new Palette(Blocks.SAND.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
            Blocks.CLAY.defaultBlockState(), Blocks.SAND.defaultBlockState(), null, true);
    private static final Map<ResourceLocation, Palette> PALETTES = new HashMap<>();
    private static final Map<Holder<Biome>, Palette> CACHE = new ConcurrentHashMap<>();

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
        return new Palette(state(top), state(alt1), state(alt2), state(sub), state(ceiling), false);
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

    /** Blocks that may be covered with sediment / replaced by ceiling material. */
    private static boolean isRock(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && !s.is(Blocks.PACKED_ICE) && !s.is(Blocks.BLUE_ICE)
                && !s.is(Blocks.SNOW_BLOCK) && !s.is(Blocks.BEDROCK) && s.isSolid();
    }

    private static boolean isOpen(BlockState s) {
        return s.isAir() || !s.getFluidState().isEmpty();
    }

    private SurfacePainter() {
    }

    public static void paint(OceanTerrain terrain, OceanSettings settings, ChunkAccess chunk) {
        int sediment = settings.getInt(OceanSettings.SEDIMENT_DEPTH);
        SimplexNoise noise = terrain.materialNoise;
        ChunkPos pos = chunk.getPos();
        int x0 = pos.getMinBlockX();
        int z0 = pos.getMinBlockZ();

        // Per column state while walking down: 0 = rock above, 1 = water above, 2 = air above
        int[] exposure = new int[256];
        int[] depth = new int[256];
        boolean[] lastWasRock = new boolean[256];
        Arrays.fill(exposure, 2);
        double[] mixA = new double[256];
        double[] mixB = new double[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                mixA[lx + lz * 16] = noise.noise2((x0 + lx) / 11.0 + 300, (z0 + lz) / 11.0);
                mixB[lx + lz * 16] = noise.noise2((x0 + lx) / 17.0 - 300, (z0 + lz) / 17.0 + 100);
            }
        }

        LevelChunkSection[] sections = chunk.getSections();
        int minSection = chunk.getMinSection();
        for (int si = sections.length - 1; si >= 0; si--) {
            LevelChunkSection section = sections[si];
            int baseY = (minSection + si) << 4;
            if (section.hasOnlyAir()) {
                Arrays.fill(exposure, 2);
                Arrays.fill(depth, 0);
                Arrays.fill(lastWasRock, false);
                continue;
            }
            boolean hasOpen = section.maybeHas(SurfacePainter::isOpen);
            boolean hasSolid = section.maybeHas(s -> !isOpen(s));
            if (!hasSolid) {
                // Completely open section: what matters below is the bottom layer of each column.
                for (int i = 0; i < 256; i++) {
                    BlockState bottom = section.getBlockState(i & 15, 0, i >> 4);
                    if (lastWasRock[i]) {
                        ceiling(chunk, terrain, x0 + (i & 15), baseY + 16, z0 + (i >> 4));
                    }
                    exposure[i] = bottom.isAir() ? 2 : 1;
                    depth[i] = 0;
                    lastWasRock[i] = false;
                }
                continue;
            }
            if (!hasOpen) {
                // Completely solid section: only the first few layers below an exposed surface get sediment.
                boolean needed = false;
                for (int i = 0; i < 256 && !needed; i++) {
                    needed = exposure[i] != 0 && depth[i] < sediment;
                }
                if (!needed) {
                    Arrays.fill(depth, 16);
                    Arrays.fill(exposure, 0);
                    Arrays.fill(lastWasRock, false);
                    continue;
                }
            }
            int cachedQuartY = Integer.MIN_VALUE;
            Palette[] pal = new Palette[256];
            for (int ly = 15; ly >= 0; ly--) {
                int y = baseY + ly;
                int qy = QuartPos.fromBlock(y);
                if (qy != cachedQuartY) {
                    cachedQuartY = qy;
                    Arrays.fill(pal, null);
                }
                for (int i = 0; i < 256; i++) {
                    int lx = i & 15;
                    int lz = i >> 4;
                    BlockState s = section.getBlockState(lx, ly, lz);
                    if (isOpen(s)) {
                        if (lastWasRock[i]) {
                            ceiling(chunk, terrain, x0 + lx, y + 1, z0 + lz);
                        }
                        exposure[i] = s.isAir() ? 2 : 1;
                        depth[i] = 0;
                        lastWasRock[i] = false;
                        continue;
                    }
                    depth[i]++;
                    boolean rock = isRock(s);
                    if (rock && exposure[i] != 0 && depth[i] <= sediment) {
                        Palette p = pal[i];
                        if (p == null) {
                            p = palette(chunk.getNoiseBiome(QuartPos.fromBlock(x0 + lx), qy, QuartPos.fromBlock(z0 + lz)));
                            pal[i] = p;
                        }
                        if (p.foreign() && exposure[i] == 2) {
                            exposure[i] = 0;
                            lastWasRock[i] = false;
                            continue;
                        }
                        BlockState floor = depth[i] == 1
                                ? (mixA[i] > 0.35 ? p.alt1() : (mixB[i] < -0.45 ? p.alt2() : p.top()))
                                : p.sub();
                        if (floor != null) {
                            section.setBlockState(lx, ly, lz, floor, false);
                        }
                        lastWasRock[i] = false;
                        continue;
                    }
                    if (depth[i] > sediment) {
                        exposure[i] = 0;
                    }
                    lastWasRock[i] = rock && depth[i] == 1;
                }
            }
        }
    }

    /** Replaces the rock block at (x, y, z) - the lowest block above an open space - with the ceiling material. */
    private static void ceiling(ChunkAccess chunk, OceanTerrain terrain, int x, int y, int z) {
        if (y >= chunk.getMaxBuildHeight()) {
            return;
        }
        if (terrain.ice && y >= terrain.seabed.iceBottom(x, z) - 3) {
            return;
        }
        Palette p = palette(chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)));
        if (p.ceiling() == null || p.foreign()) {
            return;
        }
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        BlockState cur = section.getBlockState(x & 15, y & 15, z & 15);
        if (isRock(cur)) {
            section.setBlockState(x & 15, y & 15, z & 15, p.ceiling(), false);
        }
    }
}
