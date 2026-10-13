package io.github.m4ytenn.hermodoors.network;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The operator finished a stage. {@code proof} carries whatever the server needs to check the claim
 * (for the breaker panel: the order the breakers were closed in).
 */
public record StageCompletePayload(BlockPos pos, int stage, List<Integer> proof) implements CustomPacketPayload {
    public static final Type<StageCompletePayload> TYPE = new Type<>(HermoDoors.id("stage_complete"));
    public static final StreamCodec<ByteBuf, StageCompletePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StageCompletePayload::pos,
            ByteBufCodecs.VAR_INT, StageCompletePayload::stage,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)), StageCompletePayload::proof,
            StageCompletePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
