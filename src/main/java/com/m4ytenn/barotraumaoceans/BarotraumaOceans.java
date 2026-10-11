package com.m4ytenn.barotraumaoceans;

import com.m4ytenn.barotraumaoceans.command.BarotraumaCommand;
import com.m4ytenn.barotraumaoceans.compat.SpawnRules;
import com.m4ytenn.barotraumaoceans.config.BOClientConfig;
import com.m4ytenn.barotraumaoceans.config.BOConfig;
import com.m4ytenn.barotraumaoceans.debug.SmokeTest;
import com.m4ytenn.barotraumaoceans.registry.BORegistries;
import com.m4ytenn.barotraumaoceans.spawn.SpawnHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Barotrauma Oceans: a world type with a sealed, very deep ocean, regional seabed terrain and a network of
 * wide, mostly junction-free channels between deep station caverns.
 */
@Mod(BarotraumaOceans.MODID)
public final class BarotraumaOceans {
    public static final String MODID = "barotrauma_oceans";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BarotraumaOceans(IEventBus modBus, ModContainer container) {
        BORegistries.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, BOConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, BOClientConfig.SPEC);

        modBus.addListener(EventPriority.LOWEST, SpawnRules::onRegisterSpawnPlacements);

        NeoForge.EVENT_BUS.addListener(BarotraumaCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SpawnHandler::onCreateSpawn);
        NeoForge.EVENT_BUS.addListener(SpawnHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SpawnHandler::onPlayerRespawn);

        SmokeTest.init();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
