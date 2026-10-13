package io.github.m4ytenn.hermodoors.client.gui.stage;

import io.github.m4ytenn.hermodoors.client.gui.Art;
import io.github.m4ytenn.hermodoors.client.gui.DoorPanelScreen;
import io.github.m4ytenn.hermodoors.client.gui.Px;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity.PanelAction;
import io.github.m4ytenn.hermodoors.door.Stage;
import io.github.m4ytenn.hermodoors.puzzle.PuzzleSpec;
import io.github.m4ytenn.hermodoors.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Hand pump and manometer. Work the lever to raise the pressure and keep the needle inside the green band
 * until the time relay runs out. The system leaks, and overdoing it lifts the relief valve.
 */
public class PumpView extends StageView {
    // Layout (stage-local)
    private static final float GAUGE_X = 50;
    private static final float GAUGE_Y = 56;
    private static final float SCALE_START = 135f;
    private static final float SCALE_SWEEP = 270f;
    private static final float PIVOT_X = 140;
    private static final float PIVOT_Y = 60;
    private static final float LEVER_LEN = 40;
    /** Lever angles, screen degrees: up is pointing left and a little up, down is left and down. */
    private static final float LEVER_UP = 205f;
    private static final float LEVER_DOWN = 150f;
    private static final int RELIEF_X = 148;
    private static final int RELIEF_Y = 6;
    private static final float RELAY_X = 26;
    private static final float RELAY_Y = 122;
    private static final int BLEED_X = 60;
    private static final int BLEED_Y = 114;
    private static final int CYLINDER_X = 134;
    private static final int CYLINDER_TOP = 70;

    // stage_pump.png sprites
    private static final int CAP_CLOSED_U = 168;
    private static final int CAP_OPEN_U = 180;
    private static final int CAP_V = 0;
    private static final int CAP_W = 12;
    private static final int CAP_H = 10;
    private static final int KNOB_U = 168;
    private static final int KNOB_V = 24;
    private static final int BLEED_U = 168;
    private static final int BLEED_V = 34;
    private static final int BLEED_SIZE = 18;
    private static final int GAUGE_CAP_U = 222;
    private static final int GAUGE_CAP_V = 0;

    private final PuzzleSpec.Pump pump;
    private final Random random = new Random();
    private float pressure = 0.4f;
    private float needle = 0.4f;
    private float prevNeedle = 0.4f;
    private float hold;
    private float lever = LEVER_UP;
    private float prevLever = LEVER_UP;
    private boolean dragging;
    private boolean strokeArmed = true;
    private boolean bleeding;
    private int bleedFrame;
    private int reliefTimer;
    private final List<float[]> puffs = new ArrayList<>();

    public PumpView(DoorPanelScreen screen, PuzzleSpec spec) {
        super(screen, spec);
        this.pump = spec.pump;
    }

    @Override
    public Stage stage() {
        return Stage.PUMP;
    }

    private static float gaugeAngle(float pressure) {
        return SCALE_START + SCALE_SWEEP * Mth.clamp(pressure, 0, PuzzleSpec.Pump.SCALE_MAX) / PuzzleSpec.Pump.SCALE_MAX;
    }

    @Override
    public void tick() {
        super.tick();
        prevNeedle = needle;
        prevLever = lever;
        if (finished()) {
            return;
        }
        pressure -= pump.leak * pressure + 0.002f;
        if (bleeding) {
            pressure -= 0.12f;
            bleedFrame++;
            if (ticks % 8 == 0) {
                Px.sound(ModSounds.PANEL_HISS.get(), 1.6f, 0.15f);
            }
        }
        pressure = Math.max(0, pressure);
        if (pressure > PuzzleSpec.Pump.RELIEF) {
            pressure *= 0.45f;
            reliefTimer = 30;
            Px.sound(ModSounds.PANEL_HISS.get(), 1.0f, 0.8f);
            action(PanelAction.RELIEF, 0);
            shake(5);
        }
        if (reliefTimer > 0) {
            reliefTimer--;
            if (reliefTimer > 12 && ticks % 2 == 0) {
                puffs.add(new float[] {RELIEF_X + 6 + random.nextFloat() * 2, RELIEF_Y + 2, 0});
            }
        }
        for (int i = puffs.size() - 1; i >= 0; i--) {
            float[] puff = puffs.get(i);
            puff[0] += 0.3f + random.nextFloat() * 0.3f;
            puff[1] -= 0.7f;
            puff[2]++;
            if (puff[2] > 16) {
                puffs.remove(i);
            }
        }
        // The needle chases the real pressure with a little inertia and some pump vibration.
        float jitter = pressure > 0.5f ? (random.nextFloat() - 0.5f) * 0.08f : 0;
        needle += (pressure - needle) * 0.35f + jitter;

        boolean inBand = pressure >= pump.bandLow && pressure <= pump.bandHigh;
        hold = inBand ? hold + 1 : Math.max(0, hold - 0.7f);
        if (hold >= pump.holdTicks) {
            Px.sound(SoundEvents.COMPARATOR_CLICK, 0.6f, 0.6f);
            finish(List.of());
        }
    }

