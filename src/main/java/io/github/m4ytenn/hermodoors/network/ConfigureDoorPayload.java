package io.github.m4ytenn.hermodoors.network;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The owner approved the passport: which stages the door needs, how hard, and which way it opens. */
public record ConfigureDoorPayload(BlockPos pos, int stageMask, int difficulty, boolean mirrored) implements CustomPacketPayload {
    public static final Type<ConfigureDoorPayload> TYPE = new Type<>(HermoDoors.id("configure_door"));
    public static final StreamCodec<ByteBuf, ConfigureDoorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureDoorPayload::pos,
            ByteBufCodecs.VAR_INT, ConfigureDoorPayload::stageMask,
            ByteBufCodecs.VAR_INT, ConfigureDoorPayload::difficulty,
            ByteBufCodecs.BOOL, ConfigureDoorPayload::mirrored,
            ConfigureDoorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
