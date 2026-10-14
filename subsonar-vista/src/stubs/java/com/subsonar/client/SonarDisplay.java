package com.subsonar.client;

import com.subsonar.sonar.SonarView;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaterniond;

/** Stub: see com/subsonar/README.txt. */
public final class SonarDisplay implements AutoCloseable {
    public final int size;
    public final ResourceLocation location;

    public SonarDisplay(int size) {
        throw new AssertionError();
    }

    public void render(Params p) { throw new AssertionError(); }

    @Override
    public void close() { throw new AssertionError(); }

    public static final class Params {
        public double now;
        public double gx;
        public double gy;
        public double gz;
        public float hx;
        public float hz;
        public Quaterniond orientation;
        public boolean headingUp;
        public double viewRange;
        public SonarView view;
        public boolean passiveMode;
        public float power;
        public boolean noSignal;
        public float warning;
        public double travelSeconds;
        public double fadeSeconds;
        public double contactFadeSeconds;
        public double sweepSeconds;
        public boolean chart;
        public float chartBrightness;
        public boolean showSilhouette;
        public boolean showGrid;
        public float zoom;
        public float gain;
        public float glitch;
        public double glitchAge;
        public boolean rebooting;
        public float rebootProgress;
    }
}
