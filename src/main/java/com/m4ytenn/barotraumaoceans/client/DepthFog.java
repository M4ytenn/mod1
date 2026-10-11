package com.m4ytenn.barotraumaoceans.client;

import com.m4ytenn.barotraumaoceans.config.BOClientConfig;
import com.m4ytenn.barotraumaoceans.worldgen.BOBiomes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Thicker, darker water fog the deeper the camera is - the abyss should feel like the abyss. */
public final class DepthFog {
    private DepthFog() {
    }

    private static float depthFactor(Camera camera) {
        if (!BOClientConfig.SPEC.isLoaded() || !BOClientConfig.DEPTH_FOG.get() || camera.getFluidInCamera() != FogType.WATER) {
            return 0F;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return 0F;
        }
        BlockPos pos = camera.getBlockPosition();
        if (BOClientConfig.ONLY_IN_MOD_WORLDS.get() && !level.getBiome(pos).is(BOBiomes.TAG_DEPTH_FOG)) {
            return 0F;
        }
        double depth = BOClientConfig.FOG_FADE_START.get() - camera.getPosition().y;
        float f = (float) (depth / BOClientConfig.FOG_FADE_RANGE.get());
        return Math.max(0F, Math.min(1F, f));
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.WATER) {
            return;
        }
        float f = depthFactor(event.getCamera());
        if (f <= 0F) {
            return;
        }
        float far = event.getFarPlaneDistance();
        float target = Math.min(far, BOClientConfig.MIN_FOG_DISTANCE.get().floatValue());
        float newFar = far + (target - far) * f;
        event.setFarPlaneDistance(newFar);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), newFar * 0.25F));
        event.setCanceled(true);
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float f = depthFactor(event.getCamera());
        if (f <= 0F) {
            return;
        }
        float k = 1F - (float) (BOClientConfig.DEPTH_DARKENING.get() * f);
        event.setRed(event.getRed() * k);
        event.setGreen(event.getGreen() * k);
        event.setBlue(event.getBlue() * k);
    }
}
