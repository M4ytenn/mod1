package com.m4ytenn.barotraumaoceans.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * A big round patch of underwater vegetation on a floor (seabed or cave floor).
 * Styles: seagrass meadow, coral garden, glowing patch (sea pickles + glow lichen).
 */
public class SeabedMeadowFeature extends Feature<SeabedMeadowFeature.Config> {

    public enum Style implements StringRepresentable {
        SEAGRASS("seagrass"), CORAL("coral"), GLOW("glow");

        public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);
        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record Config(Style style, IntProvider radius, float density) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Style.CODEC.fieldOf("style").forGetter(Config::style),
                IntProvider.codec(1, 15).fieldOf("radius").forGetter(Config::radius),
                Codec.floatRange(0.0F, 1.0F).fieldOf("density").forGetter(Config::density)
        ).apply(i, Config::new));
    }

    private static final Block[] CORAL_PLANTS = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL};
    private static final Block[] CORAL_FANS = {Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN};
    private static final Block[] CORAL_BLOCKS = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};

    public SeabedMeadowFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        Config cfg = ctx.config();
        int radius = cfg.radius().sample(random);
        BlockPos origin = ctx.origin();
        int placed = 0;
        int palette = random.nextInt(CORAL_PLANTS.length);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius + 0.5) {
                    continue;
                }
                double edge = 1.0 - Math.max(0, d - radius * 0.6) / (radius * 0.4 + 0.5);
                if (random.nextFloat() > cfg.density() * edge) {
                    continue;
                }
                BlockPos start = origin.offset(dx, 3, dz);
                if (!FeatureUtil.writable(level, start)) {
                    continue;
                }
                BlockPos pos = FeatureUtil.floorBelow(level, start, 10);
                if (pos == null || !FeatureUtil.isWaterSource(level, pos)) {
                    continue;
                }
                if (place(level, random, pos, cfg.style(), palette)) {
                    placed++;
                }
            }
        }
        return placed > 0;
    }

    private boolean place(WorldGenLevel level, RandomSource random, BlockPos pos, Style style, int palette) {
        float r = random.nextFloat();
        switch (style) {
            case SEAGRASS -> {
                if (r < 0.62F) {
                    level.setBlock(pos, Blocks.SEAGRASS.defaultBlockState(), 2);
                } else if (r < 0.9F) {
                    if (!FeatureUtil.isWaterSource(level, pos.above())) {
                        level.setBlock(pos, Blocks.SEAGRASS.defaultBlockState(), 2);
                    } else {
                        level.setBlock(pos, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.LOWER), 2);
                        level.setBlock(pos.above(), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER), 2);
                    }
                } else if (r < 0.95F) {
                    pickle(level, random, pos);
                } else {
                    return false;
                }
                return true;
            }
            case CORAL -> {
                int kind = random.nextFloat() < 0.6F ? palette : random.nextInt(CORAL_PLANTS.length);
                if (r < 0.12F) {
                    // small coral mound
                    level.setBlock(pos, CORAL_BLOCKS[kind].defaultBlockState(), 2);
                    BlockPos up = pos.above();
                    if (FeatureUtil.isWaterSource(level, up)) {
                        level.setBlock(up, CORAL_FANS[kind].defaultBlockState(), 2);
                    }
                } else if (r < 0.45F) {
                    level.setBlock(pos, CORAL_PLANTS[kind].defaultBlockState(), 2);
                } else if (r < 0.7F) {
                    level.setBlock(pos, CORAL_FANS[kind].defaultBlockState(), 2);
                } else if (r < 0.88F) {
                    level.setBlock(pos, Blocks.SEAGRASS.defaultBlockState(), 2);
                } else if (r < 0.95F) {
                    pickle(level, random, pos);
                } else {
                    return false;
                }
                return true;
            }
            default -> {
                if (r < 0.35F) {
                    pickle(level, random, pos);
                } else if (r < 0.75F) {
                    level.setBlock(pos, Blocks.GLOW_LICHEN.defaultBlockState()
                            .setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
                            .setValue(BlockStateProperties.WATERLOGGED, true), 2);
                } else if (r < 0.9F) {
                    level.setBlock(pos, Blocks.SEAGRASS.defaultBlockState(), 2);
                } else {
                    return false;
                }
                return true;
            }
        }
    }

    private static void pickle(WorldGenLevel level, RandomSource random, BlockPos pos) {
        BlockState s = Blocks.SEA_PICKLE.defaultBlockState()
                .setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(4))
                .setValue(SeaPickleBlock.WATERLOGGED, true);
        level.setBlock(pos, s, 2);
    }
}