    private float knobX(float angle) {
        return Px.px(PIVOT_X, LEVER_LEN, angle);
    }

    private float knobY(float angle) {
        return Px.py(PIVOT_Y, LEVER_LEN, angle);
    }

    private boolean onLever(double x, double y) {
        float kx = knobX(lever);
        float ky = knobY(lever);
        double dx = x - kx;
        double dy = y - ky;
        if (dx * dx + dy * dy <= 49) {
            return true;
        }
        // Anywhere along the handle works too.
        float lx = kx - PIVOT_X;
        float ly = ky - PIVOT_Y;
        double t = Mth.clamp(((x - PIVOT_X) * lx + (y - PIVOT_Y) * ly) / (lx * lx + ly * ly), 0.3, 1);
        double cx = PIVOT_X + lx * t - x;
        double cy = PIVOT_Y + ly * t - y;
        return cx * cx + cy * cy <= 16;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || finished()) {
            return false;
        }
        if (onLever(x, y)) {
            dragging = true;
            return true;
        }
        if (Px.inside(x, y, BLEED_X, BLEED_Y, BLEED_SIZE, BLEED_SIZE)) {
            bleeding = true;
            Px.sound(SoundEvents.LEVER_CLICK, 1.4f, 0.3f);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button) {
        if (!dragging) {
            return false;
        }
        float angle = (float) Math.toDegrees(Math.atan2(y - PIVOT_Y, x - PIVOT_X));
        if (angle < 0) {
            angle += 360f;
        }
        // Behind the pivot the angle wraps; pin it to whichever end is closer.
        if (angle < 90f || angle > 270f) {
            angle = angle > 270f ? LEVER_UP : LEVER_DOWN;
        }
        float target = Mth.clamp(angle, LEVER_DOWN, LEVER_UP);
        float moved = lever - target;
        if (moved > 0) {
            // Pressure is made on the down stroke only.
            pressure += pump.strokeGain(pressure, moved / (LEVER_UP - LEVER_DOWN));
        }
        lever = target;
        if (lever <= LEVER_DOWN + 4 && strokeArmed) {
            strokeArmed = false;
            Px.sound(SoundEvents.PISTON_CONTRACT, 1.5f, 0.35f);
            action(PanelAction.PUMP_STROKE, 0);
        } else if (lever >= LEVER_UP - 6 && !strokeArmed) {
            strokeArmed = true;
            Px.sound(SoundEvents.PISTON_EXTEND, 1.9f, 0.15f);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean used = dragging || bleeding;
        dragging = false;
        bleeding = false;
        return used;
    }

    @Override
    public void render(GuiGraphics g, int ox, int oy, int mouseX, int mouseY, float partialTick) {
        Art.blit(g, Art.PUMP, ox, oy, 0, 0, DoorPanelScreen.STAGE_W, DoorPanelScreen.STAGE_H);
        float gx = ox + GAUGE_X;
        float gy = oy + GAUGE_Y;

        // Operating band, painted on the dial face (it differs per door, so it is not baked into the sheet).
        Px.arc(g, gx, gy, 27.5f, 31f, gaugeAngle(pump.bandLow), gaugeAngle(pump.bandHigh), Art.GAUGE_GREEN);

        float shown = Mth.lerp(partialTick, prevNeedle, needle);
        float a = gaugeAngle(shown);
        Px.capsule(g, Px.px(gx, -5, a), Px.py(gy, -5, a), Px.px(gx, 31, a), Px.py(gy, 31, a), 0.75f, Art.NEEDLE, Art.NEEDLE, Art.NEEDLE);
        Px.line(g, Math.round(Px.px(gx, 24, a)), Math.round(Px.py(gy, 24, a)), Math.round(Px.px(gx, 31, a)), Math.round(Px.py(gy, 31, a)), Art.NEEDLE_RED);
        Art.blit(g, Art.PUMP, (int) gx - 3, (int) gy - 3, GAUGE_CAP_U, GAUGE_CAP_V, 7, 7);

        // Relief valve and its steam.
        Art.blit(g, Art.PUMP, ox + RELIEF_X, oy + RELIEF_Y - (reliefTimer > 18 ? 2 : 0), reliefTimer > 18 ? CAP_OPEN_U : CAP_CLOSED_U, CAP_V, CAP_W, CAP_H);
        for (float[] puff : puffs) {
            float life = puff[2] / 16f;
            Px.disc(g, ox + puff[0], oy + puff[1], 1.5f + life * 3f, Px.withAlpha(0xE8E8E2, 0.55f * (1 - life)));
        }

        // Time relay: the pointer creeps round while the pressure holds.
        float relay = Mth.clamp(hold / pump.holdTicks, 0, 1);
        float ra = 135f + 270f * relay;
        Px.line(g, Math.round(ox + RELAY_X), Math.round(oy + RELAY_Y),
                Math.round(Px.px(ox + RELAY_X, 11, ra)), Math.round(Px.py(oy + RELAY_Y, 11, ra)), Art.NEEDLE_RED);
        Px.dot(g, Math.round(ox + RELAY_X), Math.round(oy + RELAY_Y), Art.NEEDLE);

        // Bleed valve handwheel.
        Art.blit(g, Art.PUMP, ox + BLEED_X, oy + BLEED_Y, BLEED_U + ((bleedFrame / 3) % 3) * BLEED_SIZE, BLEED_V, BLEED_SIZE, BLEED_SIZE);

        // Pump: piston rod rides with the lever, then the handle and its knob.
        float la = Mth.lerp(partialTick, prevLever, lever);
        float linkX = Px.px(ox + PIVOT_X, 9, la);
        float linkY = Px.py(oy + PIVOT_Y, 9, la);
        Px.capsule(g, linkX, linkY, ox + CYLINDER_X + 5, oy + CYLINDER_TOP - 2, 1.0f, Art.STEEL_LIGHT, 0xFFD9DCDC, Art.STEEL);
        float kx = Px.px(ox + PIVOT_X, LEVER_LEN, la);
        float ky = Px.py(oy + PIVOT_Y, LEVER_LEN, la);
        Px.capsule(g, ox + PIVOT_X, oy + PIVOT_Y, kx, ky, 2.2f, Art.STEEL, Art.STEEL_LIGHT, Art.STEEL_DARK);
        Px.disc(g, ox + PIVOT_X, oy + PIVOT_Y, 3.5f, Art.STEEL_DARK);
        Px.disc(g, ox + PIVOT_X, oy + PIVOT_Y, 1.5f, Art.STEEL_LIGHT);
        Art.blit(g, Art.PUMP, Math.round(kx) - 4, Math.round(ky) - 4, KNOB_U, KNOB_V, 8, 8);
    }

    @Override
    @Nullable
    public Component tooltip(int x, int y) {
        float dx = x - GAUGE_X;
        float dy = y - GAUGE_Y;
        if (dx * dx + dy * dy <= 40 * 40) {
            return Component.translatable("tip.hermodoors.pump.gauge", String.format("%.1f", pump.bandLow), String.format("%.1f", pump.bandHigh));
        }
        if (onLever(x, y) || Px.inside(x, y, CYLINDER_X - 2, CYLINDER_TOP, 16, 74)) {
            return Component.translatable("tip.hermodoors.pump.lever");
        }
        if (Px.inside(x, y, BLEED_X, BLEED_Y, BLEED_SIZE, BLEED_SIZE + 8)) {
            return Component.translatable("tip.hermodoors.pump.bleed");
        }
        if (Px.inside(x, y, RELIEF_X - 2, RELIEF_Y - 2, 22, 26)) {
            return Component.translatable("tip.hermodoors.pump.relief");
        }
        float rx = x - RELAY_X;
        float ry = y - RELAY_Y;
        if (rx * rx + ry * ry <= 18 * 18) {
            return Component.translatable("tip.hermodoors.pump.relay");
        }
        return null;
    }
}
