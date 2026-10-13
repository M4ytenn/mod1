package io.github.m4ytenn.hermodoors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.m4ytenn.hermodoors.HermoDoors;
import io.github.m4ytenn.hermodoors.door.DoorType;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity;
import io.github.m4ytenn.hermodoors.door.Stage;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the moving parts: the leaf (swinging on its hinges or sliding into the wall), the handwheel, the clamp
 * levers and the signal lamps. The static steel frame is an ordinary block model.
 *
 * <p>Geometry is given in door-local pixels: x to the right as seen from the front, y up, d (depth) from the
 * front face inwards. Lighting is sampled per block-sized tile just outside each face, so a big door is not lit
 * by a single block's light value.
 */
public class DoorRenderer implements BlockEntityRenderer<HermoDoorBlockEntity> {
    private static final ResourceLocation DZG_TEXTURE = HermoDoors.id("textures/entity/dzg_leaf.png");
    private static final ResourceLocation GATE_TEXTURE = HermoDoors.id("textures/entity/germozatvor_leaf.png");
    private static final float SWING_DEGREES = 95f;

    public DoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HermoDoorBlockEntity door, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = door.getLevel();
        if (level == null) {
            return;
        }
        DoorType type = door.type();
        Direction facing = door.facing();
        boolean dzg = !type.sliding;
        ResourceLocation texture = dzg ? DZG_TEXTURE : GATE_TEXTURE;
        Painter p = new Painter(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), level, door.getBlockPos(),
                dzg ? 128 : 256, dzg ? 64 : 128);

        p.push();
        p.translate(0.5f, 0, 0.5f);
        p.rotateY(-facing.toYRot());

        float progress = smooth(door.openProgress(partialTick));
        float[] leaf = type.leafBox();
        boolean mirrored = door.mirrored();

        if (dzg) {
            renderDzg(door, p, leaf, progress, mirrored, partialTick);
        } else {
            renderGate(door, p, leaf, progress, mirrored);
        }
        p.pop();
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    // ---------------------------------------------------------------- ДЗГ

    private void renderDzg(HermoDoorBlockEntity door, Painter p, float[] leaf, float progress, boolean mirrored, float partialTick) {
        DoorType type = door.type();
        float hingeX = mirrored ? leaf[3] : leaf[0];
        float angle = (mirrored ? 1 : -1) * SWING_DEGREES * progress;

        // Lamp on the lintel: dark, amber once the panel has power, green when the door is free.
        boolean power = Stage.POWER.in(door.completed()) || !Stage.POWER.in(door.stageMask());
        int lampU = door.isUnlocked() ? Dzg.LAMP_GREEN : power ? Dzg.LAMP_AMBER : Dzg.LAMP_OFF;
        boolean lit = lampU != Dzg.LAMP_OFF;
        p.box(type.widthPx() / 2f - 2, type.heightPx() - 2.5f, -1.5f, type.widthPx() / 2f + 2, type.heightPx() - 0.5f, 0,
                Dzg.uv(lampU, 0, 4, 4), lit);

        // Hinge knuckles on the frame.
        for (float ky : new float[] {6, 22, 38}) {
            p.box(hingeX - 1.5f, ky, -1.5f, hingeX + 1.5f, ky + 5, 0.5f, Dzg.uv(Dzg.STEEL, 0, 3, 5), false);
        }

        p.push();
        p.pivotY(hingeX, 0, angle);

        // Leaf slab.
        p.leaf(leaf, Dzg.FRONT, Dzg.BACK, Dzg.EDGE_V, Dzg.EDGE_H);

        // Rubber seal peeking out round the back edge.
        p.box(leaf[0] + 0.5f, leaf[1] + 0.5f, leaf[5], leaf[3] - 0.5f, leaf[4] - 0.5f, leaf[5] + 0.5f, Dzg.uv(Dzg.DARK, 0, 8, 8), false);

        // Handwheel on its stub shaft. While the door moves it spins as if someone were cranking it.
        float wheelX = (leaf[0] + leaf[3]) / 2f;
        float wheelY = (leaf[1] + leaf[4]) / 2f + 2;
        float wheelTurn = door.isUnlocked() ? door.wheelDisplay(partialTick) + progress * 540f : door.wheelDisplay(partialTick);
        p.box(wheelX - 1, wheelY - 1, leaf[2] - 2.5f, wheelX + 1, wheelY + 1, leaf[2], Dzg.uv(Dzg.STEEL, 0, 2, 2), false);
        p.push();
        // Opening turns the wheel anticlockwise as seen from the front, as on the panel.
        p.pivotZ(wheelX, wheelY, wheelTurn);
        wheel(p, wheelX, wheelY, leaf[2] - 3.5f, 7.5f, 4);
        p.pop();

        // Lever clamps along the leaf edge away from the hinge, plus top and bottom.
        int count = Math.max(4, door.spec().clamps.count);
        int freed = door.isUnlocked() || Stage.CLAMPS.in(door.completed()) || !Stage.CLAMPS.in(door.stageMask()) ? count : door.clampsFreed();
        float[][] slots = {{0.86f, 0.22f}, {0.86f, 0.78f}, {0.5f, 0.95f}, {0.5f, 0.05f}, {0.86f, 0.5f}, {0.18f, 0.95f}};
        for (int i = 0; i < count && i < slots.length; i++) {
            float sx = Mth.lerp(slots[i][0], leaf[0], leaf[3]);
            if (mirrored) {
                sx = leaf[0] + leaf[3] - sx;
            }
            float sy = Mth.lerp(slots[i][1], leaf[1], leaf[4]);
            float lockedAngle = slots[i][1] > 0.9f ? 90 : slots[i][1] < 0.1f ? -90 : (sx > wheelX ? 0 : 180);
            float a = i < freed ? lockedAngle + 90 : lockedAngle;
            p.push();
            p.pivotZ(sx, sy, a);
            p.box(sx - 1, sy - 1, leaf[2] - 1.5f, sx + 5, sy + 1, leaf[2], Dzg.uv(i < freed ? Dzg.STEEL : Dzg.RUST, 0, 6, 2), false);
            p.pop();
            p.box(sx - 1.5f, sy - 1.5f, leaf[2] - 0.5f, sx + 1.5f, sy + 1.5f, leaf[2], Dzg.uv(Dzg.DARK, 0, 3, 3), false);
        }
        p.pop();
    }

    /** Spoked handwheel facing the viewer, centred on (cx, cy) in the plane at depth d. */
    private static void wheel(Painter p, float cx, float cy, float d, float radius, int spokes) {
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            p.push();
            p.pivotZ(cx, cy, i * 360f / segments);
            float half = (float) (radius * Math.tan(Math.PI / segments)) + 0.15f;
            p.box(cx - half, cy + radius - 1.2f, d - 1.2f, cx + half, cy + radius + 0.3f, d, Dzg.uv(Dzg.RED, i % 4, 3, 2), false);
            p.pop();
        }
        for (int i = 0; i < spokes; i++) {
            p.push();
            p.pivotZ(cx, cy, i * 360f / spokes + 45);
            p.box(cx - 0.6f, cy + 1, d - 0.9f, cx + 0.6f, cy + radius - 1, d - 0.2f, Dzg.uv(Dzg.RED, 4, 2, 6), false);
            p.pop();
        }
        p.box(cx - 1.5f, cy - 1.5f, d - 1.5f, cx + 1.5f, cy + 1.5f, d + 0.2f, Dzg.uv(Dzg.STEEL, 2, 3, 3), false);
    }

    // ---------------------------------------------------------------- Гермозатвор

    private void renderGate(HermoDoorBlockEntity door, Painter p, float[] leaf, float progress, boolean mirrored) {
        DoorType type = door.type();
        // Far enough for the leading edge to clear the jamb.
        float travel = type.widthPx() - type.jambPx() - leaf[0];
        float offset = (mirrored ? -1 : 1) * travel * progress;
        int w = type.widthPx();
        int h = type.heightPx();

        // Hydraulic ram on the lintel; its rod follows a bracket on the gate's top edge.
        float bodyStart = mirrored ? w - 32 : 6;
        float bodyEnd = mirrored ? w - 6 : 32;
        float rodFixed = mirrored ? bodyStart : bodyEnd;
        float bracket = (mirrored ? bodyStart : bodyEnd) + offset;
        p.box(bodyStart, h - 5, -3.5f, bodyEnd, h - 1, -0.5f, Gate.uv(Gate.DARK, 0, 8, 4), false);
        p.box(Math.min(rodFixed, bracket), h - 3.6f, -2.6f, Math.max(rodFixed, bracket) + 0.01f, h - 2.4f, -1.4f, Gate.uv(Gate.CHROME, 0, 8, 1), false);

        // Signal lamps on the lintel: amber with power, green when the way is open.
        boolean power = Stage.POWER.in(door.completed()) || !Stage.POWER.in(door.stageMask());
        float lampX = mirrored ? 10 : w - 14;
        p.box(lampX, h - 4.5f, -1.5f, lampX + 3, h - 1.5f, 0, Gate.uv(power ? Gate.LAMP_AMBER : Gate.LAMP_OFF, 0, 3, 3), power);
        p.box(lampX + 4, h - 4.5f, -1.5f, lampX + 7, h - 1.5f, 0, Gate.uv(door.isUnlocked() ? Gate.LAMP_GREEN : Gate.LAMP_OFF, 0, 3, 3), door.isUnlocked());

        p.push();
        p.translate(offset / 16f, 0, 0);
        p.leaf(leaf, Gate.FRONT, Gate.BACK, Gate.EDGE_V, Gate.EDGE_H);
        // Ram bracket and two stiffening ribs proud of the face.
        float bx = mirrored ? bodyStart : bodyEnd;
        p.box(bx - 1.5f, leaf[4] - 2, leaf[2] - 3, bx + 1.5f, leaf[4] + 0.5f, leaf[2], Gate.uv(Gate.DARK, 0, 3, 3), false);
        for (float ry : new float[] {leaf[1] + 18, leaf[1] + 38}) {
            p.box(leaf[0] + 3, ry, leaf[2] - 1.5f, leaf[3] - 3, ry + 2, leaf[2], Gate.uv(Gate.STEEL, 0, 8, 2), false);
        }
        p.pop();
    }

    // ---------------------------------------------------------------- texture layouts

    /** UV layout of dzg_leaf.png (128 x 64). */
    static final class Dzg {
        static final int[] FRONT = {0, 0, 26, 43};
        static final int[] BACK = {26, 0, 26, 43};
        static final int[] EDGE_V = {52, 0, 4, 43};
        static final int[] EDGE_H = {0, 44, 26, 4};
        static final int RED = 56;
        static final int STEEL = 64;
        static final int RUST = 72;
        static final int DARK = 80;
        static final int LAMP_OFF = 88;
        static final int LAMP_AMBER = 92;
        static final int LAMP_GREEN = 96;

        static int[] uv(int u, int v, int w, int h) {
            return new int[] {u, v, w, h};
        }
    }

    /** UV layout of germozatvor_leaf.png (256 x 128). */
    static final class Gate {
        static final int[] FRONT = {0, 0, 62, 57};
        static final int[] BACK = {62, 0, 62, 57};
        static final int[] EDGE_V = {124, 0, 6, 57};
        static final int[] EDGE_H = {0, 58, 62, 6};
        static final int STEEL = 132;
        static final int DARK = 140;
        static final int CHROME = 148;
        static final int LAMP_OFF = 156;
        static final int LAMP_AMBER = 160;
        static final int LAMP_GREEN = 164;

        static int[] uv(int u, int v, int w, int h) {
            return new int[] {u, v, w, h};
        }
    }

    // ---------------------------------------------------------------- culling

    @Override
    public AABB getRenderBoundingBox(HermoDoorBlockEntity door) {
        return door.renderBounds();
    }

    @Override
    public boolean shouldRenderOffScreen(HermoDoorBlockEntity door) {
        // The door is far bigger than the block that owns it.
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Keeps the render pose and a parallel world-space matrix in step, so every face can look up the light
     * where it actually is (also mid-swing).
     */
    private static final class Painter {
        private final PoseStack pose;
        private final PoseStack world = new PoseStack();
        private final VertexConsumer vc;
        private final Level level;
        private final float texW;
        private final float texH;
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        private final Vector3f tmp = new Vector3f();

        Painter(PoseStack pose, VertexConsumer vc, Level level, BlockPos origin, int texW, int texH) {
            this.pose = pose;
            this.vc = vc;
            this.level = level;
            this.texW = texW;
            this.texH = texH;
            world.translate(origin.getX(), origin.getY(), origin.getZ());
        }

        void push() {
            pose.pushPose();
            world.pushPose();
        }

        void pop() {
            pose.popPose();
            world.popPose();
        }

        void translate(float x, float y, float z) {
            pose.translate(x, y, z);
            world.translate(x, y, z);
        }

        void rotateY(float degrees) {
            pose.mulPose(Axis.YP.rotationDegrees(degrees));
            world.mulPose(Axis.YP.rotationDegrees(degrees));
        }

        /** Rotate about a vertical axis through door-local x at the front face. */
        void pivotY(float xPx, float dPx, float degrees) {
            float x = xPx / 16f - 0.5f;
            float z = 0.5f - dPx / 16f;
            translate(x, 0, z);
            rotateY(degrees);
            translate(-x, 0, -z);
        }

        /** Rotate in the plane of the door about the point (x, y) (door-local pixels). */
        void pivotZ(float xPx, float yPx, float degrees) {
            float x = xPx / 16f - 0.5f;
            float y = yPx / 16f;
            translate(x, y, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(degrees));
            world.mulPose(Axis.ZP.rotationDegrees(degrees));
            translate(-x, -y, 0);
        }

        /** The leaf slab with its four distinct texture regions. */
        void leaf(float[] b, int[] front, int[] back, int[] edgeV, int[] edgeH) {
            float x0 = b[0] / 16f - 0.5f;
            float x1 = b[3] / 16f - 0.5f;
            float y0 = b[1] / 16f;
            float y1 = b[4] / 16f;
            float zf = 0.5f - b[2] / 16f;
            float zb = 0.5f - b[5] / 16f;
            tiledFace(x0, y0, x1, y1, zf, true, front);
            tiledFace(x0, y0, x1, y1, zb, false, back);
            edgeQuad(x1, y0, zf, x1, y0, zb, x1, y1, zb, x1, y1, zf, 1, 0, 0, edgeV);
            edgeQuad(x0, y0, zb, x0, y0, zf, x0, y1, zf, x0, y1, zb, -1, 0, 0, edgeV);
            edgeQuad(x0, y1, zf, x1, y1, zf, x1, y1, zb, x0, y1, zb, 0, 1, 0, edgeH);
            edgeQuad(x0, y0, zb, x1, y0, zb, x1, y0, zf, x0, y0, zf, 0, -1, 0, edgeH);
        }

        /** A big face split into block-sized tiles, each with its own light. */
        private void tiledFace(float x0, float y0, float x1, float y1, float z, boolean front, int[] uv) {
            int tilesX = Math.max(1, (int) Math.ceil((x1 - x0) - 0.01f));
            int tilesY = Math.max(1, (int) Math.ceil((y1 - y0) - 0.01f));
            for (int tx = 0; tx < tilesX; tx++) {
                for (int ty = 0; ty < tilesY; ty++) {
                    float ax = x0 + (x1 - x0) * tx / tilesX;
                    float bx = x0 + (x1 - x0) * (tx + 1) / tilesX;
                    float ay = y0 + (y1 - y0) * ty / tilesY;
                    float by = y0 + (y1 - y0) * (ty + 1) / tilesY;
                    // Texture is laid out as seen from that side: the back is mirrored left to right.
                    float u0 = uv[0] + uv[2] * (front ? (float) tx / tilesX : 1 - (float) (tx + 1) / tilesX);
                    float u1 = uv[0] + uv[2] * (front ? (float) (tx + 1) / tilesX : 1 - (float) tx / tilesX);
                    float v0 = uv[1] + uv[3] * (1 - (float) (ty + 1) / tilesY);
                    float v1 = uv[1] + uv[3] * (1 - (float) ty / tilesY);
                    // Sample just outside the door's own block layer: a shut door's blocks hold no light.
                    int light = lightAt((ax + bx) / 2, (ay + by) / 2, front ? 0.8f : -0.8f);
                    if (front) {
                        vertex(ax, ay, z, u0, v1, 0, 0, 1, light);
                        vertex(bx, ay, z, u1, v1, 0, 0, 1, light);
                        vertex(bx, by, z, u1, v0, 0, 0, 1, light);
                        vertex(ax, by, z, u0, v0, 0, 0, 1, light);
                    } else {
                        vertex(bx, ay, z, u0, v1, 0, 0, -1, light);
                        vertex(ax, ay, z, u1, v1, 0, 0, -1, light);
                        vertex(ax, by, z, u1, v0, 0, 0, -1, light);
                        vertex(bx, by, z, u0, v0, 0, 0, -1, light);
                    }
                }
            }
        }

        /**
         * Small box in door-local pixels, every face showing the same texture patch. {@code emissive} draws it at
         * full brightness (lit lamps).
         */
        void box(float x0p, float y0p, float d0p, float x1p, float y1p, float d1p, int[] uv, boolean emissive) {
            float x0 = x0p / 16f - 0.5f;
            float x1 = x1p / 16f - 0.5f;
            float y0 = y0p / 16f;
            float y1 = y1p / 16f;
            float zf = 0.5f - d0p / 16f;
            float zb = 0.5f - d1p / 16f;
            int light = emissive ? LightTexture.FULL_BRIGHT : lightAt((x0 + x1) / 2, (y0 + y1) / 2, zf + 0.55f);
            quad(x0, y0, zf, x1, y0, zf, x1, y1, zf, x0, y1, zf, 0, 0, 1, uv, light);
            quad(x1, y0, zb, x0, y0, zb, x0, y1, zb, x1, y1, zb, 0, 0, -1, uv, light);
            quad(x1, y0, zf, x1, y0, zb, x1, y1, zb, x1, y1, zf, 1, 0, 0, uv, light);
            quad(x0, y0, zb, x0, y0, zf, x0, y1, zf, x0, y1, zb, -1, 0, 0, uv, light);
            quad(x0, y1, zf, x1, y1, zf, x1, y1, zb, x0, y1, zb, 0, 1, 0, uv, light);
            quad(x0, y0, zb, x1, y0, zb, x1, y0, zf, x0, y0, zf, 0, -1, 0, uv, light);
        }

        private void edgeQuad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                float dx, float dy, float dz, float nx, float ny, float nz, int[] uv) {
            int light = lightAt((ax + cx) / 2 + nx * 0.55f, (ay + cy) / 2 + ny * 0.55f, (az + cz) / 2 + nz * 0.55f);
            quad(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, nx, ny, nz, uv, light);
        }

        private void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                float dx, float dy, float dz, float nx, float ny, float nz, int[] uv, int light) {
            float u0 = uv[0];
            float v0 = uv[1];
            float u1 = uv[0] + uv[2];
            float v1 = uv[1] + uv[3];
            vertex(ax, ay, az, u0, v1, nx, ny, nz, light);
            vertex(bx, by, bz, u1, v1, nx, ny, nz, light);
            vertex(cx, cy, cz, u1, v0, nx, ny, nz, light);
            vertex(dx, dy, dz, u0, v0, nx, ny, nz, light);
        }

        private void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
            PoseStack.Pose last = pose.last();
            vc.addVertex(last, x, y, z)
                    .setColor(255, 255, 255, 255)
                    .setUv(u / texW, v / texH)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(last, nx, ny, nz);
        }

        /** Light at a point given in the current (render) frame, looked up in the world. */
        private int lightAt(float x, float y, float z) {
            Matrix4f m = world.last().pose();
            m.transformPosition(x, y, z, tmp);
            probe.set(Mth.floor(tmp.x), Mth.floor(tmp.y), Mth.floor(tmp.z));
            return LevelRenderer.getLightColor(level, probe);
        }
    }
}
