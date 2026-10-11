package com.m4ytenn.barotraumaoceans.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * A hydrothermal vent chimney: a tapering column of basalt/blackstone with magma veins. Most vents end in
 * soul sand that pushes a bubble column (an upwelling current) towards the ice; some are hot black smokers
 * capped with magma.
 */
public class HydrothermalVentFeature extends Feature<HydrothermalVentFeature.Config> {

    public record Config(IntProvider height, float upwellingChance, int maxColumn) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                IntProvider.codec(1, 64).fieldOf("height").forGetter(Config::height),
                Codec.floatRange(0, 1).fieldOf("upwelling_chance").forGetter(Config::upwellingChance),
                Codec.intRange(0, 512).fieldOf("max_bubble_column").forGetter(Config::maxColumn)
        ).apply(i, Config::new));
    }

    public HydrothermalVentFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos base = FeatureUtil.floorBelow(level, ctx.origin().above(4), 16);
        if (base == null) {
            return false;
        }
        int height = ctx.config().height().sample(random);
        double baseRadius = 1.5 + random.nextFloat() * 2.0;
        BlockState basalt = Blocks.BASALT.defaultBlockState();
        BlockState black = Blocks.BLACKSTONE.defaultBlockState();
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        int top = base.getY();
        for (int h = -1; h < height; h++) {
            double r = baseRadius * (1.0 - 0.65 * Math.max(0, h) / (double) height) + 0.35;
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r) {
                        continue;
                    }
                    BlockPos p = base.offset(dx, h, dz);
                    if (!FeatureUtil.writable(level, p)) {
                        continue;
                    }
                    if (h >= 0 && !FeatureUtil.isWater(level, p)) {
                        continue;
                    }
                    BlockState s = random.nextFloat() < 0.15F ? magma : (random.nextBoolean() ? basalt : black);
                    level.setBlock(p, s, 2);
                }
            }
            top = base.getY() + h;
        }
        BlockPos cap = new BlockPos(base.getX(), top, base.getZ());
        if (random.nextFloat() < ctx.config().upwellingChance()) {
            level.setBlock(cap, Blocks.SOUL_SAND.defaultBlockState(), 2);
            BlockState bubbles = Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false);
            BlockPos.MutableBlockPos m = cap.above().mutable();
            for (int i = 0; i < ctx.config().maxColumn() && FeatureUtil.isWaterSource(level, m); i++) {
                level.setBlock(m, bubbles, 2);
                m.move(0, 1, 0);
            }
        } else {
            level.setBlock(cap, magma, 2);
        }
        return true;
    }
}
