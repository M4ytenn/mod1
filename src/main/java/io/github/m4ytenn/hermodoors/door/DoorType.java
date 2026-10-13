package io.github.m4ytenn.hermodoors.door;

/**
 * The two door models. Sizes are in blocks; geometry below is in pixels (1/16 block) in door-local space:
 * x runs left to right as seen by someone standing in front of the door, y runs up, and z runs from the
 * front face (z = 0) to the back face (z = 16).
 */
public enum DoorType {
    /** Civil-defense shelter door (дверь защитно-герметическая). Swings out on hinges. */
    DZG("dzg", 2, 3, 70, false, "ДЗГ-2"),
    /** Metro tunnel gate (защитно-герметический затвор). Slides sideways into the wall. */
    GERMOZATVOR("germozatvor", 4, 4, 180, true, "ЗГ-4");

    public final String id;
    public final int width;
    public final int height;
    /** Ticks for a full open or close stroke. */
    public final int animTicks;
    public final boolean sliding;
    /** Factory designation stencilled on the door and printed in its passport. */
    public final String designation;

    DoorType(String id, int width, int height, int animTicks, boolean sliding, String designation) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.animTicks = animTicks;
        this.sliding = sliding;
        this.designation = designation;
    }

    public int widthPx() {
        return width * 16;
    }

    public int heightPx() {
        return height * 16;
    }

    /** Width of the side jambs. */
    public int jambPx() {
        return sliding ? 4 : 3;
    }

    /** Height of the top beam (lintel). */
    public int headPx() {
        return sliding ? 6 : 3;
    }

    /** Height of the threshold or floor track. */
    public int sillPx() {
        return sliding ? 1 : 2;
    }

    /** Leaf slab, closed position: {x0, y0, z0, x1, y1, z1}. */
    public float[] leafBox() {
        if (sliding) {
            return new float[] {1, sillPx(), 5, widthPx() - 1, heightPx() - headPx(), 11};
        }
        return new float[] {jambPx(), sillPx(), 1, widthPx() - jambPx(), heightPx() - headPx(), 5};
    }

    public String translationKey() {
        return "block.hermodoors." + (this == DZG ? "dzg_door" : "germozatvor");
    }
}
