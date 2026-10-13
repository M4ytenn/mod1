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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Breaker panel. Close the main knife switch, then close the breakers so that each one is only closed after
 * the breaker feeding it, as traced on the wiring diagram glued inside the cabinet. Closing one out of turn
 * trips everything back out.
 */
public class PowerView extends StageView {
    // Layout (stage-local)
    private static final int KNIFE_X = 6;
    private static final int KNIFE_Y = 3;
    private static final int KNIFE_W = 28;
    private static final int KNIFE_H = 52;
    private static final int MAINS_LAMP_X = 6;
    private static final int ALARM_LAMP_X = 24;
    private static final int LAMP_Y = 57;
    private static final int BREAKER_AREA_X = 40;
    private static final int BREAKER_AREA_W = 122;
    private static final int BREAKER_Y = 16;
    private static final int BREAKER_W = 16;
    private static final int BREAKER_H = 28;
    private static final int BREAKER_PITCH = 20;
    private static final int CARD_X = 4;
    private static final int CARD_Y = 75;
    private static final int CARD_W = 158;
    private static final int CARD_H = 74;

    // stage_power.png sprites
    private static final int KNIFE_OFF_U = 168;
    private static final int KNIFE_MID_U = 196;
    private static final int KNIFE_ON_U = 224;
    private static final int BREAKER_OPEN_U = 168;
    private static final int BREAKER_CLOSED_U = 184;
    private static final int BREAKER_TRIPPED_U = 200;
    private static final int BREAKER_V = 60;
    private static final int SPARK_U = 168;
    private static final int SPARK_V = 90;
    private static final int SPARK_SIZE = 12;

    private final PuzzleSpec.Power power;
    private final boolean[] closed;
    private final List<Integer> order = new ArrayList<>();
    private final int seed;
    private boolean mainOn;
    private int knifeAnim;
    private int tripTimer;
    private int sparkAt = -1;
    private int sparkTimer;
    private int refusedAt = -1;
    private int refusedTimer;

    public PowerView(DoorPanelScreen screen, PuzzleSpec spec, int seed) {
        super(screen, spec);
        this.power = spec.power;
        this.closed = new boolean[power.count];
        this.seed = seed;
    }

    @Override
    public Stage stage() {
        return Stage.POWER;
    }

    private int breakerX(int i) {
        int total = power.count * BREAKER_PITCH - (BREAKER_PITCH - BREAKER_W);
        return BREAKER_AREA_X + (BREAKER_AREA_W - total) / 2 + i * BREAKER_PITCH;
    }

    @Override
    public void tick() {
        super.tick();
        if (knifeAnim > 0) {
            knifeAnim--;
        }
        if (tripTimer > 0) {
            tripTimer--;
        }
        if (sparkTimer > 0) {
            sparkTimer--;
        }
        if (refusedTimer > 0) {
            refusedTimer--;
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || finished()) {
            return false;
        }
        if (Px.inside(x, y, KNIFE_X, KNIFE_Y, KNIFE_W, KNIFE_H)) {
            if (tripTimer > 0) {
                Px.sound(SoundEvents.LEVER_CLICK, 0.5f, 0.4f);
                return true;
            }
            mainOn = !mainOn;
            knifeAnim = 3;
            Px.sound(SoundEvents.IRON_TRAPDOOR_CLOSE, mainOn ? 1.3f : 1.5f, 0.6f);
            if (mainOn) {
                Px.sound(ModSounds.PANEL_HUM.get(), 1.0f, 0.35f);
            } else {
                // Without supply every breaker's undervoltage release drops it out.
                openAll();
            }
            action(PanelAction.BREAKER, 0);
            return true;
        }
        for (int i = 0; i < power.count; i++) {
            if (Px.inside(x, y, breakerX(i), BREAKER_Y, BREAKER_W, BREAKER_H)) {
                pressBreaker(i);
                return true;
            }
        }
        return false;
    }

