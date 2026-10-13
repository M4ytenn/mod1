package io.github.m4ytenn.hermodoors.client.gui.stage;

import io.github.m4ytenn.hermodoors.client.gui.Art;
import io.github.m4ytenn.hermodoors.client.gui.DoorPanelScreen;
import io.github.m4ytenn.hermodoors.client.gui.Px;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity.PanelAction;
import io.github.m4ytenn.hermodoors.door.Stage;
import io.github.m4ytenn.hermodoors.puzzle.PuzzleSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Seized wedge clamps. Rock the rusted clamp lever and hit it with the sledge exactly when it lines up with
 * the chalk mark. A clean blow moves it, a bad one jams it tighter.
 */
public class ClampsView extends StageView {
    // Layout (stage-local)
    private static final int DIAGRAM_X = 4;
    private static final int DIAGRAM_Y = 4;
    private static final int[][] SLOTS = {{46, 22}, {46, 66}, {28, 11}, {28, 79}, {46, 44}, {13, 11}};
    private static final int CLOSEUP_X = 64;
    private static final int CLOSEUP_Y = 4;
    private static final int CLOSEUP_W = 98;
    private static final int CLOSEUP_H = 104;
    private static final float HUB_X = 108;
    private static final float HUB_Y = 94;
    private static final float LEVER_LEN = 66;
    private static final float SWING = 30f;
    private static final float SCALE_R = 74f;
    private static final int TALLY_X = 98;
    private static final int TALLY_Y = 115;

    // stage_clamps.png sprites
    private static final int HAMMER_UP_U = 168;
    private static final int HAMMER_V = 0;
    private static final int HAMMER_DOWN_U = 200;
    private static final int HAMMER_W = 30;
    private static final int HAMMER_H = 26;
    private static final int KNOB_U = 168;
    private static final int KNOB_V = 48;
    private static final int HUB_U = 176;
    private static final int HUB_V = 48;
    private static final int MARK_U = 190;
    private static final int MARK_V = 48;
    private static final int ICON_SEIZED_U = 168;
    private static final int ICON_LOOSE_U = 175;
    private static final int ICON_FREE_U = 182;
    private static final int ICON_V = 64;
    private static final int CIRCLE_U = 190;
    private static final int CIRCLE_V = 64;

    private final PuzzleSpec.Clamps clamps;
    private final int[] hitsLeft;
    private final Random random = new Random();
    private int current;
    private int blow;
    private float time;
    private float prevTime;
    private float periodScale = 1f;
    private int hammerTimer;
    private int cooldown;
    private int freedTimer;
    private int missFlash;
    private final List<float[]> flakes = new ArrayList<>();

    public ClampsView(DoorPanelScreen screen, PuzzleSpec spec, int alreadyFreed) {
        super(screen, spec);
        this.clamps = spec.clamps;
        this.hitsLeft = clamps.hits.clone();
        this.current = Math.min(alreadyFreed, clamps.count);
        for (int i = 0; i < current; i++) {
            hitsLeft[i] = 0;
        }
    }

    @Override
    public Stage stage() {
        return Stage.CLAMPS;
    }

    /** Stage-local point inside the close-up; used by the development screenshot tour. */
    public static int[] strikePoint() {
        return new int[] {CLOSEUP_X + CLOSEUP_W / 2, CLOSEUP_Y + CLOSEUP_H / 2};
    }

    private float leverPos(float t) {
        return clamps.leverPosition(t, periodScale);
    }

    private static float swingAngle(float pos) {
        return -SWING + 2 * SWING * pos;
    }

    @Override
    public void tick() {
        super.tick();
        prevTime = time;
        if (freedTimer == 0 && !finished()) {
            time++;
        }
        if (hammerTimer > 0) {
            hammerTimer--;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (missFlash > 0) {
            missFlash--;
        }
        if (freedTimer > 0 && --freedTimer == 0) {
            current++;
            blow = 0;
            periodScale = 1f;
        }
        if (current >= clamps.count && freedTimer == 0 && !finished()) {
            // Also covers reopening the panel after every clamp was already knocked loose.
            finish(List.of());
        }
        for (int i = flakes.size() - 1; i >= 0; i--) {
            float[] f = flakes.get(i);
            f[0] += f[2];
            f[1] += f[3];
            f[3] += 0.35f;
            if (f[1] > CLOSEUP_Y + CLOSEUP_H) {
                flakes.remove(i);
            }
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || finished() || current >= clamps.count) {
            return false;
        }
        if (!Px.inside(x, y, CLOSEUP_X, CLOSEUP_Y, CLOSEUP_W, CLOSEUP_H)) {
            return false;
        }
        if (cooldown > 0 || freedTimer > 0) {
            return true;
        }
        hammerTimer = 6;
        cooldown = 7;
        float pos = leverPos(time);
        float mark = clamps.markPosition(current, blow);
        if (Math.abs(pos - mark) <= clamps.markHalfWidth) {
            hitsLeft[current]--;
            Px.sound(SoundEvents.ANVIL_PLACE, 1.25f + random.nextFloat() * 0.15f, 0.55f);
            action(PanelAction.HAMMER_HIT, current);
            spawnFlakes(pos, 6);
            shake(3);
            if (hitsLeft[current] <= 0) {
                freedTimer = 14;
                Px.sound(SoundEvents.IRON_TRAPDOOR_OPEN, 0.6f, 0.7f);
                Px.sound(SoundEvents.CHAIN_BREAK, 0.8f, 0.5f);
                action(PanelAction.CLAMP_FREED, current + 1);
            }
        } else {
            hitsLeft[current] = Math.min(hitsLeft[current] + 1, clamps.hits[current] + 2);
            periodScale = Math.max(0.7f, periodScale * 0.93f);
            missFlash = 8;
            Px.sound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 0.6f, 0.45f);
            action(PanelAction.HAMMER_MISS, current);
            spawnFlakes(pos, 2);
            shake(6);
        }
        blow++;
        return true;
    }

