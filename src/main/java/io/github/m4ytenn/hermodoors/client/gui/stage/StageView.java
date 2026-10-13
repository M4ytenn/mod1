package io.github.m4ytenn.hermodoors.client.gui.stage;

import io.github.m4ytenn.hermodoors.client.gui.DoorPanelScreen;
import io.github.m4ytenn.hermodoors.door.HermoDoorBlockEntity.PanelAction;
import io.github.m4ytenn.hermodoors.door.Stage;
import io.github.m4ytenn.hermodoors.puzzle.PuzzleSpec;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One instrument section of the control panel. Coordinates handed to a view are local to the stage area
 * ({@link DoorPanelScreen#STAGE_W} by {@link DoorPanelScreen#STAGE_H}); the view simulates at 20 ticks per
 * second and interpolates in {@link #render}.
 */
public abstract class StageView {
    protected final DoorPanelScreen screen;
    protected final PuzzleSpec spec;
    protected final Font font;
    protected int ticks;
    private boolean finished;

    protected StageView(DoorPanelScreen screen, PuzzleSpec spec) {
        this.screen = screen;
        this.spec = spec;
        this.font = screen.font();
    }

    public abstract Stage stage();

    public void tick() {
        ticks++;
    }

    public abstract void render(GuiGraphics g, int ox, int oy, int mouseX, int mouseY, float partialTick);

    public boolean mouseClicked(double x, double y, int button) {
        return false;
    }

    public boolean mouseDragged(double x, double y, int button) {
        return false;
    }

    public boolean mouseReleased(double x, double y, int button) {
        return false;
    }

    /** Explanation shown when hovering a control, in the player's language. */
    @Nullable
    public Component tooltip(int x, int y) {
        return null;
    }

    public boolean finished() {
        return finished;
    }

    protected void finish(List<Integer> proof) {
        if (!finished) {
            finished = true;
            screen.stageFinished(stage(), proof);
        }
    }

    protected void action(PanelAction action, int value) {
        screen.panelAction(action, value);
    }

    protected void shake(int ticks) {
        screen.shake(ticks);
    }
}