    private void pressBreaker(int i) {
        if (tripTimer > 0) {
            return;
        }
        if (closed[i]) {
            openBranch(i);
            Px.sound(SoundEvents.LEVER_CLICK, 0.75f, 0.5f);
            action(PanelAction.BREAKER, 0);
            return;
        }
        if (!mainOn) {
            // Undervoltage release: the lever will not latch with no voltage on the panel.
            refusedAt = i;
            refusedTimer = 6;
            Px.sound(SoundEvents.LEVER_CLICK, 1.1f, 0.35f);
            return;
        }
        if (!power.canClose(closed, i)) {
            trip(i);
            return;
        }
        closed[i] = true;
        order.add(i);
        Px.sound(SoundEvents.LEVER_CLICK, 0.6f, 0.6f);
        Px.sound(SoundEvents.COMPARATOR_CLICK, 1.4f, 0.25f);
        action(PanelAction.BREAKER, 0);
        if (order.size() == power.count) {
            Px.sound(SoundEvents.COMPARATOR_CLICK, 0.5f, 0.6f);
            finish(new ArrayList<>(order));
        }
    }

    private void openBranch(int i) {
        closed[i] = false;
        order.remove(Integer.valueOf(i));
        for (int j = 0; j < power.count; j++) {
            if (closed[j] && power.parent[j] == i) {
                openBranch(j);
            }
        }
    }

    private void openAll() {
        for (int j = 0; j < power.count; j++) {
            closed[j] = false;
        }
        order.clear();
    }

