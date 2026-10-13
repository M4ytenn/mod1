package io.github.m4ytenn.hermodoors.registry;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.github.m4ytenn.hermodoors.door.HermoDoorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HermoDoors.MODID);

    public static final DeferredItem<HermoDoorItem> DZG_DOOR = ITEMS.register("dzg_door",
            () -> new HermoDoorItem(ModBlocks.DZG_DOOR.get(), new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<HermoDoorItem> GERMOZATVOR = ITEMS.register("germozatvor",
            () -> new HermoDoorItem(ModBlocks.GERMOZATVOR.get(), new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    private ModItems() {}
}
