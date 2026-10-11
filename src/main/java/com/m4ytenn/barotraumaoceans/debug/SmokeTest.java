package com.m4ytenn.barotraumaoceans.debug;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
import com.m4ytenn.barotraumaoceans.worldgen.TerrainAccess;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.m4ytenn.barotraumaoceans.worldgen.core.TunnelNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Development-only world generation smoke test (enabled with -Dbarotrauma_oceans.smokeTest=true, used by CI).
 * Generates chunks in interesting places of a Barotrauma Oceans world, checks a few invariants, prints
 * ASCII cross-sections of the real generated blocks and stops the server.
 */
public final class SmokeTest {
    private static final List<String> FAILURES = new ArrayList<>();

    private SmokeTest() {
    }

    public static void init() {
        if (Boolean.getBoolean("barotrauma_oceans.smokeTest")) {
            BarotraumaOceans.LOGGER.info("BAROTRAUMA SMOKE TEST: enabled");
            NeoForge.EVENT_BUS.addListener(SmokeTest::onServerStarted);
        }
    }

    private static void log(String msg, Object... args) {
        BarotraumaOceans.LOGGER.info("SMOKE | " + msg, args);
    }

    private static void fail(String msg) {
        FAILURES.add(msg);
        BarotraumaOceans.LOGGER.error("SMOKE | FAIL: {}", msg);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            run(server);
        } catch (Throwable t) {
            BarotraumaOceans.LOGGER.error("SMOKE | exception", t);
            FAILURES.add("exception: " + t);
        }
        if (FAILURES.isEmpty()) {
            BarotraumaOceans.LOGGER.info("BAROTRAUMA SMOKE TEST: PASSED");
        } else {
            BarotraumaOceans.LOGGER.error("BAROTRAUMA SMOKE TEST: FAILED {}", FAILURES);
        }
        server.halt(false);
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        OceanTerrain t = TerrainAccess.terrain(level);
        if (t == null) {
            fail("overworld does not use the Barotrauma Oceans generator: " + level.getChunkSource().getGenerator().getClass());
            return;
        }
        log("terrain seed {} water level {} ice {} minY {} height {}", t.seed, t.waterLevel, t.ice, level.getMinBuildHeight(), level.getHeight());

        // 1. Spawn
        BlockPos spawn = level.getSharedSpawnPos();
        log("spawn at {}", spawn);
        BlockState feet = level.getBlockState(spawn);
        BlockState below = level.getBlockState(spawn.below());
        log("spawn feet={} below={}", feet, below);
        if (!feet.isAir() || below.isAir() || !below.getFluidState().isEmpty()) {
            fail("spawn point is not a safe air pocket: feet=" + feet + " below=" + below);
        }

