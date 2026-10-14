package com.subsonar.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/** Stub: see com/subsonar/README.txt. */
public final class ClientSable {
    private ClientSable() {}

    public static Pose pose(BlockPos pos, float partialTick) { throw new AssertionError(); }

    public record Pose(Vec3 position, Quaterniond orientation, boolean onShip, String shipName) {}
}
