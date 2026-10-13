package io.github.m4ytenn.hermodoors.network;

import io.github.m4ytenn.hermodoors.HermoDoors;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Something audible or visible happened on the panel (a hammer blow, a breaker trip, the wheel turned). */
public record PanelActionPayload(BlockPos pos, int action, int value) implements CustomPacketPayload {
    public static final Type<PanelActionPayload> TYPE = new Type<>(HermoDoors.id("panel_action"));
    public static final StreamCodec<ByteBuf, PanelActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PanelActionPayload::pos,
            ByteBufCodecs.VAR_INT, PanelActionPayload::action,
            ByteBufCodecs.VAR_INT, PanelActionPayload::value,
            PanelActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
