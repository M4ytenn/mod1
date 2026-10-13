package io.github.m4ytenn.hermodoors.door;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** ДЗГ: 2 wide, 3 tall, swings open on its hinges. */
public class DzgDoorBlock extends HermoDoorBlock {
    public static final MapCodec<DzgDoorBlock> CODEC = simpleCodec(DzgDoorBlock::new);
    public static final IntegerProperty PART_X = IntegerProperty.create("px", 0, 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("py", 0, 2);

    public DzgDoorBlock(Properties properties) {
        super(DoorType.DZG, properties);
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
