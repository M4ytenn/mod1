package com.subsonar.vista;

import com.subsonar.block.SonarConsoleBlockEntity;
import com.subsonar.menu.SonarMenu;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.broadcast.LevelBEBroadcastLocation;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.integration.sable.SableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Players watching a sonar console on a TV. Sub Sonar sends its scans only to players with the console's screen
 * open; the console mixin adds these players to that list, so their TV gets the same data. The client reports the
 * TV every second and a watch lapses a few seconds after the reports stop. Server thread only.
 */
public final class TvWatchers {
    private static final int HOLD_TICKS = 100;
    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    private record Watch(GlobalPos console, int until) {}

    private TvWatchers() {}

    static void onWatching(WatchingSonarPayload msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            GlobalPos console = consoleShownBy(player, msg.tv());
            if (console != null) {
                WATCHES.put(player.getUUID(), new Watch(console, player.server.getTickCount() + HOLD_TICKS));
            }
        }
    }

    /** The sonar console that a TV near the player is really showing, or null for anything else. */
    @Nullable
    private static GlobalPos consoleShownBy(ServerPlayer player, BlockPos tvPos) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(tvPos) || !nearPlayer(player, level, tvPos)
                || !(level.getBlockEntity(tvPos) instanceof TVBlockEntity tv)) {
            return null;
        }
        UUID feed = tv.getViewingFeedId();
        if (feed == null || !(BroadcastManager.getInstance(level).getFeedLocationById(feed) instanceof LevelBEBroadcastLocation location)) {
            return null;
        }
        GlobalPos console = location.globalPos();
        ServerLevel consoleLevel = player.server.getLevel(console.dimension());
        if (consoleLevel == null || !consoleLevel.isLoaded(console.pos())) {
            return null;
        }
        return consoleLevel.getBlockEntity(console.pos()) instanceof SonarConsoleBlockEntity ? console : null;
    }

    /** Within the player's view distance. A TV on a Sable ship is measured where the ship is in the world. */
    private static boolean nearPlayer(ServerPlayer player, ServerLevel level, BlockPos tvPos) {
        ChunkPos chunk = SableCompat.projectChunkOutOfSubLevel(level, tvPos);
        return chunk != null
                && player.chunkPosition().getChessboardDistance(chunk) <= player.server.getPlayerList().getViewDistance() + 1;
    }

    /** Adds the players watching this console on a TV to the console's list of screen viewers. */
    public static void addWatchers(ServerLevel level, BlockPos consolePos, List<ServerPlayer> viewers) {
        if (WATCHES.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Watch>> it = WATCHES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Watch> entry = it.next();
            Watch watch = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || watch.until() < now) {
                it.remove();
                continue;
            }
            if (!watch.console().pos().equals(consolePos) || !watch.console().dimension().equals(level.dimension())) {
                continue;
            }
            // Someone at a sonar screen gets that console's data; a second console's scans would mix into it.
            if (!(player.containerMenu instanceof SonarMenu) && !viewers.contains(player)) {
                viewers.add(player);
            }
        }
    }

    static void forget(UUID player) {
        WATCHES.remove(player);
    }

    static void clear() {
        WATCHES.clear();
    }
}