        // 2. Nearest station and one of its channels
        TunnelNetwork.Station st = t.network.nearestStations(spawn.getX(), spawn.getZ(), 1, 1).get(0);
        log("nearest station ({},{}) at {} {} {} floor {}", st.gx, st.gz, (int) st.x, (int) st.y, (int) st.z, st.floorBlockY());
        long t0 = System.nanoTime();
        int chunks = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk(((int) st.x >> 4) + dx, ((int) st.z >> 4) + dz);
                chunks++;
            }
        }
        log("generated {} chunks around the station in {} ms", chunks, (System.nanoTime() - t0) / 1_000_000);
        BlockPos inside = new BlockPos((int) st.x, st.floorBlockY() + 3, (int) st.z);
        BlockState in = level.getBlockState(inside);
        Holder<Biome> biome = level.getBiome(inside);
        log("station centre block {} biome {}", in, biome.unwrapKey().map(k -> k.location().toString()).orElse("?"));
        if (!in.is(Blocks.WATER)) {
            fail("station cavern centre is not water: " + in);
        }
        if (!biome.unwrapKey().map(k -> k.location().getPath().equals("station_hollow")).orElse(false)) {
            fail("station cavern biome is " + biome);
        }

        List<TunnelNetwork.Tunnel> tunnels = t.network.tunnelsOf(st);
        if (tunnels.isEmpty()) {
            fail("station has no channels");
        } else {
            TunnelNetwork.Tunnel tn = tunnels.get(0);
            int k = tn.n / 2;
            BlockPos mid = new BlockPos((int) tn.px[k], (int) tn.py[k], (int) tn.pz[k]);
            level.getChunk(mid.getX() >> 4, mid.getZ() >> 4);
            BlockState ms = level.getBlockState(mid);
            log("channel {} ({}) midpoint {} = {} biome {}", tn.cls, (int) tn.length, mid, ms,
                    level.getBiome(mid).unwrapKey().map(kk -> kk.location().toString()).orElse("?"));
            if (!ms.is(Blocks.WATER)) {
                fail("channel centre line is not water at " + mid + ": " + ms);
            }
            section(level, "channel cross-section", mid, tn.dir == TunnelNetwork.DIR_EAST, 70, 60, 1);
        }

        // 3. Material census + lava check around the station
        Map<String, Integer> census = new TreeMap<>();
        int lava = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunk(((int) st.x >> 4) + dx, ((int) st.z >> 4) + dz);
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y += 2) {
                    for (int x = 0; x < 16; x += 2) {
                        for (int z = 0; z < 16; z += 2) {
                            BlockState s = chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z));
                            if (s.is(Blocks.LAVA)) {
                                lava++;
                            }
                            census.merge(s.getBlock().getDescriptionId().replace("block.minecraft.", ""), 1, Integer::sum);
                        }
                    }
                }
            }
        }
        log("block census around station: {}", census);
        if (lava > 0) {
            fail(lava + " lava blocks found in the sampled chunks");
        }

        // 4. Cross-sections of real blocks
        section(level, "station cross-section (X)", new BlockPos((int) st.x, (int) st.y, (int) st.z), true, 110, 70, 2);
        section(level, "seabed and ice above the station (X)", new BlockPos((int) st.x, (int) ((t.seabed.seabed(st.x, st.z) + 240) / 2), (int) st.z), true, 120, 180, 3);
        section(level, "spawn pocket (X)", spawn, true, 16, 14, 1);
    }

    private static char glyph(BlockState s) {
        if (s.isAir()) return ' ';
        if (s.is(Blocks.WATER)) return '.';
        if (s.is(Blocks.BUBBLE_COLUMN)) return ':';
        if (s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE) || s.is(Blocks.ICE)) return '*';
        if (s.is(Blocks.SNOW_BLOCK)) return '^';
        if (s.is(Blocks.SAND) || s.is(Blocks.SANDSTONE)) return 's';
        if (s.is(Blocks.GRAVEL)) return 'g';
        if (s.is(Blocks.MUD) || s.is(Blocks.CLAY)) return 'm';
        if (s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT) || s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS)) return '"';
        if (s.is(Blocks.LAVA)) return 'L';
        if (s.is(Blocks.BEDROCK)) return '=';
        if (!s.getFluidState().isEmpty()) return ',';
        return '#';
    }

    /** Prints a vertical cross-section centred at {@code centre}. */
    private static void section(ServerLevel level, String title, BlockPos centre, boolean alongX, int halfWidth, int halfHeight, int step) {
        StringBuilder sb = new StringBuilder();
        sb.append("SMOKE | ").append(title).append(" centre ").append(centre).append(" step ").append(step).append('\n');
        for (int dy = halfHeight; dy >= -halfHeight; dy -= step) {
            int y = centre.getY() + dy;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                continue;
            }
            sb.append(String.format("SMOKE | %5d ", y));
            for (int d = -halfWidth; d <= halfWidth; d += step) {
                BlockPos p = alongX ? new BlockPos(centre.getX() + d, y, centre.getZ()) : new BlockPos(centre.getX(), y, centre.getZ() + d);
                sb.append(glyph(level.getBlockState(p)));
            }
            sb.append('\n');
        }
        BarotraumaOceans.LOGGER.info("\n{}", sb);
    }
}
