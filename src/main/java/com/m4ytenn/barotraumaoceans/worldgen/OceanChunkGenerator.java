package com.m4ytenn.barotraumaoceans.worldgen;

import com.google.common.base.Suppliers;
import com.m4ytenn.barotraumaoceans.config.BOConfig;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanSettings;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.m4ytenn.barotraumaoceans.worldgen.core.TunnelNetwork;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The Barotrauma Oceans chunk generator. It keeps the vanilla noise-based pipeline (surface rules, structures,
 * features, mob spawning), but fills the terrain itself from {@link OceanTerrain}: rock, water everywhere below
 * the water level, the ice crust and the station network.
 */
public class OceanChunkGenerator extends NoiseBasedChunkGenerator {

    public static final MapCodec<OceanChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings)
    ).apply(instance, instance.stable(OceanChunkGenerator::new)));

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    /** Cumulative generation timings (nanoseconds / calls), reported by the smoke test and /barotrauma info. */
    public static final java.util.concurrent.atomic.AtomicLong FILL_NANOS = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong FILL_CALLS = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong SURFACE_NANOS = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong DECORATION_NANOS = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong BIOME_NANOS = new java.util.concurrent.atomic.AtomicLong();

    private final OceanSettings oceanSettings;
    private final int waterLevel;
    private volatile OceanTerrain lastTerrain;

    public OceanChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.oceanSettings = biomeSource instanceof OceanBiomeSource obs ? obs.oceanSettings() : BOConfig.worldgenSnapshot();
        this.waterLevel = oceanSettings.getInt(OceanSettings.WATER_LEVEL);
        // Vanilla turns every cave below y=-54 into lava. Our ocean is water all the way down.
        final Aquifer.FluidStatus water = new Aquifer.FluidStatus(waterLevel, WATER);
        final Aquifer.FluidPicker picker = (x, y, z) -> water;
        this.globalFluidPicker = Suppliers.memoize(() -> picker);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public OceanSettings oceanSettings() {
        return oceanSettings;
    }

    public OceanTerrain terrain(RandomState randomState) {
        long seed = TerrainAccess.seedOf(randomState);
        OceanTerrain t = lastTerrain;
        if (t == null || t.seed != seed) {
            t = OceanTerrain.get(seed, oceanSettings);
            lastTerrain = t;
        }
        return t;
    }

    private int genMinY() {
        return generatorSettings().value().noiseSettings().minY();
    }

    private int genMaxY() {
        NoiseSettings ns = generatorSettings().value().noiseSettings();
        return ns.minY() + ns.height();
    }

    @Override
    public int getSeaLevel() {
        return waterLevel;
    }

    // ------------------------------------------------------------------------------------------------
    // Biomes
    // ------------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        long t0 = System.nanoTime();
        chunk.fillBiomesFromNoise(this.biomeSource, randomState.sampler());
        BIOME_NANOS.addAndGet(System.nanoTime() - t0);
        return CompletableFuture.completedFuture(chunk);
    }

    // ------------------------------------------------------------------------------------------------
    // Terrain fill
    // ------------------------------------------------------------------------------------------------

    /** Density at the 4x4x4 cell corners of a column, bottom to top, with the bedrock floor applied. */
    private double[] cornerColumn(OceanTerrain t, int x, int z, int minY, int levels, Beardifier beard) {
        OceanTerrain.Column c = t.column(x, z);
        double[] out = new double[levels];
        for (int k = 0; k < levels; k++) {
            int y = minY + k * 4;
            double d = t.density(c, y);
            double floor = (minY + 3) - y;
            if (floor > d) {
                d = floor;
            }
            if (beard != null) {
                d += 12.0 * beard.compute(new DensityFunction.SinglePointContext(x, y, z));
            }
            out[k] = d;
        }
        return out;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        long started = System.nanoTime();
        OceanTerrain terrain = terrain(randomState);
        int minY = Math.max(genMinY(), chunk.getMinBuildHeight());
        int maxY = Math.min(genMaxY(), chunk.getMaxBuildHeight());
        minY = Math.floorDiv(minY, 4) * 4;
        int cellsY = (maxY - minY) / 4;
        ChunkPos pos = chunk.getPos();
        int x0 = pos.getMinBlockX();
        int z0 = pos.getMinBlockZ();
        Beardifier beard = Beardifier.forStructuresInChunk(structureManager, pos);
        BlockState stone = generatorSettings().value().defaultBlock();
        BlockState fluid = generatorSettings().value().defaultFluid();

        double[][] corners = new double[25][];
        for (int i = 0; i <= 4; i++) {
            for (int j = 0; j <= 4; j++) {
                corners[i * 5 + j] = cornerColumn(terrain, x0 + i * 4, z0 + j * 4, minY, cellsY + 1, beard);
            }
        }

        Set<LevelChunkSection> locked = new HashSet<>();
        for (int y = minY; y < maxY; y += 16) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            if (locked.add(section)) {
                section.acquire();
            }
        }
        try {
            for (int cx = 0; cx < 4; cx++) {
                for (int cz = 0; cz < 4; cz++) {
                    double[] c00 = corners[cx * 5 + cz];
                    double[] c01 = corners[cx * 5 + cz + 1];
                    double[] c10 = corners[(cx + 1) * 5 + cz];
                    double[] c11 = corners[(cx + 1) * 5 + cz + 1];
                    for (int cy = 0; cy < cellsY; cy++) {
                        double a000 = c00[cy], a001 = c01[cy], a100 = c10[cy], a101 = c11[cy];
                        double a010 = c00[cy + 1], a011 = c01[cy + 1], a110 = c10[cy + 1], a111 = c11[cy + 1];
                        int baseY = minY + cy * 4;
                        boolean allSolid = a000 > 0 && a001 > 0 && a100 > 0 && a101 > 0 && a010 > 0 && a011 > 0 && a110 > 0 && a111 > 0;
                        boolean allOpen = a000 <= 0 && a001 <= 0 && a100 <= 0 && a101 <= 0 && a010 <= 0 && a011 <= 0 && a110 <= 0 && a111 <= 0;
                        for (int ly = 0; ly < 4; ly++) {
                            int y = baseY + ly;
                            if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) {
                                continue;
                            }
                            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                            BlockState open = y < waterLevel ? fluid : AIR;
                            double fy = ly / 4.0;
                            for (int lx = 0; lx < 4; lx++) {
                                double fx = lx / 4.0;
                                for (int lz = 0; lz < 4; lz++) {
                                    BlockState state;
                                    if (allSolid) {
                                        state = stone;
                                    } else if (allOpen) {
                                        state = open;
                                    } else {
                                        double fz = lz / 4.0;
                                        double d00 = a000 + (a010 - a000) * fy;
                                        double d01 = a001 + (a011 - a001) * fy;
                                        double d10 = a100 + (a110 - a100) * fy;
                                        double d11 = a101 + (a111 - a101) * fy;
                                        double d0 = d00 + (d01 - d00) * fz;
                                        double d1 = d10 + (d11 - d10) * fz;
                                        double d = d0 + (d1 - d0) * fx;
                                        state = d > 0 ? stone : open;
                                    }
                                    if (state != AIR) {
                                        section.setBlockState(cx * 4 + lx, y & 15, cz * 4 + lz, state, false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            carveCrewTunnels(terrain, chunk, minY, maxY);
        } finally {
            for (LevelChunkSection section : locked) {
                section.release();
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG));
        setSeabedHeightmap(chunk, Heightmap.Types.OCEAN_FLOOR_WG);
        FILL_NANOS.addAndGet(System.nanoTime() - started);
        FILL_CALLS.incrementAndGet();
        return CompletableFuture.completedFuture(chunk);
    }

    private void carveCrewTunnels(OceanTerrain terrain, ChunkAccess chunk, int minY, int maxY) {
        ChunkPos pos = chunk.getPos();
        int x0 = pos.getMinBlockX();
        int z0 = pos.getMinBlockZ();
        List<TunnelNetwork.CrewTunnel> list = new ArrayList<>();
        terrain.crewTunnelsIn(x0, z0, x0 + 15, z0 + 15, list);
        for (TunnelNetwork.CrewTunnel ct : list) {
            int ax = Math.max(x0, (int) Math.floor(ct.minX));
            int bx = Math.min(x0 + 15, (int) Math.ceil(ct.maxX));
            int az = Math.max(z0, (int) Math.floor(ct.minZ));
            int bz = Math.min(z0 + 15, (int) Math.ceil(ct.maxZ));
            int ay = Math.max(minY + 4, (int) Math.floor(ct.minY));
            int by = Math.min(maxY - 1, (int) Math.ceil(ct.maxY));
            for (int y = ay; y <= by; y++) {
                LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                BlockState open = y < waterLevel ? WATER : AIR;
                for (int x = ax; x <= bx; x++) {
                    for (int z = az; z <= bz; z++) {
                        if (terrain.crewSdf(ct, x + 0.5, y + 0.5, z + 0.5) < 0) {
                            BlockState cur = section.getBlockState(x & 15, y & 15, z & 15);
                            if (!cur.isAir() && cur.getFluidState().isEmpty()) {
                                section.setBlockState(x & 15, y & 15, z & 15, open, false);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Sets a heightmap to the real seabed: the first motion-blocking block below the first water block,
     * scanning from the top. Without the ice crust this equals the vanilla definition; with it, vegetation,
     * shipwrecks and other ocean-floor decorations end up on the seabed instead of on top of the ice.
     */
    static void setSeabedHeightmap(ChunkAccess chunk, Heightmap.Types type) {
        int minY = chunk.getMinBuildHeight();
        int height = chunk.getHeight();
        SimpleBitStorage storage = new SimpleBitStorage(Mth.ceillog2(height + 1), 256);
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        int top = chunk.getMaxBuildHeight() - 1;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int start = Math.min(top, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) + 1);
                boolean seenWater = false;
                int floor = minY - 1;
                int firstSolid = minY - 1;
                for (int y = start; y >= minY; y--) {
                    mpos.set(x, y, z);
                    BlockState s = chunk.getBlockState(mpos);
                    if (!seenWater && s.getFluidState().is(FluidTags.WATER)) {
                        seenWater = true;
                        continue;
                    }
                    if (type.isOpaque().test(s)) {
                        if (firstSolid < minY) {
                            firstSolid = y;
                        }
                        if (seenWater) {
                            floor = y;
                            break;
                        }
                    }
                }
                int value = floor >= minY ? floor : firstSolid;
                storage.set(x + z * 16, Math.max(0, value + 1 - minY));
            }
        }
        chunk.setHeightmap(type, storage.getRaw());
    }

    // ------------------------------------------------------------------------------------------------
    // Columns for structures (must match fillFromNoise)
    // ------------------------------------------------------------------------------------------------

    private BlockState[] columnStates(int x, int z, int minY, int height, RandomState randomState) {
        OceanTerrain terrain = terrain(randomState);
        int gMin = Math.floorDiv(Math.max(genMinY(), minY), 4) * 4;
        int gMax = Math.min(genMaxY(), minY + height);
        int levels = (gMax - gMin) / 4 + 1;
        int bx = Math.floorDiv(x, 4) * 4;
        int bz = Math.floorDiv(z, 4) * 4;
        double fx = (x - bx) / 4.0;
        double fz = (z - bz) / 4.0;
        double[] c00 = cornerColumn(terrain, bx, bz, gMin, levels, null);
        double[] c01 = cornerColumn(terrain, bx, bz + 4, gMin, levels, null);
        double[] c10 = cornerColumn(terrain, bx + 4, bz, gMin, levels, null);
        double[] c11 = cornerColumn(terrain, bx + 4, bz + 4, gMin, levels, null);
        BlockState stone = generatorSettings().value().defaultBlock();
        BlockState fluid = generatorSettings().value().defaultFluid();
        BlockState[] states = new BlockState[height];
        for (int i = 0; i < height; i++) {
            int y = minY + i;
            if (y < gMin || y >= gMax) {
                states[i] = AIR;
                continue;
            }
            int k = (y - gMin) / 4;
            double fy = ((y - gMin) % 4) / 4.0;
            int k1 = Math.min(levels - 1, k + 1);
            double d0 = lerp2(fx, fz, c00[k], c10[k], c01[k], c11[k]);
            double d1 = lerp2(fx, fz, c00[k1], c10[k1], c01[k1], c11[k1]);
            double d = d0 + (d1 - d0) * fy;
            states[i] = d > 0 ? stone : (y < waterLevel ? fluid : AIR);
        }
        return states;
    }

    private static double lerp2(double fx, double fz, double v00, double v10, double v01, double v11) {
        double a = v00 + (v10 - v00) * fx;
        double b = v01 + (v11 - v01) * fx;
        return a + (b - a) * fz;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        int minY = level.getMinBuildHeight();
        BlockState[] states = columnStates(x, z, minY, level.getHeight(), randomState);
        boolean oceanFloor = type == Heightmap.Types.OCEAN_FLOOR_WG || type == Heightmap.Types.OCEAN_FLOOR;
        boolean seenWater = false;
        int firstMatch = minY;
        boolean haveFirst = false;
        for (int i = states.length - 1; i >= 0; i--) {
            BlockState s = states[i];
            if (!seenWater && s.getFluidState().is(FluidTags.WATER)) {
                seenWater = true;
                continue;
            }
            if (type.isOpaque().test(s)) {
                if (!haveFirst) {
                    firstMatch = minY + i + 1;
                    haveFirst = true;
                }
                if (!oceanFloor || seenWater) {
                    return minY + i + 1;
                }
            }
        }
        return firstMatch;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        return new NoiseColumn(level.getMinBuildHeight(), columnStates(x, z, level.getMinBuildHeight(), level.getHeight(), randomState));
    }

    // ------------------------------------------------------------------------------------------------
    // Surface, carvers, decoration
    // ------------------------------------------------------------------------------------------------

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        long t0 = System.nanoTime();
        super.buildSurface(level, structureManager, randomState, chunk);
        SurfacePainter.paint(terrain(randomState), oceanSettings, chunk);
        SURFACE_NANOS.addAndGet(System.nanoTime() - t0);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving step) {
        if (oceanSettings.getBool(OceanSettings.VANILLA_CARVERS)) {
            super.applyCarvers(level, seed, randomState, biomeManager, structureManager, chunk, step);
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        long t0 = System.nanoTime();
        OceanTerrain terrain = terrain(level.getLevel().getChunkSource().randomState());
        // The OCEAN_FLOOR heightmap was just primed from the blocks (= top of the ice). Point it at the seabed
        // for the decoration pass and re-prime it afterwards.
        boolean swap = terrain.ice;
        if (swap) {
            setSeabedHeightmap(chunk, Heightmap.Types.OCEAN_FLOOR);
        }
        try {
            super.applyBiomeDecoration(level, chunk, structureManager);
        } finally {
            if (swap) {
                Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR));
            }
        }
        NetworkDecorator.decorate(level, chunk, terrain, oceanSettings);
        DECORATION_NANOS.addAndGet(System.nanoTime() - t0);
    }

    /** Average milliseconds per chunk spent in each generation stage so far. */
    public static String timingReport() {
        long n = Math.max(1, FILL_CALLS.get());
        return String.format("%d chunks: biomes %.2f ms, terrain %.2f ms, surface %.2f ms, decoration %.2f ms (per chunk)",
                FILL_CALLS.get(), BIOME_NANOS.get() / 1e6 / n, FILL_NANOS.get() / 1e6 / n, SURFACE_NANOS.get() / 1e6 / n,
                DECORATION_NANOS.get() / 1e6 / n);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        OceanTerrain t = terrain(randomState);
        OceanTerrain.Column c = t.column(pos.getX() & ~3, pos.getZ() & ~3);
        OceanTerrain.SlotResult r = new OceanTerrain.SlotResult();
        t.slot(c, pos.getY(), r);
        info.add(String.format("Barotrauma Oceans: %s / %s, seabed %.0f", c.ps.dominant.name().toLowerCase(), r.slot.name().toLowerCase(), c.seabed));
        if (t.network.enabled()) {
            List<TunnelNetwork.Station> near = t.network.nearestStations(pos.getX(), pos.getZ(), 1, 1);
            if (!near.isEmpty()) {
                TunnelNetwork.Station st = near.get(0);
                info.add(String.format("Nearest station: %d %d %d (%.0f blocks)", (int) st.x, st.floorBlockY(), (int) st.z,
                        Math.sqrt((st.x - pos.getX()) * (st.x - pos.getX()) + (st.z - pos.getZ()) * (st.z - pos.getZ()))));
            }
        }
    }
}
