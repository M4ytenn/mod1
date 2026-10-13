package io.github.m4ytenn.hermodoors.door;

import org.jetbrains.annotations.Nullable;

/** The opening procedure steps, always performed in this order. */
public enum Stage {
    POWER("power"),
    PUMP("pump"),
    CLAMPS("clamps"),
    WHEEL("wheel");

    public static final int ALL = 0b1111;

    public final String id;
    public final int bit;

    Stage(String id) {
        this.id = id;
        this.bit = 1 << ordinal();
    }

    public String translationKey() {
        return "stage.hermodoors." + id;
    }

    public boolean in(int mask) {
        return (mask & bit) != 0;
    }

    /** The first stage of {@code mask} that is not yet in {@code completed}, or null when the door is solved. */
    @Nullable
    public static Stage next(int mask, int completed) {
        for (Stage stage : values()) {
            if (stage.in(mask) && !stage.in(completed)) {
                return stage;
            }
        }
        return null;
    }

    @Nullable
    public static Stage byIndex(int index) {
        Stage[] values = values();
        return index >= 0 && index < values.length ? values[index] : null;
    }
}
