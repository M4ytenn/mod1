package io.github.m4ytenn.hermodoors.client.gui;

import io.github.m4ytenn.hermodoors.HermoConfig;
import io.github.m4ytenn.hermodoors.door.DoorType;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity;
import io.github.m4ytenn.hermodoors.door.Stage;
import io.github.m4ytenn.hermodoors.menu.DoorPassportMenu;
import io.github.m4ytenn.hermodoors.network.ConfigureDoorPayload;
import io.github.m4ytenn.hermodoors.network.DismantleDoorPayload;
import io.github.m4ytenn.hermodoors.registry.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The door's technical passport, a typed form. Tick the steps the door's opening procedure needs, circle a
 * difficulty category and the hinge side, then put the QC stamp in the "М.П." circle to approve it.
 */
public class PassportScreen extends AbstractContainerScreen<DoorPassportMenu> {
    public static final int WIDTH = 232;
    public static final int HEIGHT = 184;
    private static final int PRINT = 0xFF26231E;
    private static final int FIELD_X = 96;
    private static final int FIELD_Y = 35;
    private static final int PROCEDURE_Y = 76;
    private static final int STEP_Y = 87;
    private static final int STEP_H = 10;
    private static final int BOX_X = 22;
    private static final int DIFF_LABEL_Y = 129;
    private static final int DIFF_Y = 139;
    private static final int[] DIFF_X = {32, 52, 76};
    private static final String[] DIFF_LABEL = {"I", "II", "III"};
    private static final int HINGE_LABEL_Y = 150;
    private static final int HINGE_Y = 160;
    private static final int[] HINGE_X = {44, 104};
    private static final int STAMP_X = 166;
    private static final int STAMP_Y = 124;
    private static final int STAMP_SIZE = 52;
    private static final int WRITE_OFF_X = 18;
    private static final int WRITE_OFF_Y = 169;

    // passport.png sprites
    private static final int CIRCLE_U = 0;
    private static final int CIRCLE_V = 184;
    private static final int CIRCLE_W = 20;
    private static final int CIRCLE_H = 13;
    private static final int WIDE_CIRCLE_V = 197;
    private static final int WIDE_CIRCLE_W = 44;
    private static final int STAMP_U = 48;
    private static final int STAMP_V = 184;
    private static final int WRITE_OFF_STAMP_U = 104;
    private static final int WRITE_OFF_STAMP_V = 184;
    private static final int WRITE_OFF_STAMP_W = 64;
    private static final int WRITE_OFF_STAMP_H = 22;

    private int mask;
    private int difficulty;
    private boolean mirrored;
    private boolean loaded;
    private int approveTimer = -1;
    private int writeOffTimer = -1;
    private int confirmWriteOff;
    private int ticks;

