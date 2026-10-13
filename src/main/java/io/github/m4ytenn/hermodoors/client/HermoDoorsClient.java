package io.github.m4ytenn.hermodoors.client;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.github.m4ytenn.hermodoors.client.dev.ScreenshotTour;
import io.github.m4ytenn.hermodoors.client.gui.DoorPanelScreen;
import io.github.m4ytenn.hermodoors.client.gui.PassportScreen;
import io.github.m4ytenn.hermodoors.client.render.DoorRenderer;
import io.github.m4ytenn.hermodoors.registry.ModBlockEntities;
import io.github.m4ytenn.hermodoors.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point; never loaded on a dedicated server. */
@Mod(value = HermoDoors.MODID, dist = Dist.CLIENT)
public final class HermoDoorsClient {
    public HermoDoorsClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(HermoDoorsClient::registerScreens);
        modBus.addListener(HermoDoorsClient::registerRenderers);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        if (Boolean.getBoolean("hermodoors.tour")) {
            ScreenshotTour.start();
        }
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.DOOR_PANEL.get(), DoorPanelScreen::new);
        event.register(ModMenus.DOOR_PASSPORT.get(), PassportScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.DOOR.get(), DoorRenderer::new);
    }
}
