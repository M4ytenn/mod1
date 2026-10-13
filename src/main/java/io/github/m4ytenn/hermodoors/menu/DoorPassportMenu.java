package io.github.m4ytenn.hermodoors.menu;

import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity;
import io.github.m4ytenn.hermodoors.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The door's technical passport, where the owner picks the opening procedure. No slots. */
public class DoorPassportMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final boolean firstPlacement;

    public DoorPassportMenu(int containerId, Inventory inventory, BlockPos pos, boolean firstPlacement) {
        super(ModMenus.DOOR_PASSPORT.get(), containerId);
        this.pos = pos;
        this.firstPlacement = firstPlacement;
    }

    public static DoorPassportMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new DoorPassportMenu(containerId, inventory, pos, buf.readBoolean());
    }

    public BlockPos pos() {
        return pos;
    }

    /** True when the passport opened by itself right after the door was placed. */
    public boolean firstPlacement() {
        return firstPlacement;
    }

    @Nullable
    public HermoDoorBlockEntity door(Player player) {
        return player.level().getBlockEntity(pos) instanceof HermoDoorBlockEntity door ? door : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        HermoDoorBlockEntity door = door(player);
        return door != null && door.isInReach(player);
    }
}