    public PassportScreen(DoorPassportMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Nullable
    private HermoDoorBlockEntity door() {
        return minecraft == null || minecraft.player == null ? null : menu.door(minecraft.player);
    }

    @Override
    protected void containerTick() {
        ticks++;
        HermoDoorBlockEntity door = door();
        if (!loaded && door != null) {
            mask = door.stageMask();
            difficulty = door.difficulty();
            mirrored = door.mirrored();
            loaded = true;
        }
        if (confirmWriteOff > 0) {
            confirmWriteOff--;
        }
        if (approveTimer >= 0 && ++approveTimer == 14) {
            PacketDistributor.sendToServer(new ConfigureDoorPayload(menu.pos(), mask, difficulty, mirrored));
        }
        if (writeOffTimer >= 0 && ++writeOffTimer == 14) {
            PacketDistributor.sendToServer(new DismantleDoorPayload(menu.pos()));
        }
    }

    private boolean locked() {
        return approveTimer >= 0 || writeOffTimer >= 0;
    }

    private void type() {
        Px.sound(ModSounds.PASSPORT_TYPE.get(), 0.9f + (ticks % 5) * 0.05f, 0.7f);
    }

    // ---------------------------------------------------------------- render

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Art.blit(g, Art.PASSPORT, x, y, 0, 0, WIDTH, HEIGHT);
        HermoDoorBlockEntity door = door();
        if (door == null) {
            return;
        }
        DoorType type = door.type();
        boolean jitter = HermoConfig.CLIENT.typewriterJitter.get();
        int seed = door.serial();

        // Printed heading.
        g.pose().pushPose();
        Component heading = Component.translatable("passport.hermodoors.title");
        g.pose().translate(x + WIDTH / 2f - font.width(heading) * 0.75f, y + 9, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, heading, 0, 0, PRINT, false);
        g.pose().popPose();
        Component kind = Component.translatable("passport.hermodoors.kind." + type.id);
        g.drawString(font, kind, x + WIDTH / 2 - font.width(kind) / 2, y + 24, PRINT, false);

        // Typed particulars.
        String[] labels = {"model", "serial", "year", "owner"};
        String[] values = {
                type.designation,
                String.format("%04d", door.serial()),
                String.valueOf(door.year()),
                door.ownerName().isEmpty() ? "—" : door.ownerName()
        };
        for (int i = 0; i < labels.length; i++) {
            int ly = y + FIELD_Y + i * 9;
            g.drawString(font, Component.translatable("passport.hermodoors.field." + labels[i]), x + 20, ly, PRINT, false);
            Px.typewrite(g, font, values[i], x + FIELD_X, ly, Art.TYPE_INK, seed + i * 17, jitter);
        }

        // 1. Opening procedure.
        g.drawString(font, Component.translatable("passport.hermodoors.section.procedure"), x + 18, y + PROCEDURE_Y, PRINT, false);
        for (Stage stage : Stage.values()) {
            int ly = y + STEP_Y + stage.ordinal() * STEP_H;
            box(g, x + BOX_X, ly);
            if (stage.in(mask)) {
                Px.typewrite(g, font, "X", x + BOX_X + 1, ly, Art.TYPE_INK, seed + 101 + stage.ordinal(), jitter);
            }
            g.drawString(font, Component.translatable("passport.hermodoors.step." + stage.id), x + BOX_X + 12, ly, PRINT, false);
        }
        if (mask == 0) {
            Component none = Component.translatable("passport.hermodoors.no_steps");
            g.drawString(font, none, x + STAMP_X - 2 - font.width(none) / 2 + STAMP_SIZE / 2, y + PROCEDURE_Y, Art.RED_PENCIL, false);
        }

        // 2. Difficulty category, circled in ink.
        g.drawString(font, Component.translatable("passport.hermodoors.section.difficulty"), x + 18, y + DIFF_LABEL_Y, PRINT, false);
        for (int i = 0; i < 3; i++) {
            Component label = Component.literal(DIFF_LABEL[i]);
            int lx = x + DIFF_X[i] - font.width(label) / 2;
            g.drawString(font, label, lx, y + DIFF_Y, PRINT, false);
            if (difficulty == i + 1) {
                Art.blit(g, Art.PASSPORT, x + DIFF_X[i] - CIRCLE_W / 2, y + DIFF_Y - 3, CIRCLE_U, CIRCLE_V, CIRCLE_W, CIRCLE_H);
            }
        }

        // 3. Hinge side (ДЗГ) or slide direction (гермозатвор).
        String hingeKey = type.sliding ? "slide" : "hinge";
        g.drawString(font, Component.translatable("passport.hermodoors.section." + hingeKey), x + 18, y + HINGE_LABEL_Y, PRINT, false);
        for (int i = 0; i < 2; i++) {
            Component label = Component.translatable("passport.hermodoors." + hingeKey + "." + (i == 0 ? "left" : "right"));
            int cx = x + HINGE_X[i];
            g.drawString(font, label, cx - font.width(label) / 2, y + HINGE_Y, PRINT, false);
            boolean selected = (i == 1) == (type.sliding != mirrored);
            if (selected) {
                Art.blit(g, Art.PASSPORT, cx - WIDE_CIRCLE_W / 2, y + HINGE_Y - 3, CIRCLE_U, WIDE_CIRCLE_V, WIDE_CIRCLE_W, CIRCLE_H);
            }
        }

        // Stamp: the place for it, then the stamp itself once approved.
        if (approveTimer >= 0) {
            float drop = Math.max(0, 1 - (approveTimer + partialTick) / 3f);
            g.pose().pushPose();
            float scale = 1 + drop * 0.25f;
            g.pose().translate(x + STAMP_X + STAMP_SIZE / 2f, y + STAMP_Y + STAMP_SIZE / 2f, 0);
            g.pose().scale(scale, scale, 1);
            Art.blit(g, Art.PASSPORT, -STAMP_SIZE / 2, -STAMP_SIZE / 2, STAMP_U, STAMP_V, STAMP_SIZE, STAMP_SIZE);
            g.pose().popPose();
        }

        // Write-off act, in red pencil.
        if (door.mayConfigure(minecraft.player)) {
            Component writeOff = Component.translatable(confirmWriteOff > 0 ? "passport.hermodoors.write_off.confirm" : "passport.hermodoors.write_off");
            g.drawString(font, writeOff, x + WRITE_OFF_X, y + WRITE_OFF_Y, Art.RED_PENCIL, false);
            g.fill(x + WRITE_OFF_X, y + WRITE_OFF_Y + 9, x + WRITE_OFF_X + font.width(writeOff), y + WRITE_OFF_Y + 10, Px.withAlpha(0xA3291F, 0.6f));
        }
        if (writeOffTimer >= 0) {
            Art.blit(g, Art.PASSPORT, x + 70, y + 74, WRITE_OFF_STAMP_U, WRITE_OFF_STAMP_V, WRITE_OFF_STAMP_W, WRITE_OFF_STAMP_H);
        }

        if (menu.firstPlacement() && approveTimer < 0) {
            Component note = Component.translatable("passport.hermodoors.fill_in");
            g.drawString(font, note, x + WIDTH - 10 - font.width(note), y + 2, Art.RED_PENCIL, false);
        }
    }

