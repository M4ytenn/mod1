package com.m4ytenn.barotraumaoceans.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/** A thin rock needle standing on the seabed (or hanging from a ceiling when {@code hanging}). */
public class SeabedSpikeFeature extends Feature<SeabedSpikeFeature.Config> {

    public record Config(BlockStateProvider material, IntProvider height, float maxRadius, boolean hanging) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("material").forGetter(Config::material),
                IntProvider.codec(1, 96).fieldOf("height").forGetter(Config::height),
                Codec.floatRange(0.5F, 8.0F).fieldOf("max_radius").forGetter(Config::maxRadius),
                Codec.BOOL.optionalFieldOf("hanging", false).forGetter(Config::hanging)
        ).apply(i, Config::new));
    }

    public SeabedSpikeFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        Config cfg = ctx.config();
        BlockPos base;
        int dir = cfg.hanging() ? -1 : 1;
        if (cfg.hanging()) {
            BlockPos.MutableBlockPos m = ctx.origin().mutable();
            int steps = 0;
            while (steps++ < 32 && FeatureUtil.isWater(level, m.above())) {
                m.move(0, 1, 0);
            }
            if (!FeatureUtil.isWater(level, m) || FeatureUtil.isWater(level, m.above())) {
                return false;
            }
            base = m.immutable();
        } else {
            base = FeatureUtil.floorBelow(level, ctx.origin().above(3), 12);
        }
        if (base == null) {
            return false;
        }
        int height = cfg.height().sample(random);
        double radius = Math.min(cfg.maxRadius(), 0.6 + height * 0.09) * (0.8 + random.nextFloat() * 0.4);
        double leanX = (random.nextFloat() - 0.5) * 0.25;
        double leanZ = (random.nextFloat() - 0.5) * 0.25;
        boolean any = false;
        for (int h = -1; h < height; h++) {
            double r = radius * Math.pow(1.0 - Math.max(0, h) / (double) height, 1.2) + 0.3;
            int ri = (int) Math.ceil(r);
            int ox = (int) Math.round(leanX * h);
            int oz = (int) Math.round(leanZ * h);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r) {
                        continue;
                    }
                    BlockPos p = base.offset(dx + ox, h * dir, dz + oz);
                    if (!FeatureUtil.writable(level, p)) {
                        continue;
                    }
                    if (h >= 0 && !FeatureUtil.isWater(level, p)) {
                        continue;
                    }
                    level.setBlock(p, cfg.material().getState(random, p), 2);
                    any = true;
                }
            }
        }
        return any;
    }
}
