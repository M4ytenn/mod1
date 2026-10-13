package io.github.m4ytenn.hermodoors.network;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The owner signed the write-off act in the passport: take the door down and hand it back. */
public record DismantleDoorPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<DismantleDoorPayload> TYPE = new Type<>(HermoDoors.id("dismantle_door"));
    public static final StreamCodec<ByteBuf, DismantleDoorPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(DismantleDoorPayload::new, DismantleDoorPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