    private void trip(int i) {
        sparkAt = i;
        sparkTimer = 9;
        tripTimer = 34;
        mainOn = false;
        knifeAnim = 3;
        openAll();
        Px.sound(ModSounds.PANEL_ARC.get(), 1.0f, 0.8f);
        Px.sound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.6f, 0.5f);
        action(PanelAction.TRIP, i);
        shake(8);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int ox, int oy, int mouseX, int mouseY, float partialTick) {
        Art.blit(g, Art.POWER, ox, oy, 0, 0, DoorPanelScreen.STAGE_W, DoorPanelScreen.STAGE_H);

        int knifeU = knifeAnim > 0 ? KNIFE_MID_U : mainOn ? KNIFE_ON_U : KNIFE_OFF_U;
        Art.blit(g, Art.POWER, ox + KNIFE_X, oy + KNIFE_Y, knifeU, 0, KNIFE_W, KNIFE_H);

        Art.lamp(g, ox + MAINS_LAMP_X, oy + LAMP_Y, mainOn ? Art.LAMP_RED : Art.LAMP_OFF);
        boolean alarmLit = tripTimer > 0 && (tripTimer / 4) % 2 == 0;
        Art.lamp(g, ox + ALARM_LAMP_X, oy + LAMP_Y, alarmLit ? Art.LAMP_AMBER : Art.LAMP_OFF);

        for (int i = 0; i < power.count; i++) {
            int bx = ox + breakerX(i);
            int u = closed[i] ? BREAKER_CLOSED_U : BREAKER_OPEN_U;
            if (refusedTimer > 0 && refusedAt == i) {
                u = BREAKER_TRIPPED_U;
            }
            Art.blit(g, Art.POWER, bx, oy + BREAKER_Y, u, BREAKER_V, BREAKER_W, BREAKER_H);
            Art.smallLamp(g, bx + 5, oy + BREAKER_Y - 9, closed[i] ? Art.SMALL_LAMP_GREEN : Art.SMALL_LAMP_OFF);
            Art.tag(g, font, "А" + (i + 1), bx, oy + BREAKER_Y + BREAKER_H + 2);
        }

        if (sparkTimer > 0 && sparkAt >= 0) {
            int frame = (9 - sparkTimer) / 3;
            Art.blit(g, Art.POWER, ox + breakerX(sparkAt) + 2, oy + BREAKER_Y + 4, SPARK_U + frame * SPARK_SIZE, SPARK_V, SPARK_SIZE, SPARK_SIZE);
        }

        drawSchematic(g, ox + CARD_X, oy + CARD_Y);
    }

    /**
     * Single-line diagram in pencil: the incoming supply on the left, each breaker drawn as a switch symbol in
     * the column of its depth, wired to the breaker that feeds it.
     */
    private void drawSchematic(GuiGraphics g, int cx, int cy) {
        int n = power.count;
        int maxDepth = 0;
        for (int i = 0; i < n; i++) {
            maxDepth = Math.max(maxDepth, power.depth(i));
        }
        int colW = Math.min(30, (CARD_W - 44) / (maxDepth + 1));
        int[] rowOf = new int[n];
        int[] nextRow = {0};
        // Depth-first; a node's first branch continues on its own row, further branches open new rows below.
        for (int i = 0; i < n; i++) {
            if (power.parent[i] < 0) {
                assignRows(i, nextRow[0]++, rowOf, nextRow);
            }
        }
        int rows = Math.max(1, nextRow[0]);
        int rowH = Math.min(13, (CARD_H - 18) / rows);
        int top = cy + 14 + (CARD_H - 18 - rowH * rows) / 2;
        int[] nodeX = new int[n];
        int[] nodeY = new int[n];
        for (int i = 0; i < n; i++) {
            nodeX[i] = cx + 34 + power.depth(i) * colW;
            nodeY[i] = top + rowOf[i] * rowH + rowH / 2;
        }

        // Supply
        int supplyX = cx + 6;
        Px.engrave(g, font, Px.stencil("~380"), cx + 4, cy + 3, Art.PENCIL);
        int busX = cx + 24;
        boolean anyRoot = false;
        int rootTop = Integer.MAX_VALUE;
        int rootBottom = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            if (power.parent[i] < 0) {
                anyRoot = true;
                rootTop = Math.min(rootTop, nodeY[i]);
                rootBottom = Math.max(rootBottom, nodeY[i]);
            }
        }
        if (anyRoot) {
            Px.line(g, supplyX, cy + 11, supplyX, (rootTop + rootBottom) / 2, Art.PENCIL);
            Px.line(g, supplyX, (rootTop + rootBottom) / 2, busX, (rootTop + rootBottom) / 2, Art.PENCIL);
            Px.line(g, busX, rootTop, busX, rootBottom, Art.PENCIL);
            Px.line(g, supplyX - 2, cy + 11, supplyX + 2, cy + 11, Art.PENCIL);
        }

        for (int i = 0; i < n; i++) {
            int x = nodeX[i];
            int y = nodeY[i];
            int p = power.parent[i];
            int fromX = p < 0 ? busX : nodeX[p] + 12;
            int fromY = p < 0 ? y : nodeY[p];
            boolean faded = (Px.hash(seed, i * 13 + 5) % 100) < 18 * spec.power.count / 4;
            int elbow = x - 5;
            wire(g, fromX, fromY, elbow, fromY, faded && (Px.hash(seed, i) & 1) == 0, i);
            wire(g, elbow, fromY, elbow, y, faded && (Px.hash(seed, i) & 1) == 1, i + 7);
            wire(g, elbow, y, x, y, false, i + 11);
            // Switch symbol: a lead, a gap with the blade drawn open, a lead.
            Px.line(g, x, y, x + 3, y, Art.PENCIL);
            Px.line(g, x + 3, y, x + 8, y - 3, Art.PENCIL);
            Px.line(g, x + 8, y, x + 12, y, Art.PENCIL);
            Px.dot(g, x + 8, y - 1, Art.PENCIL);
            Px.engrave(g, font, Px.stencil("А" + (i + 1)), x + 1, y - 9, Art.PENCIL);
        }
    }

    private void assignRows(int node, int row, int[] rowOf, int[] nextRow) {
        rowOf[node] = row;
        boolean first = true;
        for (int j = 0; j < power.count; j++) {
            if (power.parent[j] == node) {
                assignRows(j, first ? row : nextRow[0]++, rowOf, nextRow);
                first = false;
            }
        }
    }

    private void wire(GuiGraphics g, int x0, int y0, int x1, int y1, boolean faded, int salt) {
        if (faded) {
            Px.dotted(g, x0, y0, x1, y1, Art.PENCIL_FAINT, Px.hash(seed, salt) & 3);
        } else {
            Px.line(g, x0, y0, x1, y1, Art.PENCIL);
        }
    }

    @Override
    @Nullable
    public Component tooltip(int x, int y) {
        if (Px.inside(x, y, KNIFE_X, KNIFE_Y, KNIFE_W, KNIFE_H)) {
            return Component.translatable("tip.hermodoors.power.knife");
        }
        if (Px.inside(x, y, MAINS_LAMP_X, LAMP_Y, 10, 10)) {
            return Component.translatable("tip.hermodoors.power.mains");
        }
        if (Px.inside(x, y, ALARM_LAMP_X, LAMP_Y, 10, 10)) {
            return Component.translatable("tip.hermodoors.power.alarm");
        }
        for (int i = 0; i < power.count; i++) {
            if (Px.inside(x, y, breakerX(i), BREAKER_Y - 9, BREAKER_W, BREAKER_H + 18)) {
                return Component.translatable("tip.hermodoors.power.breaker", "А" + (i + 1));
            }
        }
        if (Px.inside(x, y, CARD_X, CARD_Y, CARD_W, CARD_H)) {
            return Component.translatable("tip.hermodoors.power.schematic");
        }
        return null;
    }
}
