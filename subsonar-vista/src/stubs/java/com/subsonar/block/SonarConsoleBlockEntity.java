package com.subsonar.block;

import com.subsonar.sonar.SonarMode;
import com.subsonar.sonar.SonarView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Stub: see com/subsonar/README.txt. The real class extends Create's KineticBlockEntity. */
public class SonarConsoleBlockEntity extends BlockEntity {
    public SonarConsoleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public SonarMode getMode() { throw new AssertionError(); }

    public SonarView getView() { throw new AssertionError(); }

    public boolean isProximityAlarm() { throw new AssertionError(); }

    public float getGlitch(double now) { throw new AssertionError(); }

    public double getGlitchAge(double now) { throw new AssertionError(); }

    public boolean isRebooting(double now) { throw new AssertionError(); }

    public double getGlitchSeconds() { throw new AssertionError(); }

    public double getSpeedFactor() { throw new AssertionError(); }

    public int getRange() { throw new AssertionError(); }

    public boolean isPowered() { throw new AssertionError(); }

    public Direction getForward() { throw new AssertionError(); }
}
