package com.subsonar.client;

import com.subsonar.menu.SonarMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Stub: see com/subsonar/README.txt. */
public abstract class SonarScreen extends AbstractContainerScreen<SonarMenu> {
    protected SonarScreen(SonarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    public BlockPos getConsolePos() { throw new AssertionError(); }
}
