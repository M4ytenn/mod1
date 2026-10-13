package io.github.m4ytenn.hermodoors.client.gui;

import io.github.m4ytenn.hermodoors.HermoConfig;
import io.github.m4ytenn.hermodoors.client.gui.stage.ClampsView;
import io.github.m4ytenn.hermodoors.client.gui.stage.PowerView;
import io.github.m4ytenn.hermodoors.client.gui.stage.PumpView;
import io.github.m4ytenn.hermodoors.client.gui.stage.StageView;
import io.github.m4ytenn.hermodoors.client.gui.stage.WheelView;
import io.github.m4ytenn.hermodoors.door.DoorType;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity.PanelAction;
import io.github.m4ytenn.hermodoors.door.Stage;
import io.github.m4ytenn.hermodoors.menu.DoorPanelMenu;
import io.github.m4ytenn.hermodoors.network.PanelActionPayload;
import io.github.m4ytenn.hermodoors.network.StageCompletePayload;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The door's control cabinet: a riveted procedure plate on the left with a lamp per step, and the instruments
 * for the current step on the right. Which step is current always comes from the server's copy of the door.
 */
public class DoorPanelScreen extends AbstractContainerScreen<DoorPanelMenu> {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 184;
    public static final int STAGE_X = 82;
    public static final int STAGE_Y = 24;
    public static final int STAGE_W = 166;
    public static final int STAGE_H = 152;
    private static final int PLATE_X = 6;
    private static final int PLATE_Y = 23;
    private static final int ROW_Y = 50;
    private static final int ROW_H = 31;
    /** How long to wait for the server to confirm a finished stage before assuming it refused. */
    private static final int CONFIRM_TIMEOUT = 60;

    private final Random random = new Random();
    @Nullable
    private StageView view;
    @Nullable
    private Stage viewStage;
    private int pending = -1;
    private int shake;
    private int faultFlash;
    private int ticks;

    public DoorPanelScreen(DoorPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    public Font font() {
        return font;
    }

    @Nullable
    private HermoDoorBlockEntity door() {
        return minecraft == null || minecraft.player == null ? null : menu.door(minecraft.player);
    }

    // ---------------------------------------------------------------- simulation

    @Override
    protected void containerTick() {
        ticks++;
        if (shake > 0) {
            shake--;
        }
        if (faultFlash > 0) {
            faultFlash--;
        }
        HermoDoorBlockEntity door = door();
        if (door == null) {
            return;
        }
        Stage current = door.currentStage();
        if (current != viewStage) {
            viewStage = current;
            view = current == null ? null : createView(current, door);
            pending = -1;
        } else if (pending >= 0 && ++pending > CONFIRM_TIMEOUT && current != null) {
            // The server did not accept the result (too fast, wrong order...). Start this step over.
            view = createView(current, door);
            pending = -1;
            faultFlash = 50;
            Px.sound(SoundEvents.COMPARATOR_CLICK, 0.4f, 0.6f);
        }
        if (view != null) {
            view.tick();
        }
    }

    private StageView createView(Stage stage, HermoDoorBlockEntity door) {
        return switch (stage) {
            case POWER -> new PowerView(this, door.spec(), door.serial());
            case PUMP -> new PumpView(this, door.spec());
            case CLAMPS -> new ClampsView(this, door.spec(), door.clampsFreed());
            case WHEEL -> new WheelView(this, door.spec());
        };
    }

    public void stageFinished(Stage stage, List<Integer> proof) {
        PacketDistributor.sendToServer(new StageCompletePayload(menu.pos(), stage.ordinal(), proof));
        pending = 0;
    }

    public void panelAction(PanelAction action, int value) {
        PacketDistributor.sendToServer(new PanelActionPayload(menu.pos(), action.ordinal(), value));
    }

    public void shake(int duration) {
        if (HermoConfig.CLIENT.screenShake.get()) {
            shake = Math.max(shake, duration);
        }
    }

    // ---------------------------------------------------------------- render

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        HermoDoorBlockEntity door = door();
        g.pose().pushPose();
        if (shake > 0) {
            g.pose().translate(random.nextInt(3) - 1, random.nextInt(3) - 1, 0);
        }
        int x = leftPos;
        int y = topPos;
        Art.blit(g, Art.PANEL, x, y, 0, 0, WIDTH, HEIGHT);
        if (door != null) {
            drawHeader(g, door, x, y);
            drawProcedure(g, door, x, y);
        }
        if (view != null) {
            view.render(g, x + STAGE_X, y + STAGE_Y, mouseX - x - STAGE_X, mouseY - y - STAGE_Y, partialTick);
        }
        if (faultFlash > 0 && (faultFlash / 5) % 2 == 0) {
            Px.engraveCentered(g, font, Px.stencil("СБОЙ - ПОВТОРИТЬ"), x + STAGE_X + STAGE_W / 2, y + STAGE_Y + STAGE_H - 9, 0xFFE0402C);
        }
        g.pose().popPose();
    }

