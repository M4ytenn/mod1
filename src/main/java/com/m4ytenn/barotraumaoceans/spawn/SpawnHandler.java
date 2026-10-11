package com.m4ytenn.barotraumaoceans.spawn;

import com.m4ytenn.barotraumaoceans.BarotraumaOceans;
import com.m4ytenn.barotraumaoceans.config.BOConfig;
import com.m4ytenn.barotraumaoceans.worldgen.OceanChunkGenerator;
import com.m4ytenn.barotraumaoceans.worldgen.TerrainAccess;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Creates the spawn pocket/raft on world creation and puts players into it. */
public final class SpawnHandler {
    private static final String JOINED_TAG = BarotraumaOceans.MODID + ":joined";

    private SpawnHandler() {
    }

    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        OceanChunkGenerator gen = TerrainAccess.generator(level);
        if (gen == null) {
            return;
        }
        BOConfig.SpawnMode mode = BOConfig.SPAWN_MODE.get();
        if (mode == BOConfig.SpawnMode.VANILLA) {
            return;
        }
        OceanTerrain terrain = gen.terrain(level.getChunkSource().randomState());
        BlockPos spawn = SpawnStructure.build(level, terrain, mode, BOConfig.SPAWN_POCKET_RADIUS.get());
        event.getSettings().setSpawn(spawn, 0.0F);
        if (BOConfig.SET_SPAWN_RADIUS_ZERO.get()) {
            level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
        }
        event.setCanceled(true);
    }

    private static boolean active(ServerLevel overworld) {
        return overworld != null && TerrainAccess.isOceanLevel(overworld) && BOConfig.SPAWN_MODE.get() != BOConfig.SpawnMode.VANILLA;
    }

    private static void sendToSpawn(ServerPlayer player, ServerLevel overworld) {
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, overworld.getSharedSpawnAngle(), 0.0F);
        player.fallDistance = 0;
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BOConfig.TELEPORT_NEW_PLAYERS.get()) {
            return;
        }
        ServerLevel overworld = player.server.overworld();
        if (!active(overworld)) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(JOINED_TAG)) {
            return;
        }
        persisted.putBoolean(JOINED_TAG, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        if (player.getRespawnPosition() == null) {
            sendToSpawn(player, overworld);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player) || !BOConfig.RESPAWN_AT_SPAWN_STRUCTURE.get()) {
            return;
        }
        ServerLevel overworld = player.server.overworld();
        if (!active(overworld) || player.level() != overworld) {
            return;
        }
        BlockPos bed = player.getRespawnPosition();
        boolean atBed = bed != null && player.getRespawnDimension() == Level.OVERWORLD && player.blockPosition().distSqr(bed) < 16 * 16;
        if (!atBed) {
            sendToSpawn(player, overworld);
        }
    }
}
