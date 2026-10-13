package io.github.m4ytenn.hermodoors.puzzle;

import io.github.m4ytenn.hermodoors.door.Stage;
import java.util.List;
import java.util.Random;

/**
 * Everything about a door's puzzles that has to agree between client and server, derived from the door's seed
 * and difficulty. The client simulates the minigames with these numbers; the server uses them to check
 * the results it is sent.
 */
public final class PuzzleSpec {
    public final Power power;
    public final Pump pump;
    public final Clamps clamps;
    public final Wheel wheel;

    private PuzzleSpec(Power power, Pump pump, Clamps clamps, Wheel wheel) {
        this.power = power;
        this.pump = pump;
        this.clamps = clamps;
        this.wheel = wheel;
    }

    public static PuzzleSpec create(long seed, int difficulty) {
        int d = Math.max(1, Math.min(3, difficulty));
        return new PuzzleSpec(
                Power.create(new Random(seed * 31 + 1), d),
                Pump.create(new Random(seed * 31 + 2), d),
                Clamps.create(new Random(seed * 31 + 3), d),
                Wheel.create(new Random(seed * 31 + 4), d));
    }

    /** Fastest plausible time for a human to finish a stage, used to reject forged completions. */
    public int minTicks(Stage stage) {
        return switch (stage) {
            case POWER -> power.count * 5;
            case PUMP -> pump.holdTicks - 5;
            case CLAMPS -> clamps.totalHits() * 5;
            case WHEEL -> (int) (wheel.turns * 360f / wheel.maxSpeed * 0.85f);
        };
    }

    /**
     * Breaker panel. Breaker {@code i} is fed through breaker {@code parent[i]} (or straight from the main
     * switch when that is -1) and can only be closed once its feeder is live.
     */
    public static final class Power {
        public final int count;
        public final int[] parent;

        private Power(int count, int[] parent) {
            this.count = count;
            this.parent = parent;
        }

        static Power create(Random rnd, int d) {
            int count = 3 + d;
            int[] perm = new int[count];
            for (int i = 0; i < count; i++) {
                perm[i] = i;
            }
            for (int i = count - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int t = perm[i];
                perm[i] = perm[j];
                perm[j] = t;
            }
            int[] parent = new int[count];
            parent[perm[0]] = -1;
            for (int k = 1; k < count; k++) {
                int p;
                if (d == 3) {
                    p = rnd.nextFloat() < 0.6f ? perm[k - 1] : perm[rnd.nextInt(k)];
                } else if (d == 2) {
                    p = rnd.nextFloat() < 0.75f ? perm[rnd.nextInt(k)] : -1;
                } else {
                    p = rnd.nextBoolean() ? perm[rnd.nextInt(k)] : -1;
                }
                parent[perm[k]] = p;
            }
            // Make sure there is something to work out: at least two breakers must hang off another breaker.
            int dependent = 0;
            for (int p : parent) {
                if (p >= 0) {
                    dependent++;
                }
            }
            for (int k = 1; k < count && dependent < 2; k++) {
                if (parent[perm[k]] < 0) {
                    parent[perm[k]] = perm[k - 1];
                    dependent++;
                }
            }
            return new Power(count, parent);
        }

        public boolean canClose(boolean[] closed, int index) {
            int p = parent[index];
            return p < 0 || closed[p];
        }

        public int depth(int index) {
            int depth = 0;
            for (int p = parent[index]; p >= 0; p = parent[p]) {
                depth++;
            }
            return depth;
        }

        /** True if closing the breakers in this order never closes one before its feeder. */
        public boolean isValidOrder(List<Integer> order) {
            if (order.size() != count) {
                return false;
            }
            boolean[] closed = new boolean[count];
            for (int index : order) {
                if (index < 0 || index >= count || closed[index] || !canClose(closed, index)) {
                    return false;
                }
                closed[index] = true;
            }
            return true;
        }
    }

    /** Hand pump and manometer. Pressure in kgf/cm², scale 0..16. */
    public static final class Pump {
        public static final float SCALE_MAX = 16f;
        public static final float RELIEF = 14.5f;

        public final float bandLow;
        public final float bandHigh;
        /** Fraction of the pressure lost per tick. */
        public final float leak;
        /** Pressure gained by one full stroke at zero pressure. */
        public final float strokeGain;
        public final int holdTicks;

        private Pump(float bandLow, float bandHigh, float leak, float strokeGain, int holdTicks) {
            this.bandLow = bandLow;
            this.bandHigh = bandHigh;
            this.leak = leak;
            this.strokeGain = strokeGain;
            this.holdTicks = holdTicks;
        }