    private void spawnFlakes(float pos, int count) {
        float a = swingAngle(pos);
        float bx = Px.px(HUB_X, 20, a - 90);
        float by = Px.py(HUB_Y, 20, a - 90);
        for (int i = 0; i < count; i++) {
            flakes.add(new float[] {bx, by, (random.nextFloat() - 0.5f) * 2.5f, -1.5f - random.nextFloat() * 1.5f});
        }
    }

    @Override
    public void render(GuiGraphics g, int ox, int oy, int mouseX, int mouseY, float partialTick) {
        Art.blit(g, Art.CLAMPS, ox, oy, 0, 0, DoorPanelScreen.STAGE_W, DoorPanelScreen.STAGE_H);

        // Door diagram: state of every clamp, the one being worked circled in chalk.
        for (int i = 0; i < clamps.count; i++) {
            int sx = ox + DIAGRAM_X + SLOTS[i][0];
            int sy = oy + DIAGRAM_Y + SLOTS[i][1];
            int u = i < current || (i == current && freedTimer > 0) ? ICON_FREE_U
                    : i == current && hitsLeft[i] < clamps.hits[i] ? ICON_LOOSE_U : ICON_SEIZED_U;
            Art.blit(g, Art.CLAMPS, sx - 3, sy - 3, u, ICON_V, 7, 7);
            if (i == current && current < clamps.count) {
                Art.blit(g, Art.CLAMPS, sx - 6, sy - 6, CIRCLE_U, CIRCLE_V, 13, 13);
            }
        }

        // Close-up. The chalk mark sits on the painted scale where the blow has to land.
        if (current < clamps.count) {
            float mark = clamps.markPosition(current, blow);
            float ma = swingAngle(mark) - 90;
            int mx = Math.round(Px.px(ox + HUB_X, SCALE_R, ma));
            int my = Math.round(Px.py(oy + HUB_Y, SCALE_R, ma));
            Art.blit(g, Art.CLAMPS, mx - 3, my - 4, MARK_U + (blow % 2) * 6, MARK_V, 6, 8);
            // Width of the target, as a faint chalk smear along the scale.
            float half = clamps.markHalfWidth * 2 * SWING;
            Px.arc(g, ox + HUB_X, oy + HUB_Y, SCALE_R + 4, SCALE_R + 5.5f, ma - half, ma + half, Px.withAlpha(0xE9E7DF, 0.6f));
        }

        float t = prevTime + (time - prevTime) * partialTick;
        float pos = freedTimer > 0 ? 1f : leverPos(t);
        float angle = swingAngle(pos) - 90;
        boolean done = current >= clamps.count || freedTimer > 0;
        int base = done ? Art.STEEL : missFlash > 0 ? Art.RUST_DARK : Art.RUST;
        int light = done ? Art.STEEL_LIGHT : Art.RUST_LIGHT;
        int dark = done ? Art.STEEL_DARK : Art.RUST_DARK;
        float tipX = Px.px(ox + HUB_X, LEVER_LEN, angle);
        float tipY = Px.py(oy + HUB_Y, LEVER_LEN, angle);
        Px.capsule(g, ox + HUB_X, oy + HUB_Y, tipX, tipY, 3.0f, base, light, dark);
        Art.blit(g, Art.CLAMPS, Math.round(tipX) - 4, Math.round(tipY) - 4, KNOB_U, KNOB_V, 8, 8);
        Art.blit(g, Art.CLAMPS, Math.round(ox + HUB_X) - 7, Math.round(oy + HUB_Y) - 7, HUB_U, HUB_V, 14, 14);

        for (float[] f : flakes) {
            Px.dot(g, ox + Math.round(f[0]), oy + Math.round(f[1]), Art.RUST_LIGHT);
        }

        boolean striking = hammerTimer > 2;
        Art.blit(g, Art.CLAMPS, ox + (striking ? 116 : 130), oy + (striking ? 74 : 64), striking ? HAMMER_DOWN_U : HAMMER_UP_U, HAMMER_V, HAMMER_W, HAMMER_H);

        // Chalk tally of the blows this clamp still needs.
        if (current < clamps.count) {
            for (int i = 0; i < hitsLeft[current]; i++) {
                int tx = ox + TALLY_X + i * 6;
                int lean = (Px.hash(current * 31 + i, 3) & 1);
                Px.line(g, tx + lean, oy + TALLY_Y, tx, oy + TALLY_Y + 12, Art.CHALK);
                Px.line(g, tx + lean + 1, oy + TALLY_Y + 1, tx + 1, oy + TALLY_Y + 11, Px.withAlpha(0xE9E7DF, 0.5f));
            }
        }
        int shownClamp = Math.min(current + 1, clamps.count);
        Px.engrave(g, font, Px.stencil("ЗАТВОР " + shownClamp + "/" + clamps.count), ox + 70, oy + 136, Art.CHALK);
    }

    @Override
    @Nullable
    public Component tooltip(int x, int y) {
        if (Px.inside(x, y, CLOSEUP_X, CLOSEUP_Y, CLOSEUP_W, CLOSEUP_H)) {
            return Component.translatable("tip.hermodoors.clamps.lever");
        }
        if (Px.inside(x, y, DIAGRAM_X, DIAGRAM_Y, 56, 86)) {
            return Component.translatable("tip.hermodoors.clamps.diagram", Math.min(current, clamps.count), clamps.count);
        }
        if (Px.inside(x, y, 64, 112, 98, 36)) {
            return Component.translatable("tip.hermodoors.clamps.tally");
        }
        return null;
    }
}
