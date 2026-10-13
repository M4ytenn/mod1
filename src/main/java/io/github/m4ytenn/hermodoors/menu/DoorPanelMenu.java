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

/**
 * The door's control panel. It has no slots; it exists so the server knows who is working which door and
 * closes the panel when they walk away or the door changes under them.
 */
public class DoorPanelMenu extends AbstractContainerMenu {
    private final BlockPos pos;

    public DoorPanelMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.DOOR_PANEL.get(), containerId);
        this.pos = pos;
    }

    public static DoorPanelMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new DoorPanelMenu(containerId, inventory, buf.readBlockPos());
    }

    public BlockPos pos() {
        return pos;
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
        return door != null && !door.isUnlocked() && door.isInReach(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            HermoDoorBlockEntity door = door(player);
            if (door != null) {
                door.releaseOperator(player);
            }
        }
    }
}
