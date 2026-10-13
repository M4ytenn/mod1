package io.github.m4ytenn.hermodoors.registry;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.github.m4ytenn.hermodoors.door.DzgDoorBlock;
import io.github.m4ytenn.hermodoors.door.GermozatvorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HermoDoors.MODID);

    public static final DeferredBlock<DzgDoorBlock> DZG_DOOR = BLOCKS.register("dzg_door", () -> new DzgDoorBlock(doorProperties(MapColor.COLOR_GREEN)));
    public static final DeferredBlock<GermozatvorBlock> GERMOZATVOR = BLOCKS.register("germozatvor", () -> new GermozatvorBlock(doorProperties(MapColor.METAL)));

    private ModBlocks() {}

    /**
     * Blast doors cannot be mined, blown up, pushed or pulled. Creative players can still break them and the
     * owner gets the door back through its passport.
     */
    private static BlockBehaviour.Properties doorProperties(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0F, 3_600_000.0F)
                .sound(SoundType.NETHERITE_BLOCK)
                .noOcclusion()
                .noLootTable()
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((state, level, pos, entityType) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }
}
