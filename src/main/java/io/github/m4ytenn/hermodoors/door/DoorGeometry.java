package io.github.m4ytenn.hermodoors.door;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Static frame geometry shared by collision shapes and the renderer. Boxes are {x0, y0, z0, x1, y1, z1} in
 * door-local pixels (see {@link DoorType}). The block models in assets are generated from the same numbers.
 */
public final class DoorGeometry {
    private DoorGeometry() {}

    public static List<float[]> frameBoxes(DoorType type) {
        List<float[]> boxes = new ArrayList<>();
        int w = type.widthPx();
        int h = type.heightPx();
        int jamb = type.jambPx();
        int head = type.headPx();
        int sill = type.sillPx();
        if (type.sliding) {
            float[] leaf = type.leafBox();
            // Jambs are split front/back, leaving a slot the gate slides through.
            boxes.add(new float[] {0, 0, 0, jamb, h - head, leaf[2]});
            boxes.add(new float[] {0, 0, leaf[5], jamb, h - head, 16});
            boxes.add(new float[] {w - jamb, 0, 0, w, h - head, leaf[2]});
            boxes.add(new float[] {w - jamb, 0, leaf[5], w, h - head, 16});
            boxes.add(new float[] {0, h - head, 0, w, h, 16});
            boxes.add(new float[] {jamb, 0, 0, w - jamb, sill, 16});
        } else {
            boxes.add(new float[] {0, 0, 0, jamb, h, 16});
            boxes.add(new float[] {w - jamb, 0, 0, w, h, 16});
            boxes.add(new float[] {jamb, h - head, 0, w - jamb, h, 16});
            boxes.add(new float[] {jamb, 0, 0, w - jamb, sill, 16});
        }
        return boxes;
    }

    /** Collision/outline shape of one part block, already rotated to world space for {@code facing}. */
    public static VoxelShape partShape(DoorType type, Direction facing, int px, int py, boolean open) {
        List<float[]> boxes = new ArrayList<>(frameBoxes(type));
        if (!open) {
            boxes.add(type.leafBox());
        }
        VoxelShape shape = Shapes.empty();
        float cx = px * 16;
        float cy = py * 16;
        for (float[] b : boxes) {
            float x0 = Math.max(b[0], cx) - cx;
            float x1 = Math.min(b[3], cx + 16) - cx;
            float y0 = Math.max(b[1], cy) - cy;
            float y1 = Math.min(b[4], cy + 16) - cy;
            if (x1 <= x0 || y1 <= y0) {
                continue;
            }
            shape = Shapes.or(shape, rotatedBox(facing, x0, y0, b[2], x1, y1, b[5]));
        }
        return shape.optimize();
    }

    /** Converts a cell-local box (x right, z front-to-back) to a world-aligned block box for the given facing. */
    public static VoxelShape rotatedBox(Direction facing, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (facing) {
            case NORTH -> Block.box(16 - x1, y0, z0, 16 - x0, y1, z1);
            case SOUTH -> Block.box(x0, y0, 16 - z1, x1, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, x0, z1, y1, x1);
            default -> Block.box(16 - z1, y0, 16 - x1, 16 - z0, y1, 16 - x0);
        };
    }
}
