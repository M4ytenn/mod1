package com.m4ytenn.barotraumaoceans.worldgen;

import com.m4ytenn.barotraumaoceans.worldgen.core.Hashing;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * A density function that always returns 0 but carries a world-seeded noise. Placed into the noise router's
 * "temperature" slot it lets every piece of our world generation (biome source, chunk generator, features,
 * commands) derive the same terrain seed from a {@code RandomState} / {@code Climate.Sampler}.
 */
public final class SeedCarrier implements DensityFunction {
    public static final MapCodec<SeedCarrier> DATA_CODEC = DensityFunction.NoiseHolder.CODEC
            .fieldOf("noise").xmap(SeedCarrier::new, SeedCarrier::noise);
    public static final KeyDispatchDataCodec<SeedCarrier> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    private final DensityFunction.NoiseHolder noise;
    private volatile long seed;
    private volatile boolean seedReady;

    public SeedCarrier(DensityFunction.NoiseHolder noise) {
        this.noise = noise;
    }

    public DensityFunction.NoiseHolder noise() {
        return noise;
    }

    /** True once the carrier has been wired with a seeded noise by {@code RandomState}. */
    public boolean isSeeded() {
        return noise.noise() != null;
    }

    public long terrainSeed() {
        if (!seedReady) {
            NormalNoise n = noise.noise();
            long h = 0x2545F4914F6CDD1DL;
            if (n != null) {
                for (int i = 0; i < 6; i++) {
                    double v = n.getValue(i * 37.719 + 0.123, i * 11.31 + 0.457, i * 23.17 + 0.891);
                    h = Hashing.mix(h ^ Double.doubleToLongBits(v) * 0x9E3779B97F4A7C15L);
                }
            }
            seed = h;
            seedReady = true;
        }
        return seed;
    }

    @Override
    public double compute(FunctionContext context) {
        return 0.0;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        java.util.Arrays.fill(array, 0.0);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new SeedCarrier(visitor.visitNoise(noise)));
    }

    @Override
    public double minValue() {
        return 0.0;
    }

    @Override
    public double maxValue() {
        return 0.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SeedCarrier other && other.noise.equals(noise);
    }

    @Override
    public int hashCode() {
        return noise.hashCode();
    }
}
