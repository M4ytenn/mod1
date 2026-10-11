package com.m4ytenn.barotraumaoceans.worldgen.placement;

import com.m4ytenn.barotraumaoceans.registry.BORegistries;
import com.m4ytenn.barotraumaoceans.worldgen.TerrainAccess;
import com.m4ytenn.barotraumaoceans.worldgen.core.OceanTerrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Like minecraft:count, but multiplied by a density value from the world's Barotrauma Oceans settings. */
public class ScaledCountPlacement extends PlacementModifier {
    public static final MapCodec<ScaledCountPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            IntProvider.codec(0, 4096).fieldOf("count").forGetter(p -> p.count),
            Codec.STRING.fieldOf("setting").forGetter(p -> p.setting)
    ).apply(i, ScaledCountPlacement::new));

    private final IntProvider count;
    private final String setting;

    public ScaledCountPlacement(IntProvider count, String setting) {
        this.count = count;
        this.setting = setting;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        double mult = 1.0;
        OceanTerrain t = TerrainAccess.terrain(context.getLevel());
        if (t != null) {
            mult = t.settings.getNumber(setting, 1.0);
        }
        double expected = count.sample(random) * mult;
        int n = (int) Math.floor(expected);
        if (random.nextDouble() < expected - n) {
            n++;
        }
        return IntStream.range(0, n).mapToObj(i -> pos);
    }

    @Override
    public PlacementModifierType<?> type() {
        return BORegistries.SCALED_COUNT.get();
    }
}
