package io.github.m4ytenn.hermodoors.door;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Гермозатвор: 4 by 4 metro gate that slides sideways into the wall. */
public class GermozatvorBlock extends HermoDoorBlock {
    public static final MapCodec<GermozatvorBlock> CODEC = simpleCodec(GermozatvorBlock::new);
    public static final IntegerProperty PART_X = IntegerProperty.create("px", 0, 3);
    public static final IntegerProperty PART_Y = IntegerProperty.create("py", 0, 3);

    public GermozatvorBlock(Properties properties) {
        super(DoorType.GERMOZATVOR, properties);
    }

    @Override
    protected IntegerProperty partX() {
        return PART_X;
    }

    @Override
    protected IntegerProperty partY() {
        return PART_Y;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
