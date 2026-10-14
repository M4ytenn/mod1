package com.subsonar.vista.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.subsonar.SonarConfig;
import com.subsonar.block.SonarConsoleBlockEntity;
import com.subsonar.client.ClientSable;
import com.subsonar.client.SonarClientData;
import com.subsonar.client.SonarDisplay;
import com.subsonar.client.SonarScreen;
import com.subsonar.sonar.SonarMode;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.client.CrtOverlay;
import net.mehvahdjukaar.vista.client.textures.ScalingMode;
import net.mehvahdjukaar.vista.client.textures.ScreenFit;
import net.mehvahdjukaar.vista.client.textures.TvScreenVertexConsumers;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * What a TV shows for a linked sonar console: Sub Sonar's own display, drawn into its texture with the same
 * settings the sonar screen uses, and handed to Vista, which adds the CRT look. One per console, shared by every
 * TV showing it.
 */
final class SonarVideoSource implements IVideoSource {
    /** The display is drawn on the CPU; a TV does not need more frames than this. */
    private static final double FRAME_SECONDS = 1 / 30.0;
    /** The display is square: keep the scope round on wide screens. */
    private static final ScreenFit ROUND = new ScreenFit(ScalingMode.CONTAIN, 1f);

    private final SonarConsoleBlockEntity console;
    private final SonarDisplay.Params params = new SonarDisplay.Params();
    @Nullable
    private SonarDisplay display;
    private float power;
    private float warning;
    private double lastUpdate = -1;
    private double lastDrawn = -1;

    SonarVideoSource(SonarConsoleBlockEntity console) {
        this.console = console;
    }

    @Override
    public @NotNull VertexConsumer getVideoFrameBuilder(
            TVBlockEntity tv, float partialTick, MultiBufferSource buffer,
            boolean shouldUpdate, Vec2i screenSize, Vec2i pixelEffectRes,
            int videoAnimationTick, boolean paused,
            IntAnimationState switchAnim, IntAnimationState staticAnim, boolean showsTime) {
        BlockPos pos = this.console.getBlockPos();
        Screen screen = Minecraft.getInstance().screen;
        boolean otherConsoleOpen = screen instanceof SonarScreen sonar && !sonar.getConsolePos().equals(pos);
        if (this.console.isRemoved() || otherConsoleOpen || !SubSonarVistaClient.claim(pos, tv.getBlockPos())) {
            // The sonar data on this client belongs to another console right now: lost signal.
            return TvScreenVertexConsumers.getNoiseVC(buffer, pixelEffectRes, switchAnim);
        }
        if (!SonarClientData.isFor(pos) && !(screen instanceof SonarScreen)) {
            SonarClientData.bind(pos);
        }
        SonarDisplay display = this.display();
        double now = SonarClientData.now();
        if (shouldUpdate && now - this.lastDrawn >= FRAME_SECONDS) {
            this.lastDrawn = now;
            this.update(now, partialTick);
            display.render(this.params);
        }
        CrtOverlay overlay = paused ? CrtOverlay.PAUSE : CrtOverlay.NONE;
        return TvScreenVertexConsumers.getSingleTextureVC(buffer, display.location, overlay, pixelEffectRes, switchAnim, staticAnim);
    }

    @Override
    public ScreenFit getScreenFit() {
        return ROUND;
    }

    private SonarDisplay display() {
        int size = SonarConfig.cget(SonarConfig.DISPLAY_RESOLUTION);
        if (this.display == null || this.display.size != size) {
            this.close();
            this.display = new SonarDisplay(size);
        }
        return this.display;
    }

    /** Fills the display parameters the way the sonar screen does, from the console's synced state. */
    private void update(double now, float partialTick) {
        double dt = this.lastUpdate < 0 ? 0 : Mth.clamp(now - this.lastUpdate, 0, 0.25);
        this.lastUpdate = now;
        SonarMode mode = this.console.getMode();
        boolean on = this.console.isPowered() && mode != SonarMode.OFF;

        // Same warm-up and fade-out as the screen.
        float target = on ? 1 : 0;
        if (!SonarConfig.cget(SonarConfig.CRT_POWER_EFFECT)) {
            this.power = target;
        } else if (this.power < target) {
            this.power = Math.min(target, this.power + (float) (dt / 0.7));
        } else if (this.power > target) {
            this.power = Math.max(target, this.power - (float) (dt / 0.45));
        }

        ClientSable.Pose pose = ClientSable.pose(this.console.getBlockPos(), partialTick);
        Direction facing = this.console.getForward();
        Vector3d heading = new Vector3d(facing.getStepX(), 0, facing.getStepZ());
        pose.orientation().transform(heading);
        double headingLength = Math.sqrt(heading.x * heading.x + heading.z * heading.z);

        boolean proximity = on && this.console.isProximityAlarm() && SonarConfig.cget(SonarConfig.PROXIMITY_WARNING);
        this.warning = proximity ? (float) (0.5 + 0.5 * Math.sin(now * 10)) : Math.max(0, this.warning - (float) dt * 3);
        double rate = SonarConfig.cget(SonarConfig.RATE_AFFECTS_VISUALS) ? this.console.getSpeedFactor() : 1;
        int range = mode == SonarMode.PASSIVE ? SonarConfig.sget(SonarConfig.PASSIVE_RANGE) : this.console.getRange();
        ScreenSettings settings = ScreenSettings.current();

        SonarDisplay.Params p = this.params;
        p.now = now;
        p.gx = pose.position().x;
        p.gy = pose.position().y;
        p.gz = pose.position().z;
        p.hx = headingLength < 1.0E-4 ? facing.getStepX() : (float) (heading.x / headingLength);
        p.hz = headingLength < 1.0E-4 ? facing.getStepZ() : (float) (heading.z / headingLength);
        p.orientation = pose.orientation();
        p.headingUp = settings.headingUp();
        p.zoom = settings.zoom();
        p.viewRange = range / settings.zoom();
        p.view = this.console.getView();
        p.passiveMode = on && mode == SonarMode.PASSIVE;
        p.power = this.power;
        p.noSignal = !on;
        p.warning = this.warning;
        p.travelSeconds = SonarConfig.cget(SonarConfig.PING_TRAVEL_SECONDS) / rate;
        p.fadeSeconds = settings.echoHold();
        p.contactFadeSeconds = SonarConfig.cget(SonarConfig.CONTACT_FADE_SECONDS);
        p.sweepSeconds = SonarConfig.cget(SonarConfig.PASSIVE_SWEEP_SECONDS) / rate;
        p.chart = settings.chart();
        p.chartBrightness = (float) SonarConfig.cget(SonarConfig.CHART_BRIGHTNESS);
        p.showGrid = SonarConfig.cget(SonarConfig.SHOW_GRID);
        p.showSilhouette = SonarConfig.cget(SonarConfig.SHOW_SILHOUETTE);
        p.gain = settings.gain();
        p.glitch = on ? this.console.getGlitch(now) : 0;
        p.glitchAge = this.console.getGlitchAge(now);
        p.rebooting = on && this.console.isRebooting(now) && p.glitchAge > 0.35;
        double glitchSeconds = this.console.getGlitchSeconds();
        p.rebootProgress = glitchSeconds > 0
                ? (float) Mth.clamp((p.glitchAge - 0.35) / Math.max(0.1, glitchSeconds - 0.35), 0, 1)
                : 1;
    }

    boolean isStale(Level currentLevel) {
        return this.console.isRemoved() || this.console.getLevel() != currentLevel;
    }

    void close() {
        if (this.display != null) {
            this.display.close();
            this.display = null;
        }
    }
}
