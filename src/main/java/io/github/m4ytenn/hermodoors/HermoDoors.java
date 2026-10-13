package io.github.m4ytenn.hermodoors;

import com.mojang.logging.LogUtils;
import io.github.m4ytenn.hermodoors.network.ModNetwork;
import io.github.m4ytenn.hermodoors.registry.ModBlockEntities;
import io.github.m4ytenn.hermodoors.registry.ModBlocks;
import io.github.m4ytenn.hermodoors.registry.ModItems;
import io.github.m4ytenn.hermodoors.registry.ModMenus;
import io.github.m4ytenn.hermodoors.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(HermoDoors.MODID)
public final class HermoDoors {
    public static final String MODID = "hermodoors";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HermoDoors(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModSounds.SOUNDS.register(modBus);

        modBus.addListener(ModNetwork::register);
        modBus.addListener(HermoDoors::fillCreativeTabs);

        container.registerConfig(ModConfig.Type.SERVER, HermoConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, HermoConfig.CLIENT_SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void fillCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.DZG_DOOR.get());
            event.accept(ModItems.GERMOZATVOR.get());
        }
    }
}
