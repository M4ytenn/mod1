package com.m4ytenn.barotraumaoceans.worldgen.feature;

import com.m4ytenn.barotraumaoceans.worldgen.core.OceanSettings;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** A cluster of very tall kelp stalks (kelp forests). */
public class GiantKelpFeature extends Feature<GiantKelpFeature.Config> {

    public record Config(IntProvider stalks, int spread, IntProvider height) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                IntProvider.codec(1, 128).fieldOf("stalks").forGetter(Config::stalks),
                Codec.intRange(0, 15).fieldOf("spread").forGetter(Config::spread),
                IntProvider.codec(1, 384).fieldOf("height").forGetter(Config::height)
        ).apply(i, Config::new));
    }

    public GiantKelpFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        Config cfg = ctx.config();
        OceanSettings settings = FeatureUtil.settings(level);
        OceanTerrain terrain = FeatureUtil.terrain(level);
        int maxHeight = settings != null ? settings.getInt(OceanSettings.KELP_MAX_HEIGHT) : 48;
        int count = cfg.stalks().sample(random);
        boolean placedAny = false;
        for (int s = 0; s < count; s++) {
            BlockPos start = ctx.origin().offset(random.nextInt(cfg.spread() * 2 + 1) - cfg.spread(), 4, random.nextInt(cfg.spread() * 2 + 1) - cfg.spread());
            if (!FeatureUtil.writable(level, start)) {
                continue;
            }
            BlockPos base = FeatureUtil.floorBelow(level, start, 16);
            if (base == null) {
                continue;
            }
            int h = Math.min(maxHeight, cfg.height().sample(random));
            if (terrain != null && terrain.ice) {
                double ceiling = terrain.seabed.iceBottom(base.getX(), base.getZ()) - 3;
                h = (int) Math.min(h, ceiling - base.getY());
            }
            BlockPos.MutableBlockPos m = base.mutable();
            int placed = 0;
            for (int k = 0; k < h; k++) {
                if (!FeatureUtil.isWaterSource(level, m) || !FeatureUtil.isWaterSource(level, m.above())) {
                    break;
                }
                level.setBlock(m, Blocks.KELP_PLANT.defaultBlockState(), 2);
                placed++;
                m.move(0, 1, 0);
            }
            if (placed > 0) {
                // top piece
                m.move(0, -1, 0);
                level.setBlock(m, Blocks.KELP.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 20 + random.nextInt(5)), 2);
                placedAny = true;
            }
        }
        return placedAny;
    }
}
