package io.github.m4ytenn.hermodoors.door;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class HermoDoorItem extends BlockItem {
    private final DoorType type;

    public HermoDoorItem(HermoDoorBlock block, Properties properties) {
        super(block, properties);
        this.type = block.type();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hermodoors.size", type.width, type.height).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hermodoors." + type.id).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.hermodoors.passport_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
