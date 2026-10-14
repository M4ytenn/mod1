package com.subsonar.vista;

import com.subsonar.block.SonarConsoleBlockEntity;
import net.mehvahdjukaar.vista.common.cassette.HollowCassetteItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Lets Vista televisions show the Sub Sonar display. The sonar console becomes a Vista broadcast source (see
 * {@code SonarConsoleBlockEntityMixin}): a hollow cassette links to it like to a viewfinder, and a TV playing
 * that cassette draws the sonar picture.
 */
@Mod(SubSonarVista.MODID)
public final class SubSonarVista {
    public static final String MODID = "subsonar_vista";

    public SubSonarVista(IEventBus modBus) {
        modBus.addListener(SubSonarVista::registerPayloads);
        NeoForge.EVENT_BUS.addListener(SubSonarVista::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(SubSonarVista::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(SubSonarVista::onServerStopped);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(WatchingSonarPayload.TYPE, WatchingSonarPayload.STREAM_CODEC, TvWatchers::onWatching);
    }

    /** Right-clicking a console with a hollow cassette links the cassette instead of opening the sonar screen. */
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof HollowCassetteItem
                && event.getLevel().getBlockEntity(event.getPos()) instanceof SonarConsoleBlockEntity) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TvWatchers.forget(event.getEntity().getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        TvWatchers.clear();
    }
}
