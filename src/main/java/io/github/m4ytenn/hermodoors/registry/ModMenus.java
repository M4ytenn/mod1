package io.github.m4ytenn.hermodoors.registry;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.github.m4ytenn.hermodoors.menu.DoorPanelMenu;
import io.github.m4ytenn.hermodoors.menu.DoorPassportMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HermoDoors.MODID);

    public static final Supplier<MenuType<DoorPanelMenu>> DOOR_PANEL = MENUS.register("door_panel",
            () -> IMenuTypeExtension.create(DoorPanelMenu::fromNetwork));
    public static final Supplier<MenuType<DoorPassportMenu>> DOOR_PASSPORT = MENUS.register("door_passport",
            () -> IMenuTypeExtension.create(DoorPassportMenu::fromNetwork));

    private ModMenus() {}
}
