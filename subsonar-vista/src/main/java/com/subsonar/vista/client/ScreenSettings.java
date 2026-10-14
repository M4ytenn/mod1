package com.subsonar.vista.client;

import com.subsonar.SonarConfig;
import com.subsonar.client.SonarScreen;
import java.lang.reflect.Field;
import org.jetbrains.annotations.Nullable;

/**
 * Zoom, gain, echo hold, heading-up and chart overlay are kept by the sonar screen in private static fields, so
 * they carry from one console to the next. A TV shows the sonar the same way; if a field cannot be read, it uses
 * the configured default.
 */
record ScreenSettings(boolean headingUp, float zoom, boolean chart, float gain, double echoHold) {
    @Nullable
    private static final Field HEADING_UP = field("headingUp");
    @Nullable
    private static final Field ZOOM = field("zoom");
    @Nullable
    private static final Field CHART = field("chart");
    @Nullable
    private static final Field GAIN = field("gain");
    @Nullable
    private static final Field ECHO_HOLD = field("echoHold");

    static ScreenSettings current() {
        return new ScreenSettings(
                readBoolean(HEADING_UP, SonarConfig.cget(SonarConfig.HEADING_UP_DEFAULT)),
                Math.max(0.05f, readFloat(ZOOM, 1f)),
                readBoolean(CHART, SonarConfig.cget(SonarConfig.PERSISTENT_CHART)),
                readFloat(GAIN, (float) SonarConfig.cget(SonarConfig.DEFAULT_GAIN)),
                readDouble(ECHO_HOLD, SonarConfig.cget(SonarConfig.ECHO_FADE_SECONDS)));
    }

    @Nullable
    private static Field field(String name) {
        try {
            Field field = SonarScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean readBoolean(@Nullable Field field, boolean fallback) {
        try {
            return field == null ? fallback : field.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private static float readFloat(@Nullable Field field, float fallback) {
        try {
            return field == null ? fallback : field.getFloat(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private static double readDouble(@Nullable Field field, double fallback) {
        try {
            return field == null ? fallback : field.getDouble(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }
}
