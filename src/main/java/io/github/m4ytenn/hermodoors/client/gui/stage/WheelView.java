package io.github.m4ytenn.hermodoors.client.gui.stage;

import io.github.m4ytenn.hermodoors.client.gui.Art;
import io.github.m4ytenn.hermodoors.client.gui.DoorPanelScreen;
import io.github.m4ytenn.hermodoors.client.gui.Px;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity.PanelAction;
import io.github.m4ytenn.hermodoors.door.Stage;
import io.github.m4ytenn.hermodoors.puzzle.PuzzleSpec;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The handwheel. Grab the rim and turn it anticlockwise, steadily: rush it, especially through the tight spots,
 * and your grip slips and the wheel springs back.
 */
public class WheelView extends StageView {
    // Layout (stage-local)
    private static final float CX = 62;
    private static final float CY = 80;
    private static final int RING_SIZE = 112;
    private static final float RIM_INNER = 46f;
    private static final float GRAB_MIN = 10f;
    private static final float GRAB_MAX = 60f;
    private static final int COUNTER_X = 130;
    private static final int COUNTER_Y = 16;
    private static final float STRAIN_X = 144;
    private static final float STRAIN_Y = 70;

    // stage_wheel.png (512 x 256) sprites
    private static final int SHEET_W = 512;
    private static final int SHEET_H = 256;
    private static final int RING_U = 170;
    private static final int RING_V = 0;
    private static final int HUB_U = 284;
    private static final int HUB_V = 0;
    private static final int HUB_SIZE = 26;
    private static final int DIGITS_U = 312;
    private static final int DIGITS_V = 0;
    private static final int DIGIT_W = 7;
    private static final int DIGIT_H = 11;

    private final PuzzleSpec.Wheel wheel;
    private final Random random = new Random();
    private final float target;
    private float angle;
    private float prevAngle;
    private float pending;
    private boolean grabbing;
    private float lastMouseAngle;
    private float strain;
    private float prevStrain;
    private int slipTimer;
    private float ratchet;
    private int sentAngle = Integer.MIN_VALUE;

    public WheelView(DoorPanelScreen screen, PuzzleSpec spec) {
        super(screen, spec);
        this.wheel = spec.wheel;
        this.target = wheel.turns * 360f;
    }

    @Override
    public Stage stage() {
        return Stage.WHEEL;
    }

    @Override
    public void tick() {
        super.tick();
        prevAngle = angle;
        prevStrain = strain;
        if (slipTimer > 0) {
            slipTimer--;
        }
        if (finished()) {
            return;
        }
        float limit = wheel.speedLimit(angle);
        float step = Mth.clamp(pending, -limit, limit);
        float excess = Math.abs(pending) - limit;
        pending = 0;
        angle = Math.max(0, angle + step);
        // Forcing it builds strain over a few ticks (the gauge and a trembling wheel give warning); easing off
        // lets it drain away again.
        if (excess > 0) {
            strain += excess / limit * 0.12f;
        } else {
            strain = Math.max(0, strain - 0.06f);
        }
        if (strain >= 1f) {
            slip();
        }
        ratchet += Math.max(0, step);
        if (ratchet >= 30f) {
            ratchet -= 30f;
            Px.sound(SoundEvents.COMPARATOR_CLICK, 1.5f + random.nextFloat() * 0.2f, 0.25f);
        }
        if (ticks % 5 == 0 && Math.round(angle) != sentAngle) {
            sentAngle = Math.round(angle);
            action(PanelAction.WHEEL_ANGLE, sentAngle);
        }
        if (angle >= target) {
            angle = target;
            Px.sound(SoundEvents.IRON_DOOR_OPEN, 0.5f, 0.8f);
            Px.sound(SoundEvents.PISTON_EXTEND, 0.6f, 0.5f);
            action(PanelAction.WHEEL_ANGLE, Math.round(angle));
            finish(List.of());
        }
    }

    private void slip() {
        angle = Math.max(0, angle - 40f);
        prevAngle = angle;
        strain = 0;
        grabbing = false;
        slipTimer = 10;
        Px.sound(SoundEvents.CHAIN_BREAK, 0.7f, 0.6f);
        Px.sound(SoundEvents.ANVIL_LAND, 1.7f, 0.2f);
        action(PanelAction.WHEEL_SLIP, 0);
        shake(7);
    }

