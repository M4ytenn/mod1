package com.m4ytenn.barotraumaoceans;

import com.m4ytenn.barotraumaoceans.client.DepthFog;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point: config screen and the depth fog. */
@Mod(value = BarotraumaOceans.MODID, dist = Dist.CLIENT)
public final class BarotraumaOceansClient {
    public BarotraumaOceansClient(ModContainer container) {
        // Mods screen -> Barotrauma Oceans -> Config opens a sectioned editor for every option.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(DepthFog::onRenderFog);
        NeoForge.EVENT_BUS.addListener(DepthFog::onFogColor);
    }
}
