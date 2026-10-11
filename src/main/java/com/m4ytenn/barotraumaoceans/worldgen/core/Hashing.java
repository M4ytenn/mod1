package com.m4ytenn.barotraumaoceans.worldgen.core;

/**
 * Deterministic, allocation-free hashing helpers used to derive per-cell / per-edge randomness
 * from the world seed. Everything in the terrain core is a pure function of (seed, coordinates),
 * so chunks can be generated in any order and on any thread.
 */
public final class Hashing {
    private Hashing() {
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long hash(long seed, long a) {
        return mix(seed + 0x9E3779B97F4A7C15L * (a + 1));
    }

    public static long hash(long seed, long a, long b) {
        return mix(hash(seed, a) + 0x632BE59BD9B4E019L * (b + 7));
    }

    public static long hash(long seed, long a, long b, long c) {
        return mix(hash(seed, a, b) + 0x85157AF5L * (c + 13));
    }

    public static long hash(long seed, long a, long b, long c, long d) {
        return mix(hash(seed, a, b, c) + 0x2545F4914F6CDD1DL * (d + 31));
    }

    /** Uniform double in [0, 1) from a hash value. */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** A tiny splittable random stream seeded from a hash, used when building network pieces. */
    public static final class Stream {
        private long state;

        public Stream(long seed) {
            this.state = mix(seed);
        }

        public long nextLong() {
            state += 0x9E3779B97F4A7C15L;
            return mix(state);
        }

        public double next() {
            return unit(nextLong());
        }

        public double range(double min, double max) {
            return min + (max - min) * next();
        }

        public int rangeInt(int minInclusive, int maxInclusive) {
            if (maxInclusive <= minInclusive) {
                return minInclusive;
            }
            return minInclusive + (int) Math.floor(next() * (maxInclusive - minInclusive + 1));
        }

        public boolean chance(double p) {
            return next() < p;
        }

        public double signed() {
            return next() * 2.0 - 1.0;
        }
    }
}