        static Pump create(Random rnd, int d) {
            // Tuned with a simulated player: category I wants about one stroke a second to hold the band,
            // II about one and a half, III two.
            float center = 9.2f + rnd.nextFloat() * 1.8f;
            float half = d == 1 ? 1.6f : d == 2 ? 1.25f : 0.95f;
            float leak = d == 1 ? 0.0040f : d == 2 ? 0.0050f : 0.0060f;
            int hold = d == 1 ? 60 : d == 2 ? 90 : 120;
            return new Pump(center - half, center + half, leak, 1.6f, hold);
        }

        public float strokeGain(float pressure, float strokeFraction) {
            return strokeGain * strokeFraction * Math.max(0.15f, 1f - pressure / 22f);
        }
    }

    /** Seized wedge clamps, knocked loose one by one with well-timed hammer blows. */
    public static final class Clamps {
        public final int count;
        public final int[] hits;
        /** Half width of the chalk mark, as a fraction of the lever's travel. */
        public final float markHalfWidth;
        /** Ticks for the lever to rock there and back once. */
        public final int periodTicks;
        private final long markSeed;

        private Clamps(int count, int[] hits, float markHalfWidth, int periodTicks, long markSeed) {
            this.count = count;
            this.hits = hits;
            this.markHalfWidth = markHalfWidth;
            this.periodTicks = periodTicks;
            this.markSeed = markSeed;
        }

        static Clamps create(Random rnd, int d) {
            int count = 3 + d;
            int[] hits = new int[count];
            for (int i = 0; i < count; i++) {
                hits[i] = d == 1 ? 1 + rnd.nextInt(2) : d == 2 ? 1 + rnd.nextInt(3) : 2 + rnd.nextInt(2);
            }
            float half = d == 1 ? 0.10f : d == 2 ? 0.075f : 0.055f;
            int period = d == 1 ? 40 : d == 2 ? 32 : 26;
            return new Clamps(count, hits, half, period, rnd.nextLong());
        }

        public int totalHits() {
            return hitsFrom(0);
        }

        /** Blows still needed when the clamps before {@code firstClamp} are already loose. */
        public int hitsFrom(int firstClamp) {
            int total = 0;
            for (int i = Math.max(0, firstClamp); i < count; i++) {
                total += hits[i];
            }
            return total;
        }

        /** Where the chalk mark sits (0..1 of the lever travel) for the given clamp and blow number. */
        public float markPosition(int clamp, int blow) {
            Random rnd = new Random(markSeed + clamp * 7919L + blow * 104729L);
            return 0.18f + rnd.nextFloat() * 0.64f;
        }

        /** Lever position (0..1) at a time in ticks. */
        public float leverPosition(float ticks, float periodScale) {
            double phase = ticks / (periodTicks * periodScale) * Math.PI * 2.0;
            return (float) (0.5 + 0.5 * Math.sin(phase));
        }
    }

    /** The handwheel that withdraws the bolts. */
    public static final class Wheel {
        public final int turns;
        /** Fastest the wheel will turn, degrees per tick, before it starts to fight back. */
        public final float maxSpeed;
        /** Centres (degrees, 0..360) of tight spots in each revolution. */
        public final float[] tightSpots;
        public final float tightWidth;
        public final float tightFactor;

        private Wheel(int turns, float maxSpeed, float[] tightSpots, float tightWidth, float tightFactor) {
            this.turns = turns;
            this.maxSpeed = maxSpeed;
            this.tightSpots = tightSpots;
            this.tightWidth = tightWidth;
            this.tightFactor = tightFactor;
        }

        static Wheel create(Random rnd, int d) {
            int turns = d == 1 ? 3 : d == 2 ? 4 : 6;
            float maxSpeed = d == 1 ? 30f : d == 2 ? 24f : 19f;
            int spots = 1 + d;
            float[] centers = new float[spots];
            for (int i = 0; i < spots; i++) {
                centers[i] = (360f / spots) * i + rnd.nextFloat() * (300f / spots);
            }
            return new Wheel(turns, maxSpeed, centers, 34f, d == 3 ? 0.4f : 0.5f);
        }

        /** Speed limit at a given wheel angle (degrees, any range). */
        public float speedLimit(float angle) {
            float a = ((angle % 360f) + 360f) % 360f;
            float limit = maxSpeed;
            for (float c : tightSpots) {
                float diff = Math.abs(a - c);
                diff = Math.min(diff, 360f - diff);
                if (diff < tightWidth / 2f) {
                    float k = 1f - diff / (tightWidth / 2f);
                    limit = Math.min(limit, maxSpeed * (1f - (1f - tightFactor) * k));
                }
            }
            return limit;
        }
    }
}