    private void drawHeader(GuiGraphics g, HermoDoorBlockEntity door, int x, int y) {
        DoorType type = door.type();
        String title = (type.sliding ? "ЗАТВОР " : "ДВЕРЬ ") + type.designation;
        Px.engrave(g, font, Px.stencil(title), x + 20, y + 8, Art.ENGRAVE_CREAM);
        String serial = String.format("ЗАВ. № %04d", door.serial());
        Px.engrave(g, font, Px.stencil(serial), x + 236 - font.width(Px.stencil(serial)), y + 8, Art.ENGRAVE_CREAM);
    }

    private void drawProcedure(GuiGraphics g, HermoDoorBlockEntity door, int x, int y) {
        Px.engraveCentered(g, font, Px.stencilKey("engraving.hermodoors.procedure_1"), x + PLATE_X + 36, y + PLATE_Y + 6, Art.ENGRAVE_DARK);
        Px.engraveCentered(g, font, Px.stencilKey("engraving.hermodoors.procedure_2"), x + PLATE_X + 36, y + PLATE_Y + 13, Art.ENGRAVE_DARK);
        Stage current = door.currentStage();
        int number = 1;
        for (Stage stage : Stage.values()) {
            int rx = x + PLATE_X + 4;
            int ry = y + ROW_Y + stage.ordinal() * ROW_H;
            if (!stage.in(door.stageMask())) {
                Art.blit(g, Art.PANEL, rx, ry, Art.BLANK_PLATE_U, Art.BLANK_PLATE_V, Art.BLANK_PLATE_W, Art.BLANK_PLATE_H);
                continue;
            }
            int lamp;
            if (stage.in(door.completed()) || door.isUnlocked()) {
                lamp = Art.LAMP_GREEN;
            } else if (stage == current) {
                lamp = pending >= 0 || (ticks / 8) % 2 == 0 ? Art.LAMP_AMBER : Art.LAMP_OFF;
            } else {
                lamp = Art.LAMP_OFF;
            }
            Art.lamp(g, rx + 1, ry + 4, lamp);
            Px.engrave(g, font, Px.stencil(number + "."), rx + 14, ry + 2, Art.ENGRAVE_DARK);
            Px.engrave(g, font, Px.stencilKey("engraving.hermodoors." + stage.id + "_1"), rx + 22, ry + 2, Art.ENGRAVE_DARK);
            Px.engrave(g, font, Px.stencilKey("engraving.hermodoors." + stage.id + "_2"), rx + 22, ry + 9, Art.ENGRAVE_DARK);
            number++;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Everything is engraved on the panel itself.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Component tip = tooltipAt(mouseX, mouseY);
        if (tip != null) {
            g.renderTooltip(font, font.split(tip, 190), mouseX, mouseY);
        }
    }

    @Nullable
    private Component tooltipAt(int mouseX, int mouseY) {
        int lx = mouseX - leftPos;
        int ly = mouseY - topPos;
        if (view != null && lx >= STAGE_X && ly >= STAGE_Y && lx < STAGE_X + STAGE_W && ly < STAGE_Y + STAGE_H) {
            return view.tooltip(lx - STAGE_X, ly - STAGE_Y);
        }
        for (Stage stage : Stage.values()) {
            int ry = ROW_Y + stage.ordinal() * ROW_H;
            if (Px.inside(lx, ly, PLATE_X + 4, ry, 66, 26)) {
                HermoDoorBlockEntity door = door();
                if (door != null && !stage.in(door.stageMask())) {
                    return Component.translatable("tip.hermodoors.stage_not_fitted");
                }
                return Component.translatable("tip.hermodoors.stage." + stage.id);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (view != null && view.mouseClicked(mouseX - leftPos - STAGE_X, mouseY - topPos - STAGE_Y, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (view != null && view.mouseDragged(mouseX - leftPos - STAGE_X, mouseY - topPos - STAGE_Y, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (view != null && view.mouseReleased(mouseX - leftPos - STAGE_X, mouseY - topPos - STAGE_Y, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
