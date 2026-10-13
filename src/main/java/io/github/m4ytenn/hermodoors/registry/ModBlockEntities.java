package io.github.m4ytenn.hermodoors.registry;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HermoDoors.MODID);

    public static final Supplier<BlockEntityType<HermoDoorBlockEntity>> DOOR = BLOCK_ENTITIES.register("door",
            () -> BlockEntityType.Builder.of(HermoDoorBlockEntity::new, ModBlocks.DZG_DOOR.get(), ModBlocks.GERMOZATVOR.get()).build(null));

    private ModBlockEntities() {}
}
