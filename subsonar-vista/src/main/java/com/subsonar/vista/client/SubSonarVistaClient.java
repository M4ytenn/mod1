package com.subsonar.vista.client;

import com.subsonar.block.SonarConsoleBlockEntity;
import com.subsonar.client.SonarScreen;
import com.subsonar.vista.SubSonarVista;
import com.subsonar.vista.WatchingSonarPayload;
import java.util.ArrayList;
import java.util.List;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client side. Sub Sonar's client keeps scan data for one console at a time, so of all the sonar feeds on TVs
 * around the player one is "watched": the first one drawn, until it has not been drawn for a second. The server
 * is told which TV shows it, and sends that console's scans here as if its screen were open.
 */
@Mod(value = SubSonarVista.MODID, dist = Dist.CLIENT)
public final class SubSonarVistaClient {
    /** Looking away culls the TV; keep the feed through a glance over the shoulder. */
    private static final long KEEP_WATCHING_MS = 10_000;
    /** Another console's TV takes over once the watched one has not been drawn for this long. */
    private static final long SWITCH_AFTER_MS = 1_000;
    private static final int REPORT_TICKS = 20;
    private static final int CLEANUP_TICKS = 100;

    private static final List<SonarVideoSource> SOURCES = new ArrayList<>();
    @Nullable
    private static BlockPos watchedConsole;
    @Nullable
    private static BlockPos watchedTv;
    private static long watchedAt;
    private static int ticks;

    public SubSonarVistaClient() {
        NeoForge.EVENT_BUS.addListener(SubSonarVistaClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SubSonarVistaClient::onLoggingOut);
    }

    public static IVideoSource createVideoSource(BlockEntity console) {
        SonarVideoSource source = new SonarVideoSource((SonarConsoleBlockEntity) console);
        SOURCES.add(source);
        return source;
    }

    /** A TV is drawing this console's feed. Returns whether it is the watched console. */
    static boolean claim(BlockPos console, BlockPos tv) {
        long now = Util.getMillis();
        if (watchedConsole != null && !watchedConsole.equals(console) && now - watchedAt <= SWITCH_AFTER_MS) {
            return false;
        }
        watchedConsole = console.immutable();
        watchedTv = tv.immutable();
        watchedAt = now;
        return true;
    }

    /** Whether scans for this console should be kept although its screen is not open. */
    public static boolean isWatchingOnTv(BlockPos console) {
        return watchedConsole != null
                && watchedConsole.equals(console)
                && Util.getMillis() - watchedAt < KEEP_WATCHING_MS
                && !(Minecraft.getInstance().screen instanceof SonarScreen);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null) {
            return;
        }
        ticks++;
        if (watchedConsole != null && Util.getMillis() - watchedAt >= KEEP_WATCHING_MS) {
            watchedConsole = null;
            watchedTv = null;
        }
        // At a sonar screen the screen itself decides which console's data arrives.
        if (ticks % REPORT_TICKS == 0 && watchedTv != null && !(mc.screen instanceof SonarScreen)) {
            PacketDistributor.sendToServer(new WatchingSonarPayload(watchedTv));
        }
        if (ticks % CLEANUP_TICKS == 0) {
            SOURCES.removeIf(source -> {
                boolean stale = source.isStale(mc.level);
                if (stale) {
                    source.close();
                }
                return stale;
            });
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SOURCES.forEach(SonarVideoSource::close);
        SOURCES.clear();
        watchedConsole = null;
        watchedTv = null;
    }
}