    /** Printed check box, 9 x 9. */
    private static void box(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 8, y, PRINT);
        g.fill(x - 1, y + 8, x + 8, y + 9, PRINT);
        g.fill(x - 1, y, x, y + 8, PRINT);
        g.fill(x + 7, y, x + 8, y + 8, PRINT);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Component tip = tooltipAt(mouseX - leftPos, mouseY - topPos);
        if (tip != null) {
            g.renderTooltip(font, font.split(tip, 190), mouseX, mouseY);
        }
    }

    @Nullable
    private Component tooltipAt(int lx, int ly) {
        for (Stage stage : Stage.values()) {
            if (Px.inside(lx, ly, BOX_X - 2, STEP_Y + stage.ordinal() * STEP_H - 1, 150, STEP_H)) {
                return Component.translatable("tip.hermodoors.stage." + stage.id);
            }
        }
        if (Px.inside(lx, ly, STAMP_X, STAMP_Y, STAMP_SIZE, STAMP_SIZE)) {
            return Component.translatable("tip.hermodoors.passport.stamp");
        }
        if (Px.inside(lx, ly, 18, DIFF_LABEL_Y - 2, 140, 22)) {
            return Component.translatable("tip.hermodoors.passport.difficulty");
        }
        return null;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        HermoDoorBlockEntity door = door();
        if (button != 0 || door == null || !loaded || locked()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        for (Stage stage : Stage.values()) {
            if (Px.inside(lx, ly, BOX_X - 2, STEP_Y + stage.ordinal() * STEP_H - 1, 150, STEP_H)) {
                mask ^= stage.bit;
                type();
                return true;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (Px.inside(lx, ly, DIFF_X[i] - 9, DIFF_Y - 3, 18, 13)) {
                difficulty = i + 1;
                type();
                return true;
            }
        }
        for (int i = 0; i < 2; i++) {
            if (Px.inside(lx, ly, HINGE_X[i] - 20, HINGE_Y - 3, 40, 13)) {
                // For the gate "right" is the default (not mirrored); for the hinged door "left" is.
                mirrored = door.type().sliding ? i == 0 : i == 1;
                type();
                return true;
            }
        }
        if (Px.inside(lx, ly, STAMP_X, STAMP_Y, STAMP_SIZE, STAMP_SIZE)) {
            approveTimer = 0;
            Px.sound(SoundEvents.BOOK_PUT, 0.7f, 1.0f);
            Px.sound(SoundEvents.WOOD_HIT, 0.6f, 0.5f);
            return true;
        }
        if (door.mayConfigure(minecraft.player) && Px.inside(lx, ly, WRITE_OFF_X, WRITE_OFF_Y - 1, 120, 11)) {
            if (confirmWriteOff > 0) {
                writeOffTimer = 0;
                Px.sound(SoundEvents.BOOK_PUT, 0.5f, 1.0f);
            } else {
                confirmWriteOff = 80;
                Px.sound(SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.8f);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