    private static float mouseAngle(double x, double y) {
        return (float) Math.toDegrees(Math.atan2(y - CY, x - CX));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || finished()) {
            return false;
        }
        double dx = x - CX;
        double dy = y - CY;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d >= GRAB_MIN && d <= GRAB_MAX) {
            grabbing = true;
            lastMouseAngle = mouseAngle(x, y);
            Px.sound(SoundEvents.CHAIN_STEP, 0.6f, 0.3f);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button) {
        if (!grabbing) {
            return false;
        }
        double dx = x - CX;
        double dy = y - CY;
        if (dx * dx + dy * dy < 16) {
            // Too close to the axle to tell which way the hand is moving.
            return true;
        }
        float a = mouseAngle(x, y);
        float delta = Mth.wrapDegrees(a - lastMouseAngle);
        lastMouseAngle = a;
        // Screen angles grow clockwise; opening is anticlockwise.
        pending -= delta;
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean was = grabbing;
        grabbing = false;
        return was;
    }

    @Override
    public void render(GuiGraphics g, int ox, int oy, int mouseX, int mouseY, float partialTick) {
        Art.blit(g, Art.WHEEL, ox, oy, 0, 0, DoorPanelScreen.STAGE_W, DoorPanelScreen.STAGE_H, SHEET_W, SHEET_H);

        float s = Mth.lerp(partialTick, prevStrain, strain);
        int jx = 0;
        int jy = 0;
        if (slipTimer > 0 || s > 0.6f) {
            jx = random.nextInt(3) - 1;
            jy = random.nextInt(3) - 1;
        }
        float cx = ox + CX + jx;
        float cy = oy + CY + jy;
        float visual = -Mth.lerp(partialTick, prevAngle, angle);

        Art.blit(g, Art.WHEEL, Math.round(cx) - RING_SIZE / 2, Math.round(cy) - RING_SIZE / 2, RING_U, RING_V, RING_SIZE, RING_SIZE, SHEET_W, SHEET_H);
        for (int k = 0; k < 4; k++) {
            float a = visual + k * 90f;
            Px.capsule(g, Px.px(cx, 11, a), Px.py(cy, 11, a), Px.px(cx, RIM_INNER + 1, a), Px.py(cy, RIM_INNER + 1, a), 3.2f,
                    Art.WHEEL_RED, Art.WHEEL_RED_LIGHT, Art.WHEEL_RED_DARK);
        }
        // Worn bare patch on one spoke and a white paint dab on the rim so the turning reads at a glance.
        Px.capsule(g, Px.px(cx, 30, visual), Px.py(cy, 30, visual), Px.px(cx, 38, visual), Px.py(cy, 38, visual), 1.6f,
                Art.STEEL, Art.STEEL_LIGHT, Art.STEEL);
        Px.disc(g, Px.px(cx, 51, visual + 45), Px.py(cy, 51, visual + 45), 2.2f, Art.PAINT_WHITE);
        Art.blit(g, Art.WHEEL, Math.round(cx) - HUB_SIZE / 2, Math.round(cy) - HUB_SIZE / 2, HUB_U, HUB_V, HUB_SIZE, HUB_SIZE, SHEET_W, SHEET_H);

        // Turn counter: two rolling drums, the units drum turns continuously with the wheel.
        float turns = Mth.lerp(partialTick, prevAngle, angle) / 360f;
        drum(g, ox + COUNTER_X, oy + COUNTER_Y, (float) Math.floor(turns / 10f) % 10f);
        drum(g, ox + COUNTER_X + DIGIT_W + 1, oy + COUNTER_Y, turns % 10f);
        Px.engrave(g, font, Px.stencil("/" + wheel.turns), ox + COUNTER_X + 2 * DIGIT_W + 4, oy + COUNTER_Y + 3, Art.ENGRAVE_CREAM);

        // Strain gauge.
        float sa = 135f + 270f * Mth.clamp(s, 0, 1.1f) / 1.1f;
        Px.capsule(g, ox + STRAIN_X, oy + STRAIN_Y, Px.px(ox + STRAIN_X, 12, sa), Px.py(oy + STRAIN_Y, 12, sa), 0.6f, Art.NEEDLE, Art.NEEDLE, Art.NEEDLE);
        Px.dot(g, Math.round(ox + STRAIN_X), Math.round(oy + STRAIN_Y), Art.NEEDLE_RED);
    }

    /** One drum of the mechanical counter; {@code value} 0..10, fractions roll the drum. */
    private static void drum(GuiGraphics g, int x, int y, float value) {
        int digit = (int) Math.floor(value) % 10;
        float frac = value - (float) Math.floor(value);
        // The strip holds 0..9 and then 0 again, so a rolling 9 can show the next 0 underneath it.
        int roll = frac > 0.85f ? Math.round((frac - 0.85f) / 0.15f * DIGIT_H) : 0;
        Art.blit(g, Art.WHEEL, x, y, DIGITS_U, DIGITS_V + digit * DIGIT_H + roll, DIGIT_W, DIGIT_H, SHEET_W, SHEET_H);
    }

    @Override
    @Nullable
    public Component tooltip(int x, int y) {
        float dx = x - CX;
        float dy = y - CY;
        if (dx * dx + dy * dy <= GRAB_MAX * GRAB_MAX) {
            return Component.translatable("tip.hermodoors.wheel.wheel");
        }
        if (Px.inside(x, y, COUNTER_X - 4, COUNTER_Y - 4, 34, 24)) {
            return Component.translatable("tip.hermodoors.wheel.counter", (int) (angle / 360f), wheel.turns);
        }
        float sx = x - STRAIN_X;
        float sy = y - STRAIN_Y;
        if (sx * sx + sy * sy <= 18 * 18) {
            return Component.translatable("tip.hermodoors.wheel.strain");
        }
        return null;
    }
}
